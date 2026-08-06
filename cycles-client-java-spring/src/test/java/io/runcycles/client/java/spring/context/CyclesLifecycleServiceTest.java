package io.runcycles.client.java.spring.context;

import io.runcycles.client.java.spring.annotation.Cycles;
import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.evaluation.CyclesExpressionEvaluator;
import io.runcycles.client.java.spring.model.*;
import io.runcycles.client.java.spring.retry.CommitRetryEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// NOTE: CyclesClient has overloaded default methods (e.g. createReservation(Object) vs
// createReservation(ReservationCreateRequest)). We must use any(Object.class) rather than
// bare any() so Mockito stubs the correct overload.

@DisplayName("CyclesLifecycleService")
class CyclesLifecycleServiceTest {

    private CyclesClient client;
    private CommitRetryEngine retryEngine;
    private CyclesExpressionEvaluator evaluator;
    private CyclesRequestBuilderService requestBuilderService;
    private ScheduledExecutorService heartbeatExecutor;
    private CyclesLifecycleService service;

    // Dummy method for SpEL evaluation context
    @SuppressWarnings("unused")
    public String dummyMethod(int tokens) { return "result"; }

    private Method dummyMethod() throws NoSuchMethodException {
        return getClass().getMethod("dummyMethod", int.class);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        client = mock(CyclesClient.class);
        retryEngine = mock(CommitRetryEngine.class);
        evaluator = mock(CyclesExpressionEvaluator.class);
        requestBuilderService = mock(CyclesRequestBuilderService.class);
        heartbeatExecutor = mock(ScheduledExecutorService.class);
        // Return a mock future from one-shot schedule so cancelHeartbeat works
        when(heartbeatExecutor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                .thenAnswer(inv -> mock(ScheduledFuture.class));
        service = new CyclesLifecycleService(client, retryEngine, requestBuilderService, evaluator, heartbeatExecutor);
    }

    @AfterEach
    void tearDown() {
        CyclesContextHolder.clear();
    }

    private Cycles mockCycles(boolean dryRun) {
        Cycles cycles = mock(Cycles.class);
        when(cycles.value()).thenReturn("1000");
        when(cycles.estimate()).thenReturn("");
        when(cycles.actual()).thenReturn("");
        when(cycles.metadata()).thenReturn("");
        when(cycles.useEstimateIfActualNotProvided()).thenReturn(true);
        when(cycles.unit()).thenReturn("TOKENS");
        when(cycles.ttlMs()).thenReturn(60000L);
        when(cycles.gracePeriodMs()).thenReturn(5000L);
        when(cycles.overagePolicy()).thenReturn("ALLOW_IF_AVAILABLE");
        when(cycles.dryRun()).thenReturn(dryRun);
        when(cycles.tenant()).thenReturn("test-tenant");
        when(cycles.workspace()).thenReturn("");
        when(cycles.app()).thenReturn("");
        when(cycles.workflow()).thenReturn("");
        when(cycles.agent()).thenReturn("");
        when(cycles.toolset()).thenReturn("");
        when(cycles.dimensions()).thenReturn(new String[0]);
        when(cycles.actionTags()).thenReturn(new String[0]);
        return cycles;
    }

    private Map<String, Object> allowResponse(String reservationId) {
        Map<String, Object> body = new HashMap<>();
        body.put("decision", "ALLOW");
        body.put("reservation_id", reservationId);
        body.put("expires_at_ms", System.currentTimeMillis() + 60000);
        body.put("affected_scopes", List.of("tenant:test-tenant"));
        body.put("scope_path", "tenant:test-tenant");
        body.put("reserved", Map.of("unit", "TOKENS", "amount", 1000));
        return body;
    }

    private Map<String, Object> denyResponse() {
        Map<String, Object> body = new HashMap<>();
        body.put("decision", "DENY");
        body.put("affected_scopes", List.of("tenant:test-tenant"));
        body.put("reason_code", "BUDGET_EXCEEDED");
        body.put("retry_after_ms", 5000);
        return body;
    }

    private Map<String, Object> allowWithCapsResponse(String reservationId) {
        Map<String, Object> body = allowResponse(reservationId);
        body.put("decision", "ALLOW_WITH_CAPS");
        body.put("caps", Map.of("max_tokens", 500, "cooldown_ms", 1000));
        return body;
    }

    private Map<String, Object> commitSuccessResponse() {
        return Map.of("status", "COMMITTED",
                "charged", Map.of("unit", "TOKENS", "amount", 1000));
    }

    private Map<String, Object> releaseSuccessResponse() {
        return Map.of("status", "RELEASED",
                "released", Map.of("unit", "TOKENS", "amount", 1000));
    }

    // ========================================================================
    // Happy path
    // ========================================================================

    @Nested
    @DisplayName("Happy path: reserve -> execute -> commit")
    class HappyPath {

        @Test
        void shouldExecuteFullLifecycle() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(eq("1000"), eq(method), eq(args), isNull(), eq(target)))
                    .thenReturn(1000L);
            when(requestBuilderService.buildReservation(eq(cycles), eq(1000L), eq("llm"), eq("complete"), isNull()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-1")));
            when(requestBuilderService.buildCommit(eq(cycles), eq(1000L), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(eq("res-1"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            Object result = service.executeWithReservation(
                    () -> "hello",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            assertThat(result).isEqualTo("hello");
            verify(client).createReservation(any(Object.class));
            InOrder settlementOrder = inOrder(retryEngine, client);
            settlementOrder.verify(retryEngine).persistPending(
                    eq("res-1"), any(), any());
            settlementOrder.verify(client).commitReservation(
                    eq("res-1"), any(Object.class));
            settlementOrder.verify(retryEngine).discardPending("res-1");
            verify(client, never()).releaseReservation(anyString(), any(Object.class));
        }

        @Test
        void shouldTreatProtocolInvalidCommit2xxAsAmbiguous() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(
                    any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-ambiguous")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(eq("res-ambiguous"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "COMMITTED")));

            service.executeWithReservation(
                    () -> "ok", cycles, method, args, this, "llm", "complete");

            verify(retryEngine).persistPending(eq("res-ambiguous"), any(), any());
            verify(retryEngine).schedule(eq("res-ambiguous"), any(), any(), isNull());
            verify(retryEngine, never()).discardPending("res-ambiguous");
        }

        @Test
        void shouldSetAndClearContext() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-ctx")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            AtomicReference<CyclesReservationContext> capturedCtx = new AtomicReference<>();
            service.executeWithReservation(
                    () -> {
                        capturedCtx.set(CyclesContextHolder.get());
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            // Context was available during execution
            assertThat(capturedCtx.get()).isNotNull();
            assertThat(capturedCtx.get().getReservationId()).isEqualTo("res-ctx");
            // Context is cleared after execution
            assertThat(CyclesContextHolder.get()).isNull();
        }

        @Test
        void shouldUseActualExpressionWhenProvided() throws Throwable {
            Cycles cycles = mockCycles(false);
            when(cycles.actual()).thenReturn("#result.length()");
            when(cycles.useEstimateIfActualNotProvided()).thenReturn(false);

            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(eq("1000"), eq(method), eq(args), isNull(), eq(target)))
                    .thenReturn(1000L);
            when(evaluator.evaluate(eq("#result.length()"), eq(method), eq(args), eq("hello"), eq(target)))
                    .thenReturn(5L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-actual")));
            when(requestBuilderService.buildCommit(eq(cycles), eq(5L), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            service.executeWithReservation(
                    () -> "hello",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(requestBuilderService).buildCommit(eq(cycles), eq(5L), any(), any());
        }

        @Test
        void shouldMergeAnnotationMetadataWithContextMetadata() throws Throwable {
            Cycles cycles = mockCycles(false);
            String metadataExpression = "{ 'request_id': #args[0], 'source': 'annotation' }";
            when(cycles.metadata()).thenReturn(metadataExpression);

            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;
            Map<String, Object> annotationMetadata = Map.of(
                    "request_id", "req-123",
                    "source", "annotation");
            Map<String, Object> expectedMetadata = Map.of(
                    "request_id", "req-123",
                    "source", "context",
                    "user", "alice",
                    // no actual expression + useEstimateIfActualNotProvided=true -> marker
                    "actual_source", "estimate");

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(evaluator.evaluateMap(eq(metadataExpression), eq(method), eq(args), eq("hello"), eq(target)))
                    .thenReturn(annotationMetadata);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-meta")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            service.executeWithReservation(
                    () -> {
                        CyclesContextHolder.get().setCommitMetadata(Map.of(
                                "source", "context",
                                "user", "alice"));
                        return "hello";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(requestBuilderService).buildCommit(
                    eq(cycles), eq(1000L), any(CyclesMetrics.class), eq(expectedMetadata));
        }

        @Test
        void shouldSkipAnnotationMetadataWhenEvaluationFails() throws Throwable {
            Cycles cycles = mockCycles(false);
            String metadataExpression = "{ 'request_id': #missing.value }";
            when(cycles.metadata()).thenReturn(metadataExpression);

            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(evaluator.evaluateMap(eq(metadataExpression), eq(method), eq(args), eq("hello"), eq(target)))
                    .thenThrow(new IllegalArgumentException("bad metadata"));
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-meta-fail")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            Object result = service.executeWithReservation(
                    () -> "hello",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            assertThat(result).isEqualTo("hello");
            // Annotation metadata was skipped; only the estimate-fallback marker remains
            verify(requestBuilderService).buildCommit(
                    eq(cycles), eq(1000L), any(CyclesMetrics.class),
                    eq(Map.of("actual_source", "estimate")));
        }
    }

    // ========================================================================
    // DENY decision
    // ========================================================================

    @Nested
    @DisplayName("DENY decision")
    class DenyDecision {

        @Test
        void shouldThrowProtocolExceptionOnDeny() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, denyResponse()));

            AtomicBoolean actionExecuted = new AtomicBoolean(false);

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> { actionExecuted.set(true); return "should not run"; },
                    cycles, method, args, target,
                    "llm", "complete"
            ))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("denied");

            assertThat(actionExecuted.get()).isFalse();
            verify(client, never()).commitReservation(anyString(), any(Object.class));
        }
    }

    // ========================================================================
    // ALLOW_WITH_CAPS
    // ========================================================================

    @Nested
    @DisplayName("ALLOW_WITH_CAPS decision")
    class AllowWithCaps {

        @Test
        void shouldProceedWithCapsInContext() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowWithCapsResponse("res-caps")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            AtomicReference<Caps> capturedCaps = new AtomicReference<>();
            service.executeWithReservation(
                    () -> {
                        CyclesReservationContext ctx = CyclesContextHolder.get();
                        capturedCaps.set(ctx.getCaps());
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            assertThat(capturedCaps.get()).isNotNull();
            assertThat(capturedCaps.get().getMaxTokens()).isEqualTo(500);
        }
    }

    // ========================================================================
    // Dry-run
    // ========================================================================

    @Nested
    @DisplayName("Dry-run mode")
    class DryRun {

        @Test
        void shouldReturnDryRunResultOnAllow() throws Throwable {
            Cycles cycles = mockCycles(true);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-dry")));

            Object result = service.executeWithReservation(
                    () -> { throw new AssertionError("Should not execute"); },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            assertThat(result).isInstanceOf(DryRunResult.class);
            DryRunResult dryRun = (DryRunResult) result;
            assertThat(dryRun.getDecision()).isEqualTo(Decision.ALLOW);
            assertThat(dryRun.isAllowed()).isTrue();

            verify(client, never()).commitReservation(anyString(), any(Object.class));
        }

        @Test
        void shouldThrowOnDryRunDeny() throws Throwable {
            Cycles cycles = mockCycles(true);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, denyResponse()));

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "should not run",
                    cycles, method, args, target,
                    "llm", "complete"
            ))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("Dry-run denied");
        }
    }

    // ========================================================================
    // Reservation HTTP error
    // ========================================================================

    @Nested
    @DisplayName("Reservation HTTP error")
    class ReservationHttpError {

        @Test
        void shouldThrowOnReservationFailure() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));

            Map<String, Object> errorBody = Map.of(
                    "error", "BUDGET_EXCEEDED",
                    "message", "No budget left",
                    "request_id", "req-1"
            );
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(409, "No budget left", errorBody));

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "nope",
                    cycles, method, args, target,
                    "llm", "complete"
            ))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("No budget left");
        }
    }

    // ========================================================================
    // Guarded action failure -> release
    // ========================================================================

    @Nested
    @DisplayName("Guarded action failure")
    class GuardedActionFailure {

        @Test
        void shouldReleaseOnActionException() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-fail")));
            when(requestBuilderService.buildRelease(anyString()))
                    .thenReturn(Map.of("idempotency_key", "rel-1", "reason", "guarded_method_failed"));
            when(client.releaseReservation(eq("res-fail"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, releaseSuccessResponse()));

            RuntimeException actionError = new RuntimeException("Boom!");
            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> { throw actionError; },
                    cycles, method, args, target,
                    "llm", "complete"
            ))
                    .isSameAs(actionError);

            verify(client).releaseReservation(eq("res-fail"), any(Object.class));
            verify(client, never()).commitReservation(anyString(), any(Object.class));
            // Context should be cleared
            assertThat(CyclesContextHolder.get()).isNull();
        }
    }

    // ========================================================================
    // Commit failure -> retry or release
    // ========================================================================

    @Nested
    @DisplayName("Commit failure handling")
    class CommitFailure {

        @Test
        void shouldScheduleRetryOnTransportError() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-retry")));
            Map<String, Object> commitBody = Map.of("idempotency_key", "com-1");
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(commitBody);
            when(client.commitReservation(eq("res-retry"), any(Object.class)))
                    .thenReturn(CyclesResponse.transportError(new RuntimeException("connection reset")));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(retryEngine).schedule(eq("res-retry"), eq(commitBody), anyMap(), isNull());
        }

        @Test
        void shouldScheduleRetryOn5xx() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-5xx")));
            Map<String, Object> commitBody = Map.of("idempotency_key", "com-1");
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(commitBody);
            when(client.commitReservation(eq("res-5xx"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(500, "Internal error",
                            Map.of("error", "INTERNAL_ERROR", "message", "Internal error", "request_id", "r1")));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(retryEngine).schedule(eq("res-5xx"), eq(commitBody), anyMap(), isNull());
        }

        @Test
        void shouldSkipReleaseOnReservationFinalized() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-fin")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(eq("res-fin"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(409, "Already finalized",
                            Map.of("error", "RESERVATION_FINALIZED", "message", "Already finalized", "request_id", "r1")));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            // Should NOT release or retry
            verify(client, never()).releaseReservation(anyString(), any(Object.class));
            verify(retryEngine, never()).schedule(anyString(), any(), any(), any());
            verify(retryEngine, never()).scheduleEvent(anyString(), any());
        }

        @Test
        void shouldNotReleaseOnIdempotencyMismatch() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-idem")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(eq("res-idem"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(409, "Idempotency mismatch",
                            Map.of("error", "IDEMPOTENCY_MISMATCH", "message", "Idempotency mismatch", "request_id", "r1")));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            // Should NOT release or retry — reservation state is uncertain
            verify(client, never()).releaseReservation(anyString(), any(Object.class));
            verify(retryEngine, never()).schedule(anyString(), any(), any(), any());
            verify(retryEngine, never()).scheduleEvent(anyString(), any());
        }

        @Test
        void shouldNotReleaseKnownSpendOnNonRetryableCommit4xx() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-4xx")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(eq("res-4xx"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(400, "Bad request",
                            Map.of("error", "INVALID_REQUEST", "message", "Bad request", "request_id", "r1")));
            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(retryEngine).discardPending("res-4xx");
            verify(client, never()).releaseReservation(anyString(), any(Object.class));
        }

        @Test
        void shouldScheduleRetryOnCommitException() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-exc")));
            Map<String, Object> commitBody = Map.of("idempotency_key", "com-1");
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(commitBody);
            when(client.commitReservation(eq("res-exc"), any(Object.class)))
                    .thenThrow(new RuntimeException("Unexpected failure"));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(retryEngine).schedule(eq("res-exc"), eq(commitBody), anyMap(), isNull());
        }

        @Test
        void shouldScheduleWithRetryAfterAndNotReleaseOn429() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-429")));
            Map<String, Object> commitBody = Map.of("idempotency_key", "com-1");
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(commitBody);
            when(client.commitReservation(eq("res-429"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(429, "Rate limited",
                            Map.of("error", "LIMIT_EXCEEDED", "message", "Rate limited", "request_id", "r1"),
                            3000));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(retryEngine).schedule(eq("res-429"), eq(commitBody), anyMap(), eq(3000));
            verify(client, never()).releaseReservation(anyString(), any(Object.class));
        }

        @Test
        void shouldScheduleRetryOnBodyless429() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-429b")));
            Map<String, Object> commitBody = Map.of("idempotency_key", "com-1");
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(commitBody);
            // 429 without an error body (proxy/load-balancer throttle)
            when(client.commitReservation(eq("res-429b"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(429, "Too many requests", Map.of()));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(retryEngine).schedule(eq("res-429b"), eq(commitBody), anyMap(), isNull());
            verify(client, never()).releaseReservation(anyString(), any(Object.class));
        }

        @Test
        void shouldScheduleAndNotReleaseOnAuthFailure() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-401")));
            Map<String, Object> commitBody = Map.of("idempotency_key", "com-1");
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(commitBody);
            when(client.commitReservation(eq("res-401"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(401, "Unauthorized",
                            Map.of("error", "UNAUTHORIZED", "message", "Unauthorized", "request_id", "r1")));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            // Never release — that would return budget for spend that already happened
            verify(retryEngine).schedule(eq("res-401"), eq(commitBody), anyMap(), isNull());
            verify(client, never()).releaseReservation(anyString(), any(Object.class));
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldScheduleEventFallbackAndNotReleaseOnExpired() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            Map<String, Object> subject = Map.of("tenant", "test-tenant");
            Map<String, Object> action = Map.of("kind", "llm", "name", "complete");
            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1", "subject", subject, "action", action));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-exp")));
            Map<String, Object> actual = Map.of("unit", "TOKENS", "amount", 1000);
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1", "actual", actual,
                            "metadata", Map.of("trace", "abc")));
            when(client.commitReservation(eq("res-exp"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(410, "Expired",
                            Map.of("error", "RESERVATION_EXPIRED", "message", "Expired", "request_id", "r1")));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            org.mockito.ArgumentCaptor<Map<String, Object>> fallbackCaptor =
                    org.mockito.ArgumentCaptor.forClass(Map.class);
            verify(retryEngine).scheduleEvent(eq("res-exp"), fallbackCaptor.capture());
            verify(retryEngine, never()).schedule(anyString(), any(), any(), any());
            verify(client, never()).releaseReservation(anyString(), any(Object.class));

            Map<String, Object> fallback = fallbackCaptor.getValue();
            assertThat(fallback)
                    .containsEntry("idempotency_key", "com-1")
                    .containsEntry("subject", subject)
                    .containsEntry("action", action)
                    .containsEntry("actual", actual)
                    .doesNotContainKey("overage_policy");
            Map<String, Object> metadata = (Map<String, Object>) fallback.get("metadata");
            assertThat(metadata)
                    .containsEntry("trace", "abc")
                    .containsEntry("recovered_reservation_id", "res-exp")
                    .containsEntry("recovery_reason", "commit_after_reservation_expired");
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldCarryFallbackWithRecoveryMetadataOnTransientFailure() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            Map<String, Object> subject = Map.of("tenant", "test-tenant");
            Map<String, Object> action = Map.of("kind", "llm", "name", "complete");
            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1", "subject", subject, "action", action));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-fb")));
            Map<String, Object> metrics = Map.of("latency_ms", 12);
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1",
                            "actual", Map.of("unit", "TOKENS", "amount", 1000),
                            "metrics", metrics));
            when(client.commitReservation(eq("res-fb"), any(Object.class)))
                    .thenReturn(CyclesResponse.transportError(new RuntimeException("connection reset")));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            org.mockito.ArgumentCaptor<Map<String, Object>> fallbackCaptor =
                    org.mockito.ArgumentCaptor.forClass(Map.class);
            verify(retryEngine).schedule(eq("res-fb"), anyMap(), fallbackCaptor.capture(), isNull());

            Map<String, Object> fallback = fallbackCaptor.getValue();
            assertThat(fallback)
                    .containsEntry("subject", subject)
                    .containsEntry("action", action)
                    .containsEntry("metrics", metrics);
            Map<String, Object> metadata = (Map<String, Object>) fallback.get("metadata");
            assertThat(metadata)
                    .containsEntry("recovered_reservation_id", "res-fb")
                    .containsEntry("recovery_reason", "commit_after_reservation_expired");
        }
    }

    // ========================================================================
    // resolveEstimateExpression
    // ========================================================================

    @Nested
    @DisplayName("Estimate expression resolution")
    class EstimateExpression {

        @Test
        void shouldRejectBothValueAndEstimate() {
            Cycles cycles = mockCycles(false);
            when(cycles.value()).thenReturn("100");
            when(cycles.estimate()).thenReturn("200");

            Method method;
            try { method = dummyMethod(); } catch (NoSuchMethodException e) { throw new RuntimeException(e); }
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            Method finalMethod = method;
            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "nope",
                    cycles, finalMethod, args, target,
                    "llm", "complete"
            ))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not both");
        }

        @Test
        void shouldRejectNeitherValueNorEstimate() {
            Cycles cycles = mockCycles(false);
            when(cycles.value()).thenReturn("");
            when(cycles.estimate()).thenReturn("");

            Method method;
            try { method = dummyMethod(); } catch (NoSuchMethodException e) { throw new RuntimeException(e); }
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            Method finalMethod = method;
            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "nope",
                    cycles, finalMethod, args, target,
                    "llm", "complete"
            ))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("required");
        }
    }

    // ========================================================================
    // resolveActualAmount
    // ========================================================================

    @Nested
    @DisplayName("Actual amount resolution")
    class ActualAmount {

        @Test
        void shouldThrowWhenActualRequiredButNotProvided() throws Throwable {
            Cycles cycles = mockCycles(false);
            when(cycles.actual()).thenReturn("");
            when(cycles.useEstimateIfActualNotProvided()).thenReturn(false);

            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            AtomicBoolean actionRan = new AtomicBoolean(false);

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> {
                        actionRan.set(true);
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            ))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Actual expression required");

            assertThat(actionRan).isFalse();
            verify(client, never()).createReservation(any(Object.class));
            verify(client, never()).releaseReservation(anyString(), any(Object.class));
            verify(client, never()).commitReservation(anyString(), any(Object.class));
            verify(retryEngine, never()).scheduleEvent(anyString(), any());
        }

        @Test
        void shouldCommitEstimateWhenActualExpressionFailsAfterAction() throws Throwable {
            Cycles cycles = mockCycles(false);
            when(cycles.actual()).thenReturn("#result.missing");
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(eq("1000"), eq(method), eq(args), isNull(), eq(target)))
                    .thenReturn(1000L);
            when(evaluator.evaluate(eq("#result.missing"), eq(method), eq(args), eq("ok"), eq(target)))
                    .thenThrow(new IllegalArgumentException("bad actual expression"));
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-actual-fallback")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(eq("res-actual-fallback"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            Object result = service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            assertThat(result).isEqualTo("ok");
            verify(requestBuilderService).buildCommit(
                    eq(cycles), eq(1000L), any(CyclesMetrics.class),
                    eq(Map.of("actual_source", "estimate")));
            verify(client, never()).releaseReservation(anyString(), any(Object.class));
        }
    }

    // ========================================================================
    // Estimate-committed-as-actual marker
    // ========================================================================

    @Nested
    @DisplayName("Estimate-committed-as-actual marker")
    class EstimateAsActualMarker {

        @Test
        void shouldMarkCommitMetadataWhenEstimateUsedAsActual() throws Throwable {
            // actual is blank + useEstimateIfActualNotProvided=true -> the commit records
            // the estimate as measured spend, so it must carry actual_source=estimate.
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-marker")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(requestBuilderService).buildCommit(
                    eq(cycles), eq(1000L), any(CyclesMetrics.class),
                    eq(Map.of("actual_source", "estimate")));
        }

        @Test
        void shouldNotMarkCommitMetadataWhenExplicitActualProvided() throws Throwable {
            // An explicit actual expression is a measured value — no marker, even with
            // useEstimateIfActualNotProvided left at true (the fallback branch is not taken).
            Cycles cycles = mockCycles(false);
            when(cycles.actual()).thenReturn("#result.length()");
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(eq("1000"), eq(method), eq(args), isNull(), eq(target)))
                    .thenReturn(1000L);
            when(evaluator.evaluate(eq("#result.length()"), eq(method), eq(args), eq("hello"), eq(target)))
                    .thenReturn(5L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-nomarker")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            service.executeWithReservation(
                    () -> "hello",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(requestBuilderService).buildCommit(
                    eq(cycles), eq(5L), any(CyclesMetrics.class), isNull());
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldCarryMarkerIntoEventFallbackBody() throws Throwable {
            // The /v1/events fallback body copies the commit's metadata, so the marker
            // must survive when an expired reservation flips the commit to an event.
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            Map<String, Object> subject = Map.of("tenant", "test-tenant");
            Map<String, Object> action = Map.of("kind", "llm", "name", "complete");
            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1", "subject", subject, "action", action));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-marker-fb")));
            // Mirror the real builder: the metadata argument lands in the commit body
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenAnswer(invocation -> {
                        Map<String, Object> body = new HashMap<>();
                        body.put("idempotency_key", "com-1");
                        body.put("actual", Map.of("unit", "TOKENS", "amount", 1000));
                        Map<String, Object> metadata = invocation.getArgument(3);
                        if (metadata != null) {
                            body.put("metadata", metadata);
                        }
                        return body;
                    });
            when(client.commitReservation(eq("res-marker-fb"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(410, "Expired",
                            Map.of("error", "RESERVATION_EXPIRED", "message", "Expired", "request_id", "r1")));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            org.mockito.ArgumentCaptor<Map<String, Object>> fallbackCaptor =
                    org.mockito.ArgumentCaptor.forClass(Map.class);
            verify(retryEngine).scheduleEvent(eq("res-marker-fb"), fallbackCaptor.capture());

            Map<String, Object> metadata = (Map<String, Object>) fallbackCaptor.getValue().get("metadata");
            assertThat(metadata)
                    .containsEntry("actual_source", "estimate")
                    .containsEntry("recovered_reservation_id", "res-marker-fb")
                    .containsEntry("recovery_reason", "commit_after_reservation_expired");
        }
    }

    // ========================================================================
    // Null reservation ID
    // ========================================================================

    @Nested
    @DisplayName("Missing reservation ID")
    class MissingReservationId {

        @Test
        void shouldThrowWhenReservationIdMissing() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));

            Map<String, Object> responseWithoutId = new HashMap<>();
            responseWithoutId.put("decision", "ALLOW");
            responseWithoutId.put("expires_at_ms", System.currentTimeMillis() + 60000);
            responseWithoutId.put("affected_scopes", List.of("tenant:test-tenant"));
            // no reservation_id

            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, responseWithoutId));

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "nope",
                    cycles, method, args, target,
                    "llm", "complete"
            ))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("missing reservation identifier");
        }
    }

    // ========================================================================
    // Metrics from context
    // ========================================================================

    @Nested
    @DisplayName("Metrics from context")
    class MetricsFromContext {

        @Test
        void shouldIncludeMetricsSetDuringExecution() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-metrics")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            service.executeWithReservation(
                    () -> {
                        CyclesReservationContext ctx = CyclesContextHolder.get();
                        CyclesMetrics metrics = new CyclesMetrics();
                        metrics.setTokensInput(100);
                        metrics.setTokensOutput(50);
                        ctx.setMetrics(metrics);
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            // Verify that buildCommit was called with a metrics object containing our values
            verify(requestBuilderService).buildCommit(eq(cycles), eq(1000L), argThat(m -> {
                if (m instanceof CyclesMetrics cm) {
                    return cm.getTokensInput() == 100 && cm.getTokensOutput() == 50;
                }
                return false;
            }), any());
        }
    }

    // ========================================================================
    // Heartbeat scheduling and cancellation
    // ========================================================================

    @Nested
    @DisplayName("Heartbeat scheduling and cancellation")
    class Heartbeat {

        // Deterministic monotonic clock for lead-estimate heartbeat tests. The service
        // reads it via the visible-for-testing LongSupplier constructor parameter.
        private final AtomicLong nanoClock = new AtomicLong(0);

        /** Replaces the shared service with one driven by the deterministic clock. */
        private void useClockedService() {
            service = new CyclesLifecycleService(
                    client, retryEngine, requestBuilderService, evaluator, heartbeatExecutor, nanoClock::get);
        }

        /**
         * Same, but with a KNOWN enforced per-attempt HTTP timeout (ms) so field-mode
         * scheduling has a finite attempt budget:
         * reserve = 2 x max(timeout, 1000, 2 x maxRtt) + max(1000, 2 x maxRtt).
         */
        private void useClockedService(long requestTimeoutMs) {
            service = new CyclesLifecycleService(
                    client, retryEngine, requestBuilderService, evaluator, heartbeatExecutor,
                    nanoClock::get, requestTimeoutMs);
        }

        private void advanceClockMs(long ms) {
            nanoClock.addAndGet(ms * 1_000_000L);
        }

        /** Reservation response with a fixed server-frame expiry (no client wall clock). */
        private Map<String, Object> allowResponseWithExpiry(String reservationId, long expiresAtMs) {
            Map<String, Object> body = allowResponse(reservationId);
            body.put("expires_at_ms", expiresAtMs);
            return body;
        }

        @SuppressWarnings("unchecked")
        private String idempotencyKeyOf(Object body) {
            return String.valueOf(((Map<String, Object>) body).get("idempotency_key"));
        }

        @Test
        void shouldScheduleFirstBeatImmediately() throws Throwable {
            // v2.3: ANY bounded first-beat delay can outlive a small capped lease (tenant
            // policy max_reservation_ttl_ms caps silently and the create response has no
            // effective-TTL field), so the first one-shot fires at ~0ms and primes the
            // grant observation that drives all later cadence.
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-hb")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(heartbeatExecutor).schedule(
                    any(Runnable.class), eq(0L), eq(TimeUnit.MILLISECONDS));
        }

        @Test
        void shouldRetryFailedFirstBeatAtHeldCadenceNotZeroWithSameKey() throws Throwable {
            // The first beat fires at 0, but a transiently FAILED first extend must not
            // reschedule at 0 (a hot-loop against a down server): before any grant is
            // observed the retry delay is the held cadence min(requestedTtl/2, 30s)
            // = 30000 for a 60s request — and the retry replays the SAME idempotency key.
            useClockedService();
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(60000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponseWithExpiry("res-hb-60", initialExpiry)));
            when(requestBuilderService.buildExtend(eq(60000L), isNull()))
                    .thenReturn(Map.of("idempotency_key", "ext-template", "extend_by_ms", 60000L));
            when(client.extendReservation(eq("res-hb-60"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(503, "Unavailable", Map.of()))
                    .thenReturn(CyclesResponse.success(200,
                            Map.of("status", "ACTIVE", "expires_at_ms", initialExpiry + 60000L)));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            service.executeWithReservation(
                    () -> {
                        Runnable beat = capturedHeartbeat.get();
                        beat.run(); // primed beat at t=0: extend fails transiently -> key kept
                        advanceClockMs(30000);
                        beat.run(); // retry at held cadence: SAME key, succeeds
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            // 0 (primed), then the held cadence after the transient failure — never 0
            // again — then the normal grant-derived cadence clamp(60000/2, 500, 30000)
            assertThat(scheduledDelays).containsExactly(0L, 30000L, 30000L);
            org.mockito.ArgumentCaptor<Object> bodyCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(client, times(2)).extendReservation(eq("res-hb-60"), bodyCaptor.capture());
            assertThat(idempotencyKeyOf(bodyCaptor.getAllValues().get(1)))
                    .isEqualTo(idempotencyKeyOf(bodyCaptor.getAllValues().get(0)));
        }

        @Test
        void shouldUseHalfTtlSteadyCadenceWithNoFloorForSmallTtl() throws Throwable {
            // ttl=1200: a 1000ms interval floor would beat with only 200ms of lifetime
            // left — a guaranteed lapse window. Steady-state cadence must be
            // clamp(1200/2, 500, 600) = 600 after the immediate first beat.
            useClockedService();
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(1200L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            AtomicLong serverExpiry = new AtomicLong(initialExpiry);
            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponseWithExpiry("res-hb-small", initialExpiry)));
            when(requestBuilderService.buildExtend(eq(1200L), isNull()))
                    .thenReturn(Map.of("idempotency_key", "ext-template", "extend_by_ms", 1200L));
            when(client.extendReservation(eq("res-hb-small"), any(Object.class)))
                    .thenAnswer(inv -> CyclesResponse.success(200,
                            Map.of("status", "ACTIVE", "expires_at_ms", serverExpiry.addAndGet(1200L))));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            service.executeWithReservation(
                    () -> {
                        Runnable beat = capturedHeartbeat.get();
                        beat.run();           // beat 1 at 0: leadMin 0, no grant yet -> extend, grant 1200
                        advanceClockMs(600);  // beat 2: leadMin 1200-600=600 < 1800 -> extend
                        beat.run();
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            // first beat immediate; next delays clamp(1200/2, 500, 600) = 600, no floor
            assertThat(scheduledDelays).containsExactly(0L, 600L, 600L);
            verify(client, times(2)).extendReservation(eq("res-hb-small"), any(Object.class));
        }

        @Test
        void shouldNotScheduleHeartbeatWhenExpiresAtMsIsNull() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));

            // Response without expires_at_ms
            Map<String, Object> responseNoExpiry = new HashMap<>();
            responseNoExpiry.put("decision", "ALLOW");
            responseNoExpiry.put("reservation_id", "res-no-expiry");
            responseNoExpiry.put("affected_scopes", List.of("tenant:test-tenant"));

            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, responseNoExpiry));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(heartbeatExecutor, never()).schedule(
                    any(Runnable.class), anyLong(), any(TimeUnit.class));
        }

        @Test
        void shouldNotScheduleHeartbeatWhenTtlMsIsZero() throws Throwable {
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(0L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-zero-ttl")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(heartbeatExecutor, never()).schedule(
                    any(Runnable.class), anyLong(), any(TimeUnit.class));
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldCancelHeartbeatAfterCommit() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            ScheduledFuture<?> mockFuture = mock(ScheduledFuture.class);
            when(heartbeatExecutor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(inv -> mockFuture);

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-cancel")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(mockFuture).cancel(false);
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldCancelHeartbeatAfterActionFailure() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            ScheduledFuture<?> mockFuture = mock(ScheduledFuture.class);
            when(heartbeatExecutor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(inv -> mockFuture);

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-cancel-fail")));
            when(requestBuilderService.buildRelease(anyString()))
                    .thenReturn(Map.of("idempotency_key", "rel-1"));
            when(client.releaseReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, releaseSuccessResponse()));

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> { throw new RuntimeException("Boom"); },
                    cycles, method, args, target,
                    "llm", "complete"
            )).isInstanceOf(RuntimeException.class);

            verify(mockFuture).cancel(false);
        }

        @Test
        void shouldCallExtendAndUpdateContextOnHeartbeatTick() throws Throwable {
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            // Capture the Runnable passed to the one-shot schedule
            AtomicReference<Runnable> capturedHeartbeat = new AtomicReference<>();
            when(heartbeatExecutor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(invocation -> {
                        capturedHeartbeat.set(invocation.getArgument(0));
                        return mock(ScheduledFuture.class);
                    });

            long newExpiresAtMs = System.currentTimeMillis() + 80000;
            Map<String, Object> extendBody = Map.of("idempotency_key", "ext-1", "extend_by_ms", 20000);
            Map<String, Object> extendResponseBody = Map.of("status", "ACTIVE", "expires_at_ms", newExpiresAtMs);

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-extend")));
            when(requestBuilderService.buildExtend(eq(20000L), isNull()))
                    .thenReturn(extendBody);
            when(client.extendReservation(eq("res-extend"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, extendResponseBody));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            AtomicReference<CyclesReservationContext> capturedCtx = new AtomicReference<>();
            service.executeWithReservation(
                    () -> {
                        capturedCtx.set(CyclesContextHolder.get());
                        // Simulate heartbeat firing during execution
                        capturedHeartbeat.get().run();
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            // Verify extend was called with extend_by_ms = ttl and a per-attempt idempotency
            // key (the service overwrites the builder's key so failed attempts can reuse it)
            org.mockito.ArgumentCaptor<Object> extendBodyCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(client).extendReservation(eq("res-extend"), extendBodyCaptor.capture());
            Map<?, ?> sentBody = (Map<?, ?>) extendBodyCaptor.getValue();
            assertThat(((Number) sentBody.get("extend_by_ms")).longValue()).isEqualTo(20000L);
            assertThat(sentBody.get("idempotency_key")).isNotNull();
            // Verify context expiresAtMs was updated
            assertThat(capturedCtx.get().getExpiresAtMs()).isEqualTo(newExpiresAtMs);
        }

        @Test
        void shouldHandleExtendFailureGracefully() throws Throwable {
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = new AtomicReference<>();
            when(heartbeatExecutor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(invocation -> {
                        capturedHeartbeat.set(invocation.getArgument(0));
                        return mock(ScheduledFuture.class);
                    });

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-ext-fail")));
            when(requestBuilderService.buildExtend(anyLong(), isNull()))
                    .thenReturn(Map.of("idempotency_key", "ext-1", "extend_by_ms", 20000));
            when(client.extendReservation(eq("res-ext-fail"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(503, "Unavailable",
                            Map.of("error", "INTERNAL_ERROR", "message", "Unavailable", "request_id", "r1")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            AtomicReference<Long> expiresBeforeHeartbeat = new AtomicReference<>();

            Object result = service.executeWithReservation(
                    () -> {
                        CyclesReservationContext ctx = CyclesContextHolder.get();
                        expiresBeforeHeartbeat.set(ctx.getExpiresAtMs());
                        // Heartbeat fires but extend fails — should not crash
                        capturedHeartbeat.get().run();
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            // Lifecycle completed successfully despite heartbeat failure
            assertThat(result).isEqualTo("ok");
            // expiresAtMs should be unchanged since extend failed
            assertThat(CyclesContextHolder.get()).isNull(); // cleared after execution
        }

        @Test
        void shouldHandleExtendExceptionGracefully() throws Throwable {
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = new AtomicReference<>();
            when(heartbeatExecutor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(invocation -> {
                        capturedHeartbeat.set(invocation.getArgument(0));
                        return mock(ScheduledFuture.class);
                    });

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-ext-exc")));
            when(requestBuilderService.buildExtend(anyLong(), isNull()))
                    .thenReturn(Map.of("idempotency_key", "ext-1", "extend_by_ms", 20000L));
            when(client.extendReservation(eq("res-ext-exc"), any(Object.class)))
                    .thenThrow(new RuntimeException("Network down"))
                    .thenReturn(CyclesResponse.success(200,
                            Map.of("status", "ACTIVE", "expires_at_ms", 2_000_000L)));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            var appender = attachWarnAppender();
            try {
                Object result = service.executeWithReservation(
                        () -> {
                            // Heartbeat fires but throws — should not crash the action
                            capturedHeartbeat.get().run();
                            capturedHeartbeat.get().run();
                            return "ok";
                        },
                        cycles, method, args, target,
                        "llm", "complete"
                );

                assertThat(result).isEqualTo("ok");
                ArgumentCaptor<Object> extendBodies = ArgumentCaptor.forClass(Object.class);
                verify(client, times(2)).extendReservation(
                        eq("res-ext-exc"), extendBodies.capture());
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> sent = extendBodies.getAllValues().stream()
                        .map(value -> (Map<String, Object>) value)
                        .toList();
                assertThat(sent).allSatisfy(body ->
                        assertThat(body.get("extend_by_ms")).isEqualTo(20000L));
                assertThat(sent.get(0).get("idempotency_key"))
                        .isEqualTo(sent.get(1).get("idempotency_key"));
                verify(client).commitReservation(eq("res-ext-exc"), any(Object.class));
                assertThat(appender.list)
                        .filteredOn(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
                        .extracting(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                        .anySatisfy(message -> {
                            assertThat(message).contains("Heartbeat extend transport error");
                            assertThat(message).contains("same idempotency key");
                            assertThat(message).contains("res-ext-exc");
                        });
            } finally {
                detachAppender(appender);
            }
        }

        // Mock future returned by the one-shot schedule, kept so tests can verify
        // the heartbeat's self-cancellation on permanent failures.
        private ScheduledFuture<?> heartbeatFuture;

        // Every delay passed to the one-shot schedule, in order: the first entry is the
        // first-beat delay, each later entry is the self-rescheduled next delay.
        private final java.util.List<Long> scheduledDelays = new java.util.ArrayList<>();

        private AtomicReference<Runnable> captureHeartbeat() {
            heartbeatFuture = mock(ScheduledFuture.class);
            AtomicReference<Runnable> captured = new AtomicReference<>();
            when(heartbeatExecutor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(invocation -> {
                        captured.set(invocation.getArgument(0));
                        scheduledDelays.add(invocation.getArgument(1));
                        return heartbeatFuture;
                    });
            return captured;
        }

        /** Common stubs for ttl=20000 heartbeat scenarios (extend responses stubbed per test). */
        private void stubHeartbeatLifecycle(String reservationId, long initialExpiry) {
            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200,
                            allowResponseWithExpiry(reservationId, initialExpiry)));
            when(requestBuilderService.buildExtend(eq(20000L), isNull()))
                    .thenReturn(Map.of("idempotency_key", "ext-template", "extend_by_ms", 20000L));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));
        }

        @Test
        void shouldFollowLeadLowerBoundBeatPattern() throws Throwable {
            // Server grants a full requested ttl per extend; the first beat is immediate
            // and later beats fire every ttl/2. leadMin = grantsSum - elapsed starts at 0
            // (nothing proven), so the bound must be BUILT from observed grants before a
            // beat may skip:
            //   beat 1 (t=0):  leadMin 0-0 = 0, lastGrant null         -> extend (sum 20000)
            //   beat 2: leadMin 20000-10000 = 10000 < 1.5*20000        -> extend (sum 40000)
            //   beat 3: leadMin 40000-20000 = 20000 < 30000            -> extend (sum 60000)
            //   beat 4: leadMin 60000-30000 = 30000 >= 1.5*lastGrant   -> skip
            //   beat 5: leadMin 60000-40000 = 20000 < 30000            -> extend (sum 80000)
            //   beat 6: leadMin 80000-50000 = 30000 >= 30000           -> skip
            useClockedService();
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            AtomicLong serverExpiry = new AtomicLong(initialExpiry);
            java.util.concurrent.atomic.AtomicInteger extendCalls = new java.util.concurrent.atomic.AtomicInteger();
            stubHeartbeatLifecycle("res-beat", initialExpiry);
            when(client.extendReservation(eq("res-beat"), any(Object.class)))
                    .thenAnswer(inv -> {
                        extendCalls.incrementAndGet();
                        return CyclesResponse.success(200,
                                Map.of("status", "ACTIVE", "expires_at_ms", serverExpiry.addAndGet(20000L)));
                    });

            service.executeWithReservation(
                    () -> {
                        Runnable beat = capturedHeartbeat.get();
                        beat.run(); // beat 1 at t=0: no grant observed yet -> extend
                        assertThat(extendCalls.get()).isEqualTo(1);
                        advanceClockMs(10000);
                        beat.run(); // beat 2: leadMin 10000 -> extend
                        assertThat(extendCalls.get()).isEqualTo(2);
                        advanceClockMs(10000);
                        beat.run(); // beat 3: leadMin 20000 -> extend
                        assertThat(extendCalls.get()).isEqualTo(3);
                        advanceClockMs(10000);
                        beat.run(); // beat 4: leadMin 30000 >= 1.5*20000 -> skip, no HTTP call
                        assertThat(extendCalls.get()).isEqualTo(3);
                        advanceClockMs(10000);
                        beat.run(); // beat 5: leadMin 20000 -> extend
                        assertThat(extendCalls.get()).isEqualTo(4);
                        advanceClockMs(10000);
                        beat.run(); // beat 6: leadMin 30000 -> skip
                        assertThat(extendCalls.get()).isEqualTo(4);
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(client, times(4)).extendReservation(eq("res-beat"), any(Object.class));
            // Immediate first beat, then full grants keep every rescheduled delay at
            // clamp(20000/2, 500, 10000) = 10000
            assertThat(scheduledDelays)
                    .containsExactly(0L, 10000L, 10000L, 10000L, 10000L, 10000L, 10000L);
        }

        @Test
        void shouldReuseIdempotencyKeyOnRetryThenRegenerateAfterSuccess() throws Throwable {
            // A failed extend keeps its idempotency key so the retry replays the same
            // request (a lost response must not double-extend); after a 2xx the next
            // extend uses a fresh key.
            useClockedService();
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            stubHeartbeatLifecycle("res-key", initialExpiry);
            when(client.extendReservation(eq("res-key"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(500, "Server error", Map.of()))
                    .thenReturn(CyclesResponse.success(200,
                            Map.of("status", "ACTIVE", "expires_at_ms", initialExpiry + 20000L)))
                    .thenReturn(CyclesResponse.success(200,
                            Map.of("status", "ACTIVE", "expires_at_ms", initialExpiry + 40000L)));

            service.executeWithReservation(
                    () -> {
                        Runnable beat = capturedHeartbeat.get();
                        beat.run(); // beat 1 at t=0: extend fails (5xx) -> key kept pending
                        advanceClockMs(10000);
                        beat.run(); // beat 2: retry with the SAME key, succeeds (grant 20000)
                        advanceClockMs(10000);
                        beat.run(); // beat 3: leadMin 0 -> extend with a FRESH key
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            org.mockito.ArgumentCaptor<Object> bodyCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(client, times(3)).extendReservation(eq("res-key"), bodyCaptor.capture());
            String key1 = idempotencyKeyOf(bodyCaptor.getAllValues().get(0));
            String key2 = idempotencyKeyOf(bodyCaptor.getAllValues().get(1));
            String key3 = idempotencyKeyOf(bodyCaptor.getAllValues().get(2));
            assertThat(key1).isNotEqualTo("ext-template"); // service key overrides the builder's
            assertThat(key2).isEqualTo(key1);
            assertThat(key3).isNotEqualTo(key1);
        }

        @Test
        void shouldReuseIdempotencyKeyAfterExtendException() throws Throwable {
            // A thrown extend (transport error) also keeps its key for the next tick.
            useClockedService();
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            stubHeartbeatLifecycle("res-exc-key", initialExpiry);
            when(client.extendReservation(eq("res-exc-key"), any(Object.class)))
                    .thenThrow(new RuntimeException("connection reset"))
                    .thenReturn(CyclesResponse.success(200,
                            Map.of("status", "ACTIVE", "expires_at_ms", initialExpiry + 20000L)));

            service.executeWithReservation(
                    () -> {
                        Runnable tick = capturedHeartbeat.get();
                        tick.run(); // tick 1 at t=0: extend throws -> key kept pending
                        advanceClockMs(10000);
                        tick.run(); // tick 2: retries with the SAME key, succeeds
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            org.mockito.ArgumentCaptor<Object> bodyCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(client, times(2)).extendReservation(eq("res-exc-key"), bodyCaptor.capture());
            assertThat(idempotencyKeyOf(bodyCaptor.getAllValues().get(1)))
                    .isEqualTo(idempotencyKeyOf(bodyCaptor.getAllValues().get(0)));
        }

        @Test
        void shouldStopPermanentlyOnMaxExtensionsExceeded() throws Throwable {
            // MAX_EXTENSIONS_EXCEEDED can never succeed on retry: the heartbeat cancels
            // itself and later ticks make no HTTP calls.
            useClockedService();
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            stubHeartbeatLifecycle("res-max", 1_000_000L);
            when(client.extendReservation(eq("res-max"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(409, "Max extensions",
                            Map.of("error", "MAX_EXTENSIONS_EXCEEDED", "message", "Max extensions", "request_id", "r1")));

            service.executeWithReservation(
                    () -> {
                        Runnable tick = capturedHeartbeat.get();
                        advanceClockMs(10000);
                        tick.run(); // tick 1: permanent failure -> stop + self-cancel
                        // Self-cancellation happened inside the tick (the lifecycle's own
                        // finally-cancel has not run yet at this point)
                        verify(heartbeatFuture).cancel(false);
                        advanceClockMs(10000);
                        tick.run(); // tick 2: stopped -> no call
                        advanceClockMs(10000);
                        tick.run(); // tick 3: stopped -> no call
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(client, times(1)).extendReservation(eq("res-max"), any(Object.class));
            // One-shot scheduling: a stopped beat never reschedules itself, so only the
            // initial (immediate) first-beat schedule ever happened.
            assertThat(scheduledDelays).containsExactly(0L);
        }

        @Test
        void shouldStopPermanentlyOnBodyless410() throws Throwable {
            // A bare 410 (proxy-stripped body) still means the reservation is gone.
            useClockedService();
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            stubHeartbeatLifecycle("res-410", 1_000_000L);
            when(client.extendReservation(eq("res-410"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(410, "Gone", Map.of()));

            service.executeWithReservation(
                    () -> {
                        Runnable tick = capturedHeartbeat.get();
                        advanceClockMs(10000);
                        tick.run(); // tick 1: 410 -> stop
                        advanceClockMs(10000);
                        tick.run(); // tick 2: stopped -> no call
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(client, times(1)).extendReservation(eq("res-410"), any(Object.class));
        }

        @Test
        void shouldStopPermanentlyOnReservationFinalized() throws Throwable {
            // RESERVATION_FINALIZED means committed/released elsewhere — nothing to keep alive.
            useClockedService();
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            stubHeartbeatLifecycle("res-final", 1_000_000L);
            when(client.extendReservation(eq("res-final"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(409, "Finalized",
                            Map.of("error", "RESERVATION_FINALIZED", "message", "Finalized", "request_id", "r1")));

            service.executeWithReservation(
                    () -> {
                        Runnable tick = capturedHeartbeat.get();
                        advanceClockMs(10000);
                        tick.run(); // tick 1: RESERVATION_FINALIZED -> stop
                        advanceClockMs(10000);
                        tick.run(); // tick 2: stopped -> no call
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(client, times(1)).extendReservation(eq("res-final"), any(Object.class));
        }

        // NOTE: v2.1 had a shouldSkipCatchUpTickWithNoClockAdvance test pinning the
        // fixed-rate catch-up hazard (queued missed ticks firing back-to-back). One-shot
        // self-rescheduling removes that hazard by construction — the next beat is only
        // scheduled when the current one finishes, so there is no queue of missed ticks.

        @Test
        void shouldAdaptCadenceToCappedGrantAndKeepRequestedExtendAmount() throws Throwable {
            // Tenant policy clamps every grant to 4000ms while the client requests 20000
            // — but each extend still gains REAL lease (grant 4000 > 1.25×elapsed 2000),
            // so this is the grant-clamp regime, not the lead-clamp one: the observed
            // grant drives the cadence down to clamp(4000/2, 500, 10000) = 2000ms, while
            // extend_by_ms stays the REQUESTED 20000 (the server clamps it; sending the
            // observed grant would compound the clamp).
            useClockedService();
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            AtomicLong serverExpiry = new AtomicLong(initialExpiry);
            stubHeartbeatLifecycle("res-clamp", initialExpiry);
            when(client.extendReservation(eq("res-clamp"), any(Object.class)))
                    .thenAnswer(inv -> CyclesResponse.success(200,
                            Map.of("status", "ACTIVE", "expires_at_ms", serverExpiry.addAndGet(4000L))));

            service.executeWithReservation(
                    () -> {
                        Runnable beat = capturedHeartbeat.get();
                        beat.run(); // beat 1 at t=0: no grant yet -> extend; grant 4000 -> delay 2000
                        advanceClockMs(2000);
                        beat.run(); // beat 2: leadMin 4000-2000 = 2000 < 6000 -> extend
                        advanceClockMs(2000);
                        beat.run(); // beat 3: leadMin 8000-4000 = 4000 < 6000 -> extend
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(client, times(3)).extendReservation(eq("res-clamp"), any(Object.class));
            // extend_by_ms is always the requested ttl, never the observed grant
            verify(requestBuilderService, times(3)).buildExtend(eq(20000L), isNull());
            verify(requestBuilderService, never()).buildExtend(eq(4000L), isNull());
            // immediate first beat, then the observed grant halves the cadence
            assertThat(scheduledDelays).containsExactly(0L, 2000L, 2000L, 2000L);
        }

        @Test
        void shouldKeepSameKeyAndStateWhen2xxIsNotSchemaValid() throws Throwable {
            // A 200 without the required response body/expires_at_ms is
            // ambiguous, not an observed success.
            useClockedService();
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            stubHeartbeatLifecycle("res-noexp", initialExpiry);
            when(client.extendReservation(eq("res-noexp"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, null))                       // no body at all
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "ACTIVE"))); // no expires_at_ms

            AtomicReference<CyclesReservationContext> capturedCtx = new AtomicReference<>();
            service.executeWithReservation(
                    () -> {
                        capturedCtx.set(CyclesContextHolder.get());
                        Runnable beat = capturedHeartbeat.get();
                        beat.run(); // ambiguous: keep key and expiry
                        advanceClockMs(10000);
                        beat.run(); // retry the same key; still ambiguous
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(client, times(2)).extendReservation(eq("res-noexp"), any(Object.class));
            assertThat(capturedCtx.get().getExpiresAtMs()).isEqualTo(initialExpiry);
            ArgumentCaptor<Object> bodies = ArgumentCaptor.forClass(Object.class);
            verify(client, times(2)).extendReservation(eq("res-noexp"), bodies.capture());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> sent = bodies.getAllValues().stream()
                    .map(value -> (Map<String, Object>) value)
                    .toList();
            assertThat(sent.get(0).get("idempotency_key"))
                    .isEqualTo(sent.get(1).get("idempotency_key"));
        }

        // NOTE: v2.2 derived a first-beat delay from the HTTP Date header
        // (expires_at_ms − Date, halved). v2.3 removed it: any bounded first-beat
        // delay can outlive a small capped lease, so the first one-shot fires at 0
        // and the Date hint has nothing left to shorten. DefaultCyclesClient still
        // captures Date (CyclesResponse.getDateMs()) as a general accessor, with
        // its own tests in DefaultCyclesClientTest.

        /** WARN events currently recorded on the CyclesLifecycleService logger. */
        private ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> attachWarnAppender() {
            ch.qos.logback.classic.Logger logger =
                    (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(CyclesLifecycleService.class);
            ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                    new ch.qos.logback.core.read.ListAppender<>();
            appender.start();
            logger.addAppender(appender);
            return appender;
        }

        private void detachAppender(
                ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender) {
            ((ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(CyclesLifecycleService.class))
                    .detachAppender(appender);
        }

        private long warnCount(
                ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender,
                String messageFragment) {
            return appender.list.stream()
                    .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
                    .filter(e -> e.getFormattedMessage().contains(messageFragment))
                    .count();
        }

        private long leadClampWarnCount(
                ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender) {
            return warnCount(appender, "lead-clamped");
        }

        @Test
        void shouldHoldCadenceAndWarnOnceUnderLeadClamp() throws Throwable {
            // Maximum-LEAD clamp: a depleting budget holds expiry at ≈ now+L, so each
            // extend response echoes INITIAL + elapsed — successive expires_at_ms
            // differences measure ELAPSED time, not lease. Feeding them into grant/2
            // would collapse the cadence to the floor and burn max_extensions in
            // seconds. v2.3 detects the regime (grant ≤ 0, or grant < 0.9×requested and
            // ≤ 1.25×elapsed), HOLDS the cadence at min(requestedTtl/2, 30s) = 10000,
            // keeps extending every tick (leadMin stays low), and warns exactly once.
            useClockedService();
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            stubHeartbeatLifecycle("res-leadclamp", initialExpiry);
            when(client.extendReservation(eq("res-leadclamp"), any(Object.class)))
                    .thenAnswer(inv -> CyclesResponse.success(200, Map.of(
                            "status", "ACTIVE",
                            // expiry held at INITIAL + elapsed (injected clock, ms)
                            "expires_at_ms", initialExpiry + nanoClock.get() / 1_000_000L)));

            var appender = attachWarnAppender();
            try {
                service.executeWithReservation(
                        () -> {
                            Runnable beat = capturedHeartbeat.get();
                            beat.run(); // beat 1 at t=0: grant 0 -> lead-clamp, warn, hold 10000
                            advanceClockMs(10000);
                            beat.run(); // beat 2: leadMin -10000 -> extend; grant 10000 = elapsed -> clamped
                            advanceClockMs(10000);
                            beat.run(); // beat 3: leadMin -10000 -> extend; still clamped, no 2nd warn
                            advanceClockMs(10000);
                            beat.run(); // beat 4: leadMin -10000 -> extend
                            return "ok";
                        },
                        cycles, method, args, target,
                        "llm", "complete"
                );
            } finally {
                detachAppender(appender);
            }

            // Extends every tick — the clamped grants never satisfy the skip bound —
            // and the cadence never tightens below the held min(20000/2, 30000).
            verify(client, times(4)).extendReservation(eq("res-leadclamp"), any(Object.class));
            assertThat(scheduledDelays).containsExactly(0L, 10000L, 10000L, 10000L, 10000L);
            assertThat(leadClampWarnCount(appender)).isEqualTo(1);
        }

        @Test
        void shouldHoldCadenceOnZeroGrantImmediatePrime() throws Throwable {
            // The immediate first beat can land inside the same ms as the reserve: the
            // server echoes the unchanged expires_at_ms (grant 0). Grant-derived cadence
            // clamp(0/2, 500, 10000) = 500 would start hammering — instead the zero
            // grant marks the lead-clamp regime and the cadence holds at
            // min(requestedTtl/2, 30s) = 10000, never tightening.
            useClockedService();
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            stubHeartbeatLifecycle("res-zerogrant", initialExpiry);
            when(client.extendReservation(eq("res-zerogrant"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200,
                            Map.of("status", "ACTIVE", "expires_at_ms", initialExpiry)))   // grant 0
                    .thenReturn(CyclesResponse.success(200,
                            Map.of("status", "ACTIVE", "expires_at_ms", initialExpiry + 20000L)));

            var appender = attachWarnAppender();
            try {
                service.executeWithReservation(
                        () -> {
                            Runnable beat = capturedHeartbeat.get();
                            beat.run(); // beat 1 at t=0: grant 0 -> held cadence 10000, warn
                            advanceClockMs(10000);
                            beat.run(); // beat 2: leadMin -10000 -> extend; real grant 20000 -> normal
                            return "ok";
                        },
                        cycles, method, args, target,
                        "llm", "complete"
                );
            } finally {
                detachAppender(appender);
            }

            verify(client, times(2)).extendReservation(eq("res-zerogrant"), any(Object.class));
            // held 10000 after the zero grant (NOT the 500 floor), then normal cadence
            assertThat(scheduledDelays).containsExactly(0L, 10000L, 10000L);
            assertThat(leadClampWarnCount(appender)).isEqualTo(1);
        }

        @Test
        void shouldExitHoldAfterPostSkipGrantMatchesDoubledGapOnce() throws Throwable {
            // Sticky-misclassification regression (caught by the Rust port, fixed in
            // Python): after a leadMin skip the next grant arrives across a DOUBLED gap,
            // so a genuine grant-clamped server (+15000 per extend on a 60000 request,
            // cadence grant/2 = 7500) shows grant == elapsed exactly once. An
            // upper-bound-only test (grant <= 1.25*elapsed) would lock it into the
            // 30s hold, where a 15000ms lease banks less than the 30000ms elapsed per
            // cycle and decays to a lapse. The band's LOWER bound (grant >= 0.75*elapsed)
            // lets the hold last at most one cycle: at the held cadence the ratio falls
            // to 15000/30000 = 0.5, exits the band, and the cadence re-tightens.
            //   beat 1 (t=0):    extend; grant 15000, elapsed 0     -> normal, delay 7500 (sum 15000)
            //   beat 2 (7500):   leadMin  7500 < 22500 -> extend; ratio 2.0  -> normal   (sum 30000)
            //   beat 3 (15000):  leadMin 15000 < 22500 -> extend; ratio 2.0  -> normal   (sum 45000)
            //   beat 4 (22500):  leadMin 22500 >= 1.5*15000 -> skip
            //   beat 5 (30000):  leadMin 15000 -> extend; elapsed 15000, ratio 1.0 -> HOLD 30000 (sum 60000)
            //   beat 6 (60000):  leadMin 0 -> extend; elapsed 30000, ratio 0.5 -> re-tighten 7500 (sum 75000)
            //   beat 7 (67500):  leadMin 7500 -> extend; ratio 2.0 -> normal (sum 90000)
            useClockedService();
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(60000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            AtomicLong serverExpiry = new AtomicLong(initialExpiry);
            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponseWithExpiry("res-postskip", initialExpiry)));
            when(requestBuilderService.buildExtend(eq(60000L), isNull()))
                    .thenReturn(Map.of("idempotency_key", "ext-template", "extend_by_ms", 60000L));
            when(client.extendReservation(eq("res-postskip"), any(Object.class)))
                    .thenAnswer(inv -> CyclesResponse.success(200,
                            Map.of("status", "ACTIVE", "expires_at_ms", serverExpiry.addAndGet(15000L))));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            service.executeWithReservation(
                    () -> {
                        Runnable beat = capturedHeartbeat.get();
                        beat.run();             // beat 1: extend
                        advanceClockMs(7500);
                        beat.run();             // beat 2: extend
                        advanceClockMs(7500);
                        beat.run();             // beat 3: extend
                        advanceClockMs(7500);
                        beat.run();             // beat 4: skip
                        advanceClockMs(7500);
                        beat.run();             // beat 5: extend across the doubled gap -> one hold
                        advanceClockMs(30000);
                        beat.run();             // beat 6: extend at held cadence -> re-tightens
                        advanceClockMs(7500);
                        beat.run();             // beat 7: extend at normal cadence again
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            // 6 extends over 7 beats; the hold lasts exactly one cycle
            verify(client, times(6)).extendReservation(eq("res-postskip"), any(Object.class));
            assertThat(scheduledDelays).containsExactly(
                    0L, 7500L, 7500L, 7500L, 7500L, 30000L, 7500L, 7500L);
        }

        // ------------------------------------------------------------------
        // Server-authoritative remaining_ttl_ms scheduling (spec PR #148,
        // HEARTBEAT GUIDANCE in spec PR #148). Unless stated otherwise the tests
        // use a known 5000ms per-attempt timeout, rtt 0: attemptBudget = 5000,
        // safetyMargin = 1000, retryReserve = 2x5000+1000 = 11000.
        // ------------------------------------------------------------------

        /** Create response carrying the spec PR #148 remaining_ttl_ms field. */
        private Map<String, Object> allowResponseWithRemaining(String reservationId,
                                                               long expiresAtMs,
                                                               long remainingTtlMs) {
            Map<String, Object> body = allowResponseWithExpiry(reservationId, expiresAtMs);
            body.put("remaining_ttl_ms", remainingTtlMs);
            return body;
        }

        /** Common stubs for ttl=20000 with a remaining_ttl_ms-bearing create response. */
        private void stubHeartbeatLifecycleWithRemaining(String reservationId,
                                                         long initialExpiry,
                                                         long remainingTtlMs) {
            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200,
                            allowResponseWithRemaining(reservationId, initialExpiry, remainingTtlMs)));
            when(requestBuilderService.buildExtend(eq(20000L), isNull()))
                    .thenReturn(Map.of("idempotency_key", "ext-template", "extend_by_ms", 20000L));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));
        }

        @Test
        void shouldDeductLocalSetupTimeAfterCreateReceiptFromFirstDelay() throws Throwable {
            useClockedService(5000L);
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenAnswer(inv -> {
                advanceClockMs(2000L);
                return 20000L;
            });
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            stubHeartbeatLifecycleWithRemaining("res-field-setup", 1_000_000L, 60000L);
            captureHeartbeat();

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            // lead at receipt 60000, then 2000ms of local setup, less the
            // 11000ms reserve: schedule 47000ms from now, not 49000ms.
            assertThat(scheduledDelays).containsExactly(47000L);
        }

        @Test
        void shouldScheduleExactlyFromRemainingTtlAndBypassSkip() throws Throwable {
            // remaining_ttl_ms is NORMATIVE: leadFloor = max(0, 60000 - rtt 0) = 60000,
            // retryReserve = 2 x max(5000, 1000, 0) + max(1000, 0) = 11000, delay =
            // 49000 — from the CREATE response (no 0ms prime) and from every
            // schema-valid extend response. The leadMin skip check is bypassed even
            // when accumulated grants would trip it.
            useClockedService(5000L);
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            AtomicLong serverExpiry = new AtomicLong(initialExpiry);
            stubHeartbeatLifecycleWithRemaining("res-field", initialExpiry, 60000L);
            // Huge expiry grants (+1_000_000 per extend) so that by beat 3 the heuristic
            // bound (leadMin 2_000_000-147_000 >= 1.5*1_000_000) would demand a skip.
            when(client.extendReservation(eq("res-field"), any(Object.class)))
                    .thenAnswer(inv -> CyclesResponse.success(200, Map.of(
                            "status", "ACTIVE",
                            "expires_at_ms", serverExpiry.addAndGet(1_000_000L),
                            "remaining_ttl_ms", 60000L)));

            var appender = attachWarnAppender();
            try {
                service.executeWithReservation(
                        () -> {
                            Runnable beat = capturedHeartbeat.get();
                            advanceClockMs(49000);
                            beat.run(); // beat 1: extend, delay recomputed to 49000
                            advanceClockMs(49000);
                            beat.run(); // beat 2: extend
                            advanceClockMs(49000);
                            beat.run(); // beat 3: heuristic would SKIP — field mode extends
                            return "ok";
                        },
                        cycles, method, args, target,
                        "llm", "complete"
                );
            } finally {
                detachAppender(appender);
            }

            verify(client, times(3)).extendReservation(eq("res-field"), any(Object.class));
            assertThat(scheduledDelays).containsExactly(49000L, 49000L, 49000L, 49000L);
            assertThat(leadClampWarnCount(appender)).isZero();
        }

        @Test
        void shouldStopAfterTwoConsecutiveZeroDelaySchedules() throws Throwable {
            // ZERO-DELAY GUARD trace. A capped create (remaining_ttl_ms 1000 <=
            // retryReserve 11000) yields firstDelay 0 — that IS the one permitted
            // immediate fresh attempt. When its schema-valid success also computes
            // nextDelay 0 (lease still 1000), the client MUST stop and surface that
            // the lease is shorter than the retry-safety budget — never loop.
            useClockedService(5000L);
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            stubHeartbeatLifecycleWithRemaining("res-zerodelay", initialExpiry, 1000L);
            when(client.extendReservation(eq("res-zerodelay"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of(
                            "status", "ACTIVE",
                            "expires_at_ms", initialExpiry + 1000L,
                            "remaining_ttl_ms", 1000L)));

            var appender = attachWarnAppender();
            try {
                service.executeWithReservation(
                        () -> {
                            Runnable beat = capturedHeartbeat.get();
                            beat.run(); // immediate beat: success, nextDelay 0 again -> STOP
                            beat.run(); // stopped -> no further HTTP call
                            return "ok";
                        },
                        cycles, method, args, target,
                        "llm", "complete"
                );
            } finally {
                detachAppender(appender);
            }

            verify(client, times(1)).extendReservation(eq("res-zerodelay"), any(Object.class));
            // only the immediate first schedule ever happened — a stopped beat never reschedules
            assertThat(scheduledDelays).containsExactly(0L);
            verify(heartbeatFuture, atLeastOnce()).cancel(false);
            assertThat(warnCount(appender, "retry-safety budget")).isEqualTo(1);
        }

        @Test
        void shouldStopAfterOneImmediateAttemptWhenTimeoutUnknown() throws Throwable {
            // Unknown/unbounded per-attempt timeout -> attemptBudget = +infinity ->
            // every field-mode delay is 0. The guard permits the one immediate fresh
            // extension and then stops; the client MUST NOT downgrade to the fieldless
            // fallback merely because it cannot bound its own attempts.
            useClockedService(); // no timeout wired
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            stubHeartbeatLifecycleWithRemaining("res-notiming", initialExpiry, 60000L);
            when(client.extendReservation(eq("res-notiming"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of(
                            "status", "ACTIVE",
                            "expires_at_ms", initialExpiry + 20000L,
                            "remaining_ttl_ms", 60000L)));

            var appender = attachWarnAppender();
            try {
                service.executeWithReservation(
                        () -> {
                            Runnable beat = capturedHeartbeat.get();
                            beat.run(); // immediate attempt: success but delay 0 again -> STOP
                            advanceClockMs(10000);
                            beat.run(); // stopped -> no fallback takeover, no HTTP call
                            return "ok";
                        },
                        cycles, method, args, target,
                        "llm", "complete"
                );
            } finally {
                detachAppender(appender);
            }

            verify(client, times(1)).extendReservation(eq("res-notiming"), any(Object.class));
            assertThat(scheduledDelays).containsExactly(0L);
            verify(heartbeatFuture, atLeastOnce()).cancel(false);
            assertThat(warnCount(appender, "retry-safety budget")).isEqualTo(1);
        }

        @Test
        void shouldNotCollapseNorWarnUnderMaxLeadClampWithField() throws Throwable {
            // Max-lead clamp WITH the field: each extend echoes expiry = INITIAL+elapsed
            // (grant == elapsed, the undecidable heuristic case) but reports
            // remaining_ttl_ms = 5000 — server-authoritative. With a 1000ms timeout,
            // retryReserve = 2x1000+1000 = 3000, so the cadence stays at 5000-3000 =
            // 2000ms: no collapse to the floor, no burn, and NO lead-clamp WARN.
            useClockedService(1000L);
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            stubHeartbeatLifecycleWithRemaining("res-field-lead", initialExpiry, 5000L);
            when(client.extendReservation(eq("res-field-lead"), any(Object.class)))
                    .thenAnswer(inv -> CyclesResponse.success(200, Map.of(
                            "status", "ACTIVE",
                            "expires_at_ms", initialExpiry + nanoClock.get() / 1_000_000L,
                            "remaining_ttl_ms", 5000L)));

            var appender = attachWarnAppender();
            try {
                service.executeWithReservation(
                        () -> {
                            Runnable beat = capturedHeartbeat.get();
                            advanceClockMs(2000);
                            beat.run(); // beat 1: grant 2000 = elapsed -> field path, no warn
                            advanceClockMs(2000);
                            beat.run(); // beat 2: same
                            return "ok";
                        },
                        cycles, method, args, target,
                        "llm", "complete"
                );
            } finally {
                detachAppender(appender);
            }

            verify(client, times(2)).extendReservation(eq("res-field-lead"), any(Object.class));
            assertThat(scheduledDelays).containsExactly(2000L, 2000L, 2000L);
            assertThat(leadClampWarnCount(appender)).isZero();
        }

        @Test
        void shouldResumeHeuristicWhenFieldDisappears() throws Throwable {
            // The field is per-response: when a schema-valid extend response lacks it
            // (downgraded server, stripping proxy) the maintained grants/leadMin
            // bookkeeping takes over seamlessly — grant-derived cadence resumes on
            // that very success.
            useClockedService(5000L);
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            stubHeartbeatLifecycleWithRemaining("res-field-gone", initialExpiry, 60000L);
            when(client.extendReservation(eq("res-field-gone"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of(
                            "status", "ACTIVE", "expires_at_ms", initialExpiry + 20000L)))
                    .thenReturn(CyclesResponse.success(200, Map.of(
                            "status", "ACTIVE", "expires_at_ms", initialExpiry + 40000L)));

            service.executeWithReservation(
                    () -> {
                        Runnable beat = capturedHeartbeat.get();
                        advanceClockMs(49000);
                        beat.run(); // beat 1: NO field in response -> heuristic resumes;
                                    // grant 20000 >= 0.9*20000 -> normal cadence 10000
                        advanceClockMs(10000);
                        beat.run(); // beat 2: heuristic mode; leadMin 20000-59000 < 0 -> extend
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(client, times(2)).extendReservation(eq("res-field-gone"), any(Object.class));
            // field-scheduled first beat, then heuristic grant/2 cadence
            assertThat(scheduledDelays).containsExactly(49000L, 10000L, 10000L);
        }

        @Test
        void shouldTreatAmbiguous2xxAsTransientWithSameKeyRecovery() throws Throwable {
            // SUCCESS PREDICATE: only a schema-valid HTTP 200 ReservationExtendResponse
            // (status ACTIVE + expires_at_ms) counts. A 200 missing expires_at_ms is
            // AMBIGUOUS -> same-key recovery at min(30s, lead/4, window), never
            // "applied": at elapsed 20000, lead = 40000, window = 40000-5000-1000 =
            // 34000, delay = min(30000, 10000, 34000) = 10000.
            useClockedService(5000L);
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            stubHeartbeatLifecycleWithRemaining("res-ambiguous", initialExpiry, 60000L);
            when(client.extendReservation(eq("res-ambiguous"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "ACTIVE"))) // no expires_at_ms
                    .thenReturn(CyclesResponse.success(200, Map.of(
                            "status", "ACTIVE",
                            "expires_at_ms", initialExpiry + 20000L,
                            "remaining_ttl_ms", 60000L)));

            var appender = attachWarnAppender();
            AtomicReference<CyclesReservationContext> capturedCtx = new AtomicReference<>();
            try {
                service.executeWithReservation(
                        () -> {
                            capturedCtx.set(CyclesContextHolder.get());
                            Runnable beat = capturedHeartbeat.get();
                            advanceClockMs(20000);
                            beat.run(); // beat 1: ambiguous 2xx -> key kept, delay 10000
                            advanceClockMs(10000);
                            beat.run(); // beat 2: SAME key, schema-valid success -> 49000
                            return "ok";
                        },
                        cycles, method, args, target,
                        "llm", "complete"
                );
            } finally {
                detachAppender(appender);
            }

            org.mockito.ArgumentCaptor<Object> bodyCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(client, times(2)).extendReservation(eq("res-ambiguous"), bodyCaptor.capture());
            assertThat(idempotencyKeyOf(bodyCaptor.getAllValues().get(1)))
                    .isEqualTo(idempotencyKeyOf(bodyCaptor.getAllValues().get(0)));
            assertThat(scheduledDelays).containsExactly(49000L, 10000L, 49000L);
            assertThat(warnCount(appender, "ambiguous")).isEqualTo(1);
            // the ambiguous response never updated the context expiry
            assertThat(capturedCtx.get().getExpiresAtMs()).isEqualTo(initialExpiry + 20000L);
        }

        @Test
        void shouldRetryFieldModeTransientFailureWithinWindowWithSameKey() throws Throwable {
            // Transient failure in field mode: retry the SAME idempotency key after
            // min(30s, currentLeadEstimate/4, retryWindow), recomputed from the last
            // schema-valid response. Also covers the thrown (transport) variant after
            // re-entering field mode.
            useClockedService(5000L);
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            stubHeartbeatLifecycleWithRemaining("res-field-retry", initialExpiry, 60000L);
            when(client.extendReservation(eq("res-field-retry"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(503, "Unavailable", Map.of()))
                    .thenReturn(CyclesResponse.success(200, Map.of(
                            "status", "ACTIVE",
                            "expires_at_ms", initialExpiry + 20000L,
                            "remaining_ttl_ms", 60000L)))
                    .thenThrow(new RuntimeException("connection reset"));

            service.executeWithReservation(
                    () -> {
                        Runnable beat = capturedHeartbeat.get();
                        advanceClockMs(20000);
                        beat.run(); // beat 1: 503 -> lead 40000, window 34000 -> delay 10000
                        advanceClockMs(10000);
                        beat.run(); // beat 2: SAME key, succeeds; field -> delay 49000
                        advanceClockMs(20000);
                        beat.run(); // beat 3: throws -> lead 40000 -> delay 10000
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            org.mockito.ArgumentCaptor<Object> bodyCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(client, times(3)).extendReservation(eq("res-field-retry"), bodyCaptor.capture());
            String key1 = idempotencyKeyOf(bodyCaptor.getAllValues().get(0));
            String key2 = idempotencyKeyOf(bodyCaptor.getAllValues().get(1));
            String key3 = idempotencyKeyOf(bodyCaptor.getAllValues().get(2));
            assertThat(key2).isEqualTo(key1);        // transient failure replays the key
            assertThat(key3).isNotEqualTo(key1);     // fresh key after the success
            assertThat(scheduledDelays).containsExactly(49000L, 10000L, 49000L, 10000L);
        }

        @Test
        void shouldRepeatRecoveryUntilWindowNegativeThenStop() throws Throwable {
            // Repeated recovery: lead and window are recomputed from the SAME last
            // schema-valid response (the create) after EVERY failed attempt; retries
            // continue with the same key while window >= 0 and stop the moment
            // window < 0 (no complete attempt plus margin provably fits).
            //   create: remaining 20000 -> leadFloor 20000 -> firstDelay 9000
            //   fail 1 (t= 9000): lead 11000, window  5000 -> delay min(30000,2750,5000) = 2750
            //   fail 2 (t=11750): lead  8250, window  2250 -> delay min(30000,2062,2250) = 2062
            //   fail 3 (t=13812): lead  6188, window   188 -> delay 188
            //   fail 4 (t=15000, timer late): lead 5000, window -1000 -> STOP
            useClockedService(5000L);
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            stubHeartbeatLifecycleWithRemaining("res-recovery", initialExpiry, 20000L);
            when(client.extendReservation(eq("res-recovery"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(503, "Unavailable", Map.of()));

            var appender = attachWarnAppender();
            try {
                service.executeWithReservation(
                        () -> {
                            Runnable beat = capturedHeartbeat.get();
                            advanceClockMs(9000);
                            beat.run();  // fail 1
                            advanceClockMs(2750);
                            beat.run();  // fail 2
                            advanceClockMs(2062);
                            beat.run();  // fail 3
                            advanceClockMs(1188);
                            beat.run();  // fail 4: window < 0 -> stop
                            advanceClockMs(1000);
                            beat.run();  // stopped -> no HTTP call
                            return "ok";
                        },
                        cycles, method, args, target,
                        "llm", "complete"
                );
            } finally {
                detachAppender(appender);
            }

            org.mockito.ArgumentCaptor<Object> bodyCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(client, times(4)).extendReservation(eq("res-recovery"), bodyCaptor.capture());
            // the key never rotates across the whole recovery sequence
            String key1 = idempotencyKeyOf(bodyCaptor.getAllValues().get(0));
            assertThat(bodyCaptor.getAllValues().stream().map(this::idempotencyKeyOf))
                    .containsOnly(key1);
            assertThat(scheduledDelays).containsExactly(9000L, 2750L, 2062L, 188L);
            verify(heartbeatFuture, atLeastOnce()).cancel(false);
            assertThat(warnCount(appender, "no complete extend retry")).isEqualTo(1);
        }

        @Test
        void shouldAllowOneImmediateRetryAtZeroWindowThenStop() throws Throwable {
            // window == 0 permits exactly one immediate recovery retry; if it also
            // fails before an intervening success the beat stops — the progress guard
            // forbids a zero-time recovery loop even when coarse clocks report zero.
            //   create: remaining 12000 -> leadFloor 12000 -> firstDelay 1000
            //   fail 1 (t=6000): lead 6000, window 6000-5000-1000 = 0 -> immediate retry
            //   fail 2 (t=6000): elapsed and window unchanged -> STOP
            useClockedService(5000L);
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            stubHeartbeatLifecycleWithRemaining("res-zerowindow", initialExpiry, 12000L);
            when(client.extendReservation(eq("res-zerowindow"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(503, "Unavailable", Map.of()));

            var appender = attachWarnAppender();
            try {
                service.executeWithReservation(
                        () -> {
                            Runnable beat = capturedHeartbeat.get();
                            advanceClockMs(6000);
                            beat.run(); // fail 1: window 0 -> one immediate retry
                            beat.run(); // fail 2: no progress -> stop
                            beat.run(); // stopped -> no HTTP call
                            return "ok";
                        },
                        cycles, method, args, target,
                        "llm", "complete"
                );
            } finally {
                detachAppender(appender);
            }

            verify(client, times(2)).extendReservation(eq("res-zerowindow"), any(Object.class));
            assertThat(scheduledDelays).containsExactly(1000L, 0L);
            verify(heartbeatFuture, atLeastOnce()).cancel(false);
            assertThat(warnCount(appender, "no progress")).isEqualTo(1);
        }

        @Test
        void shouldHonor429RetryAfterWithinWindow() throws Throwable {
            // 429 in field mode: Retry-After (delta-seconds x 1000) is honored at
            // exactly that delay when it fits the retry window, with the SAME key.
            useClockedService(5000L);
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            stubHeartbeatLifecycleWithRemaining("res-429ok", initialExpiry, 60000L);
            when(client.extendReservation(eq("res-429ok"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(429, "Too many requests",
                            Map.of("error", "LIMIT_EXCEEDED", "message", "Too many", "request_id", "r1"),
                            2000))
                    .thenReturn(CyclesResponse.success(200, Map.of(
                            "status", "ACTIVE",
                            "expires_at_ms", initialExpiry + 20000L,
                            "remaining_ttl_ms", 60000L)));

            service.executeWithReservation(
                    () -> {
                        Runnable beat = capturedHeartbeat.get();
                        advanceClockMs(9000);
                        beat.run(); // 429: lead 51000, window 45000, Retry-After 2000 fits
                        advanceClockMs(2000);
                        beat.run(); // SAME key, succeeds -> 49000
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            org.mockito.ArgumentCaptor<Object> bodyCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
            verify(client, times(2)).extendReservation(eq("res-429ok"), bodyCaptor.capture());
            assertThat(idempotencyKeyOf(bodyCaptor.getAllValues().get(1)))
                    .isEqualTo(idempotencyKeyOf(bodyCaptor.getAllValues().get(0)));
            assertThat(scheduledDelays).containsExactly(49000L, 2000L, 49000L);
        }

        @Test
        void shouldStopWhen429RetryAfterExceedsWindow() throws Throwable {
            // A Retry-After beyond the safe retry window means the lease cannot be
            // renewed without violating throttling: never invent an earlier retry —
            // stop and surface. At elapsed 20000: lead 40000, window 34000 < 40000.
            useClockedService(5000L);
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            stubHeartbeatLifecycleWithRemaining("res-429far", 1_000_000L, 60000L);
            when(client.extendReservation(eq("res-429far"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(429, "Too many requests",
                            Map.of("error", "LIMIT_EXCEEDED", "message", "Too many", "request_id", "r1"),
                            40000));

            var appender = attachWarnAppender();
            try {
                service.executeWithReservation(
                        () -> {
                            Runnable beat = capturedHeartbeat.get();
                            advanceClockMs(20000);
                            beat.run(); // 429 Retry-After 40000 > window 34000 -> stop
                            beat.run(); // stopped -> no HTTP call
                            return "ok";
                        },
                        cycles, method, args, target,
                        "llm", "complete"
                );
            } finally {
                detachAppender(appender);
            }

            verify(client, times(1)).extendReservation(eq("res-429far"), any(Object.class));
            assertThat(scheduledDelays).containsExactly(49000L);
            verify(heartbeatFuture, atLeastOnce()).cancel(false);
            assertThat(warnCount(appender, "Retry-After")).isEqualTo(1);
        }

        @Test
        void shouldStopWhen429RetryAfterMissing() throws Throwable {
            // A 429 without a usable Retry-After gives no throttling-safe retry
            // instant: stop and surface rather than invent one.
            useClockedService(5000L);
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            stubHeartbeatLifecycleWithRemaining("res-429bare", 1_000_000L, 60000L);
            when(client.extendReservation(eq("res-429bare"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(429, "Too many requests", Map.of()));

            var appender = attachWarnAppender();
            try {
                service.executeWithReservation(
                        () -> {
                            Runnable beat = capturedHeartbeat.get();
                            advanceClockMs(9000);
                            beat.run(); // 429, no Retry-After -> stop
                            return "ok";
                        },
                        cycles, method, args, target,
                        "llm", "complete"
                );
            } finally {
                detachAppender(appender);
            }

            verify(client, times(1)).extendReservation(eq("res-429bare"), any(Object.class));
            verify(heartbeatFuture, atLeastOnce()).cancel(false);
            assertThat(warnCount(appender, "Retry-After")).isEqualTo(1);
        }

        @Test
        void shouldStopOnNonRetryable4xxInFieldMode() throws Throwable {
            // Any other 4xx is a request/authorization failure: stop and surface,
            // never rotate the idempotency key and retry an unchanged request.
            useClockedService(5000L);
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            stubHeartbeatLifecycleWithRemaining("res-400", 1_000_000L, 60000L);
            when(client.extendReservation(eq("res-400"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(400, "Bad request",
                            Map.of("error", "INVALID_REQUEST", "message", "Bad request", "request_id", "r1")));

            var appender = attachWarnAppender();
            try {
                service.executeWithReservation(
                        () -> {
                            Runnable beat = capturedHeartbeat.get();
                            advanceClockMs(9000);
                            beat.run(); // 400 -> stop, no retry
                            beat.run(); // stopped -> no HTTP call
                            return "ok";
                        },
                        cycles, method, args, target,
                        "llm", "complete"
                );
            } finally {
                detachAppender(appender);
            }

            verify(client, times(1)).extendReservation(eq("res-400"), any(Object.class));
            assertThat(scheduledDelays).containsExactly(49000L);
            verify(heartbeatFuture, atLeastOnce()).cancel(false);
            assertThat(warnCount(appender, "not retrying an unchanged request")).isEqualTo(1);
        }

        @Test
        void shouldStopOnUnexpected3xxInFieldMode() throws Throwable {
            useClockedService(5000L);
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            stubHeartbeatLifecycleWithRemaining("res-302", 1_000_000L, 60000L);
            when(client.extendReservation(eq("res-302"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(302, "Redirect", Map.of()));

            var appender = attachWarnAppender();
            try {
                service.executeWithReservation(
                        () -> {
                            Runnable beat = capturedHeartbeat.get();
                            advanceClockMs(9000);
                            beat.run();
                            beat.run();
                            return "ok";
                        },
                        cycles, method, args, target,
                        "llm", "complete"
                );
            } finally {
                detachAppender(appender);
            }

            verify(client, times(1)).extendReservation(eq("res-302"), any(Object.class));
            assertThat(scheduledDelays).containsExactly(49000L);
            verify(heartbeatFuture, atLeastOnce()).cancel(false);
            assertThat(warnCount(appender, "unexpected HTTP status 302")).isEqualTo(1);
        }

        @Test
        void shouldSubtractRttAndWidenReserveFromObservedRtt() throws Throwable {
            // rtt handling: the extend takes 2000ms on the clock, so leadFloor =
            // 60000 - 2000 = 58000 and maxObservedRtt = 2000 widens both budget terms:
            // attemptBudget = max(5000, 1000, 4000) = 5000, safetyMargin = max(1000,
            // 4000) = 4000, reserve = 14000 -> next delay 44000.
            useClockedService(5000L);
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            long initialExpiry = 1_000_000L;
            stubHeartbeatLifecycleWithRemaining("res-field-rtt", initialExpiry, 60000L);
            when(client.extendReservation(eq("res-field-rtt"), any(Object.class)))
                    .thenAnswer(inv -> {
                        advanceClockMs(2000); // simulated network round-trip
                        return CyclesResponse.success(200, Map.of(
                                "status", "ACTIVE",
                                "expires_at_ms", initialExpiry + 20000L,
                                "remaining_ttl_ms", 60000L));
                    });

            service.executeWithReservation(
                    () -> {
                        Runnable beat = capturedHeartbeat.get();
                        advanceClockMs(49000);
                        beat.run();
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            assertThat(scheduledDelays).containsExactly(49000L, 44000L);
        }

        @Test
        void shouldStopPermanentlyOnTenantClosed() throws Throwable {
            // TENANT_CLOSED is a terminal 409 no retry can resolve.
            useClockedService();
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            stubHeartbeatLifecycle("res-tclosed", 1_000_000L);
            when(client.extendReservation(eq("res-tclosed"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(409, "Tenant closed",
                            Map.of("error", "TENANT_CLOSED", "message", "Tenant closed", "request_id", "r1")));

            service.executeWithReservation(
                    () -> {
                        Runnable tick = capturedHeartbeat.get();
                        advanceClockMs(10000);
                        tick.run(); // tick 1: TENANT_CLOSED -> stop
                        advanceClockMs(10000);
                        tick.run(); // tick 2: stopped -> no call
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(client, times(1)).extendReservation(eq("res-tclosed"), any(Object.class));
        }

        @Test
        void shouldStopPermanentlyOnNotFound() throws Throwable {
            // NOT_FOUND: the reservation does not exist on this server — irreversible.
            useClockedService();
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            AtomicReference<Runnable> capturedHeartbeat = captureHeartbeat();
            stubHeartbeatLifecycle("res-notfound", 1_000_000L);
            when(client.extendReservation(eq("res-notfound"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(404, "Not found",
                            Map.of("error", "NOT_FOUND", "message", "Not found", "request_id", "r1")));

            service.executeWithReservation(
                    () -> {
                        Runnable tick = capturedHeartbeat.get();
                        advanceClockMs(10000);
                        tick.run(); // tick 1: NOT_FOUND -> stop
                        advanceClockMs(10000);
                        tick.run(); // tick 2: stopped -> no call
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(client, times(1)).extendReservation(eq("res-notfound"), any(Object.class));
        }
    }

    // ========================================================================
    // Strict lease response contract
    // ========================================================================

    @Nested
    @DisplayName("Strict lease response contract")
    class StrictLeaseResponseContract {

        @Test
        void shouldRecoverOneAmbiguousCreateWithTheSameBodyAndKey() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Map<String, Object> request = new HashMap<>();
            request.put("idempotency_key", "same-key");
            Map<String, Object> response = new HashMap<>();
            response.put("decision", "ALLOW");
            response.put("reservation_id", "res-create-recovery");
            response.put("affected_scopes", List.of("tenant:test-tenant"));

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(
                    any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(request);
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(202, response))
                    .thenReturn(CyclesResponse.success(200, response));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "commit-key"));
            when(client.commitReservation(eq("res-create-recovery"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            assertThat(service.executeWithReservation(
                    () -> "ok", cycles, method, new Object[]{100}, this,
                    "llm", "complete")).isEqualTo("ok");

            ArgumentCaptor<Object> bodies = ArgumentCaptor.forClass(Object.class);
            verify(client, times(2)).createReservation(bodies.capture());
            assertThat(bodies.getAllValues().get(0)).isSameAs(request);
            assertThat(bodies.getAllValues().get(1)).isSameAs(request);
        }

        @Test
        void shouldEnforceCompleteAttemptDeadlineForCustomClient() {
            service = new CyclesLifecycleService(
                    client, retryEngine, requestBuilderService, evaluator, heartbeatExecutor,
                    System::nanoTime, 10L);
            Cycles cycles = mockCycles(false);
            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(
                    any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "same-key"));
            when(client.createReservation(any(Object.class))).thenAnswer(invocation -> {
                Thread.sleep(1_000L);
                return CyclesResponse.success(200, allowResponse("too-late"));
            });

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "never", cycles, dummyMethod(), new Object[]{100}, this,
                    "llm", "complete"))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("same-key retry");
            // A timed-out daemon may be cancelled before an overloaded JVM
            // schedules it into the mocked client, so invocation count is
            // nondeterministically zero, one, or two. The exception text
            // proves the recovery loop consumed the second same-key attempt.
            verify(client, atMost(2)).createReservation(any(Object.class));
        }

        @Test
        void shouldValidateFullCreateAndExtendSchemas() {
            Map<String, Object> balance = new HashMap<>();
            balance.put("scope", "tenant:test-tenant");
            balance.put("scope_path", "tenant:test-tenant");
            balance.put("remaining", Map.of("unit", "TOKENS", "amount", -1L));
            balance.put("reserved", Map.of("unit", "TOKENS", "amount", 1L));

            Map<String, Object> create = new HashMap<>();
            create.put("decision", "ALLOW");
            create.put("reservation_id", "res-strict");
            create.put("affected_scopes", List.of("tenant:test-tenant"));
            create.put("remaining_ttl_ms", 0L);
            create.put("balances", List.of(balance));
            create.put("cycles_evidence", Map.of(
                    "evidence_id", "a".repeat(64),
                    "cycles_evidence_url", "https://cycles.example/v1/evidence/id"));
            assertThat(CyclesLifecycleService.isSchemaValidCreateResponse(
                    CyclesResponse.success(200, create))).isTrue();
            CyclesEvidenceRef evidence = ReservationResult.fromMap(create).getCyclesEvidence();
            assertThat(evidence.getEvidenceId()).isEqualTo("a".repeat(64));
            assertThat(evidence.getCyclesEvidenceUrl())
                    .isEqualTo("https://cycles.example/v1/evidence/id");

            Map<String, Object> extend = new HashMap<>();
            extend.put("status", "ACTIVE");
            extend.put("expires_at_ms", 1L);
            extend.put("remaining_ttl_ms", 0L);
            extend.put("balances", List.of(balance));
            assertThat(CyclesLifecycleService.isSchemaValidExtendResponse(
                    CyclesResponse.success(200, extend))).isTrue();

            Map<String, Object> extra = new HashMap<>(extend);
            extra.put("unexpected", true);
            assertThat(CyclesLifecycleService.isSchemaValidExtendResponse(
                    CyclesResponse.success(200, extra))).isFalse();

            Map<String, Object> negative = new HashMap<>(extend);
            negative.put("remaining_ttl_ms", -1L);
            assertThat(CyclesLifecycleService.isSchemaValidExtendResponse(
                    CyclesResponse.success(200, negative))).isFalse();

            Map<String, Object> malformedBalance = new HashMap<>(extend);
            malformedBalance.put("balances", List.of(Map.of("scope", "missing-required-fields")));
            assertThat(CyclesLifecycleService.isSchemaValidExtendResponse(
                    CyclesResponse.success(200, malformedBalance))).isFalse();

            assertThat(CyclesLifecycleService.isSchemaValidExtendResponse(
                    CyclesResponse.success(202, extend))).isFalse();

            Map<String, Object> nullCaps = new HashMap<>(create);
            nullCaps.put("caps", null);
            assertThat(CyclesLifecycleService.isSchemaValidCreateResponse(
                    CyclesResponse.success(200, nullCaps))).isFalse();

            Map<String, Object> negativeCreateExpiry = new HashMap<>(create);
            negativeCreateExpiry.put("expires_at_ms", -1L);
            assertThat(CyclesLifecycleService.isSchemaValidCreateResponse(
                    CyclesResponse.success(200, negativeCreateExpiry))).isFalse();

            Map<String, Object> overflow = new HashMap<>(extend);
            overflow.put("remaining_ttl_ms", BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
            assertThat(CyclesLifecycleService.isSchemaValidExtendResponse(
                    CyclesResponse.success(200, overflow))).isFalse();

            Map<String, Object> nestedNull = new HashMap<>(balance);
            nestedNull.put("debt", null);
            Map<String, Object> nullBalance = new HashMap<>(extend);
            nullBalance.put("balances", List.of(nestedNull));
            assertThat(CyclesLifecycleService.isSchemaValidExtendResponse(
                    CyclesResponse.success(200, nullBalance))).isFalse();
        }
    }

    // ========================================================================
    // Null decision from server
    // ========================================================================

    @Nested
    @DisplayName("Null decision")
    class NullDecision {

        @Test
        void shouldThrowOnUnrecognizedDecision() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));

            // Server returns unrecognized decision string like "THROTTLE"
            Map<String, Object> response = new HashMap<>();
            response.put("decision", "THROTTLE");
            response.put("reservation_id", "res-throttle");
            response.put("affected_scopes", List.of("tenant:test-tenant"));

            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, response));

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "nope",
                    cycles, method, args, target,
                    "llm", "complete"
            ))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("schema-valid HTTP 200");
        }
    }

    // ========================================================================
    // Null ReservationResult (unparseable response)
    // ========================================================================

    @Nested
    @DisplayName("Null ReservationResult")
    class NullReservationResult {

        @Test
        void shouldThrowWhenReservationResultCannotBeParsed() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));

            // Return a 2xx response with null body -> ReservationResult.fromMap(null) returns null
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, null));

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "nope",
                    cycles, method, args, target,
                    "llm", "complete"
            ))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("schema-valid HTTP 200");
        }
    }

    // ========================================================================
    // Release failure paths
    // ========================================================================

    @Nested
    @DisplayName("Release failure handling")
    class ReleaseFailure {

        @Test
        void shouldHandleReleaseHttpError() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-relfail")));
            when(requestBuilderService.buildRelease(anyString()))
                    .thenReturn(Map.of("idempotency_key", "rel-1"));
            // Release returns a non-2xx error
            when(client.releaseReservation(eq("res-relfail"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(500, "Server error", Map.of()));

            RuntimeException actionError = new RuntimeException("Action failed");
            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> { throw actionError; },
                    cycles, method, args, target,
                    "llm", "complete"
            ))
                    .isSameAs(actionError);

            // Release was attempted even though it failed
            verify(client).releaseReservation(eq("res-relfail"), any(Object.class));
        }

        @Test
        void shouldHandleReleaseException() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-relexc")));
            when(requestBuilderService.buildRelease(anyString()))
                    .thenReturn(Map.of("idempotency_key", "rel-1"));
            // Release throws an exception
            when(client.releaseReservation(eq("res-relexc"), any(Object.class)))
                    .thenThrow(new RuntimeException("Network down"));

            RuntimeException actionError = new RuntimeException("Action failed");
            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> { throw actionError; },
                    cycles, method, args, target,
                    "llm", "complete"
            ))
                    .isSameAs(actionError);

            // Release was attempted even though it threw
            verify(client).releaseReservation(eq("res-relexc"), any(Object.class));
        }
    }

    // ========================================================================
    // Commit RESERVATION_EXPIRED
    // ========================================================================

    @Nested
    @DisplayName("Commit with RESERVATION_EXPIRED")
    class CommitReservationExpired {

        @Test
        void shouldSkipReleaseAndRecoverViaEventOnReservationExpired() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-expired")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(eq("res-expired"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(410, "Expired",
                            Map.of("error", "RESERVATION_EXPIRED", "message", "Expired", "request_id", "r1")));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            // Should NOT release or commit-retry; spend is recovered via /v1/events
            verify(client, never()).releaseReservation(anyString(), any(Object.class));
            verify(retryEngine, never()).schedule(anyString(), any(), any(), any());
            verify(retryEngine).scheduleEvent(eq("res-expired"), anyMap());
        }

        @Test
        void shouldRecoverViaEventOnBodyless410() throws Throwable {
            // A bare 410 with no error envelope (proxy-stripped body) still means
            // expired: recover the spend via /v1/events, never release.
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-410-bare")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(eq("res-410-bare"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(410, "Gone", Map.of()));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(client, never()).releaseReservation(anyString(), any(Object.class));
            verify(retryEngine, never()).schedule(anyString(), any(), any(), any());
            verify(retryEngine).scheduleEvent(eq("res-410-bare"), anyMap());
        }
    }

    // ========================================================================
    // Commit with unrecognized response
    // ========================================================================

    @Nested
    @DisplayName("Commit with unrecognized response")
    class CommitUnrecognizedResponse {

        @Test
        void shouldHandleNon2xxNon4xxNon5xxResponse() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-weird")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            // Return a 3xx-ish status (unusual) - not 2xx, not transport, not 4xx, not 5xx
            when(client.commitReservation(eq("res-weird"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(301, "Redirect", Map.of()));

            // Should not throw, just log warning
            Object result = service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );
            assertThat(result).isEqualTo("ok");

            // Ambiguous status retains the spend and retries with the same key.
            verify(client, never()).releaseReservation(anyString(), any(Object.class));
            verify(retryEngine).schedule(eq("res-weird"), any(), any(), isNull());
            verify(retryEngine, never()).scheduleEvent(anyString(), any());
        }
    }

    // ========================================================================
    // buildProtocolException null errorResponse fallback
    // ========================================================================

    @Nested
    @DisplayName("buildProtocolException fallback paths")
    class BuildProtocolExceptionFallback {

        @Test
        void shouldFallbackWhenErrorResponseIsNull() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));

            // Error response body without structured error fields (no "error"/"message"/"request_id")
            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("some_field", "some_value");

            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(503, "Service Unavailable", errorBody));

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "nope",
                    cycles, method, args, target,
                    "llm", "complete"
            ))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("Service Unavailable");
        }
    }
}
