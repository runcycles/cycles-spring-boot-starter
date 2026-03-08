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

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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
        // Return a mock future from scheduleAtFixedRate so cancelHeartbeat works
        when(heartbeatExecutor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(mock(ScheduledFuture.class));
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
        when(cycles.useEstimateIfActualNotProvided()).thenReturn(true);
        when(cycles.unit()).thenReturn("TOKENS");
        when(cycles.ttlMs()).thenReturn(60000L);
        when(cycles.gracePeriodMs()).thenReturn(5000L);
        when(cycles.overagePolicy()).thenReturn("REJECT");
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
            when(requestBuilderService.buildCommit(eq(cycles), eq(1000L), any(), isNull()))
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
            verify(client).commitReservation(eq("res-1"), any(Object.class));
            verify(client, never()).releaseReservation(anyString(), any(Object.class));
        }

        @Test
        void shouldSetAndClearContext() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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

            verify(retryEngine).schedule(eq("res-retry"), eq(commitBody));
        }

        @Test
        void shouldScheduleRetryOn5xx() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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

            verify(retryEngine).schedule(eq("res-5xx"), eq(commitBody));
        }

        @Test
        void shouldSkipReleaseOnReservationFinalized() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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
            verify(retryEngine, never()).schedule(anyString(), any());
        }

        @Test
        void shouldReleaseOnNonRetryable4xx() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-4xx")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(eq("res-4xx"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(400, "Bad request",
                            Map.of("error", "INVALID_REQUEST", "message", "Bad request", "request_id", "r1")));
            when(requestBuilderService.buildRelease(anyString()))
                    .thenReturn(Map.of("idempotency_key", "rel-1"));
            when(client.releaseReservation(eq("res-4xx"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, releaseSuccessResponse()));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            verify(client).releaseReservation(eq("res-4xx"), any(Object.class));
        }

        @Test
        void shouldScheduleRetryOnCommitException() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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

            verify(retryEngine).schedule(eq("res-exc"), eq(commitBody));
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
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-noactual")));

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            ))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Actual expression required");

            // Should release reservation since action threw
            verify(client).releaseReservation(eq("res-noactual"), any(Object.class));
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
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));

            Map<String, Object> responseWithoutId = new HashMap<>();
            responseWithoutId.put("decision", "ALLOW");
            responseWithoutId.put("expires_at_ms", System.currentTimeMillis() + 60000);
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
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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

        @Test
        void shouldScheduleHeartbeatWithCorrectInterval() throws Throwable {
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(20000L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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

            // interval = ttlMs / 2 = 10000ms
            verify(heartbeatExecutor).scheduleAtFixedRate(
                    any(Runnable.class), eq(10000L), eq(10000L), eq(TimeUnit.MILLISECONDS));
        }

        @Test
        void shouldUseMinimumIntervalOf1000ms() throws Throwable {
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(1000L); // ttl/2 = 500, clamped to 1000
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-hb-min")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            service.executeWithReservation(
                    () -> "ok",
                    cycles, method, args, target,
                    "llm", "complete"
            );

            // Math.max(1000/2, 1000) = 1000ms
            verify(heartbeatExecutor).scheduleAtFixedRate(
                    any(Runnable.class), eq(1000L), eq(1000L), eq(TimeUnit.MILLISECONDS));
        }

        @Test
        void shouldNotScheduleHeartbeatWhenExpiresAtMsIsNull() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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

            verify(heartbeatExecutor, never()).scheduleAtFixedRate(
                    any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        }

        @Test
        void shouldNotScheduleHeartbeatWhenTtlMsIsZero() throws Throwable {
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(0L);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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

            verify(heartbeatExecutor, never()).scheduleAtFixedRate(
                    any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
        }

        @Test
        @SuppressWarnings("unchecked")
        void shouldCancelHeartbeatAfterCommit() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();
            Object[] args = {100};
            Object target = CyclesLifecycleServiceTest.this;

            ScheduledFuture<?> mockFuture = mock(ScheduledFuture.class);
            when(heartbeatExecutor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(inv -> mockFuture);

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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
            when(heartbeatExecutor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(inv -> mockFuture);

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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

            // Capture the Runnable passed to scheduleAtFixedRate
            AtomicReference<Runnable> capturedHeartbeat = new AtomicReference<>();
            when(heartbeatExecutor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(invocation -> {
                        capturedHeartbeat.set(invocation.getArgument(0));
                        return mock(ScheduledFuture.class);
                    });

            long newExpiresAtMs = System.currentTimeMillis() + 80000;
            Map<String, Object> extendBody = Map.of("idempotency_key", "ext-1", "extend_by_ms", 20000);
            Map<String, Object> extendResponseBody = Map.of("status", "ACTIVE", "expires_at_ms", newExpiresAtMs);

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
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

            // Verify extend was called
            verify(client).extendReservation(eq("res-extend"), eq(extendBody));
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
            when(heartbeatExecutor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(invocation -> {
                        capturedHeartbeat.set(invocation.getArgument(0));
                        return mock(ScheduledFuture.class);
                    });

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-ext-fail")));
            when(requestBuilderService.buildExtend(anyLong(), isNull()))
                    .thenReturn(Map.of("idempotency_key", "ext-1", "extend_by_ms", 20000));
            when(client.extendReservation(eq("res-ext-fail"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(410, "Expired",
                            Map.of("error", "RESERVATION_EXPIRED", "message", "Expired", "request_id", "r1")));
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
            when(heartbeatExecutor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class)))
                    .thenAnswer(invocation -> {
                        capturedHeartbeat.set(invocation.getArgument(0));
                        return mock(ScheduledFuture.class);
                    });

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-ext-exc")));
            when(requestBuilderService.buildExtend(anyLong(), isNull()))
                    .thenThrow(new RuntimeException("Network down"));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, commitSuccessResponse()));

            Object result = service.executeWithReservation(
                    () -> {
                        // Heartbeat fires but throws — should not crash the action
                        capturedHeartbeat.get().run();
                        return "ok";
                    },
                    cycles, method, args, target,
                    "llm", "complete"
            );

            assertThat(result).isEqualTo("ok");
        }
    }
}
