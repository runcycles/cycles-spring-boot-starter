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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CyclesLifecycleService coverage")
class CyclesLifecycleServiceCoverageTest {

    private CyclesClient client;
    private CommitRetryEngine retryEngine;
    private CyclesExpressionEvaluator evaluator;
    private CyclesRequestBuilderService requestBuilderService;
    private ScheduledExecutorService heartbeatExecutor;
    private CyclesLifecycleService service;

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

    // ========================================================================
    // Null reservation result parsing
    // ========================================================================

    @Nested
    @DisplayName("Null reservation result")
    class NullReservationResult {

        @Test
        void shouldThrowWhenResponseCannotBeParsed() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            // Return a response with null body which will make ReservationResult.fromMap return null
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, null));

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "ok", cycles, method, new Object[]{100}, this, "llm", "complete"))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("parse");
        }
    }

    // ========================================================================
    // Unrecognized decision value
    // ========================================================================

    @Nested
    @DisplayName("Unrecognized decision")
    class UnrecognizedDecision {

        @Test
        void shouldThrowOnUnknownDecision() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));

            Map<String, Object> body = new HashMap<>();
            body.put("decision", "UNKNOWN_DECISION");
            body.put("reservation_id", "r1");
            body.put("affected_scopes", List.of());
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, body));

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "ok", cycles, method, new Object[]{100}, this, "llm", "complete"))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("Unrecognized decision");
        }
    }

    // ========================================================================
    // Missing reservation ID
    // ========================================================================

    @Nested
    @DisplayName("Missing reservation ID")
    class MissingReservationId {

        @Test
        void shouldThrowWhenReservationIdMissing() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));

            Map<String, Object> body = new HashMap<>();
            body.put("decision", "ALLOW");
            body.put("affected_scopes", List.of());
            // No reservation_id
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, body));

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "ok", cycles, method, new Object[]{100}, this, "llm", "complete"))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("reservation identifier");
        }
    }

    // ========================================================================
    // Estimate expression resolution
    // ========================================================================

    @Nested
    @DisplayName("Estimate expression resolution")
    class EstimateExpression {

        @Test
        void shouldThrowWhenBothValueAndEstimateSet() throws Throwable {
            Cycles cycles = mockCycles(false);
            when(cycles.value()).thenReturn("100");
            when(cycles.estimate()).thenReturn("200");

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "ok", cycles, dummyMethod(), new Object[]{100}, this, "llm", "complete"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not both");
        }

        @Test
        void shouldThrowWhenNeitherValueNorEstimateSet() throws Throwable {
            Cycles cycles = mockCycles(false);
            when(cycles.value()).thenReturn("");
            when(cycles.estimate()).thenReturn("");

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "ok", cycles, dummyMethod(), new Object[]{100}, this, "llm", "complete"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("required");
        }

        @Test
        void shouldUseEstimateWhenValueBlank() throws Throwable {
            Cycles cycles = mockCycles(false);
            when(cycles.value()).thenReturn("");
            when(cycles.estimate()).thenReturn("500");

            when(evaluator.evaluate(eq("500"), any(), any(), any(), any())).thenReturn(500L);
            when(requestBuilderService.buildReservation(any(), eq(500L), anyString(), anyString(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("r1")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "COMMITTED",
                            "charged", Map.of("unit", "TOKENS", "amount", 500))));

            Object result = service.executeWithReservation(
                    () -> "ok", cycles, dummyMethod(), new Object[]{100}, this, "llm", "complete");
            assertThat(result).isEqualTo("ok");
        }
    }

    // ========================================================================
    // Actual amount resolution - missing actual expression
    // ========================================================================

    @Nested
    @DisplayName("Actual amount resolution")
    class ActualAmountResolution {

        @Test
        void shouldThrowWhenActualRequiredButMissing() throws Throwable {
            Cycles cycles = mockCycles(false);
            when(cycles.actual()).thenReturn("");
            when(cycles.useEstimateIfActualNotProvided()).thenReturn(false);

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("r1")));
            when(requestBuilderService.buildRelease(anyString()))
                    .thenReturn(Map.of("idempotency_key", "rel-1"));
            when(client.releaseReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "RELEASED")));

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "ok", cycles, dummyMethod(), new Object[]{100}, this, "llm", "complete"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Actual expression required");
        }
    }

    // ========================================================================
    // Commit with RESERVATION_EXPIRED error
    // ========================================================================

    @Nested
    @DisplayName("Commit with RESERVATION_EXPIRED")
    class CommitReservationExpired {

        @Test
        void shouldSkipReleaseOnExpired() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-exp")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(eq("res-exp"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(409, "Expired",
                            Map.of("error", "RESERVATION_EXPIRED", "message", "Expired", "request_id", "r1")));

            service.executeWithReservation(
                    () -> "ok", cycles, method, new Object[]{100}, this, "llm", "complete");

            verify(client, never()).releaseReservation(anyString(), any(Object.class));
            verify(retryEngine, never()).schedule(anyString(), any());
        }
    }

    // ========================================================================
    // Commit with unrecognized response code
    // ========================================================================

    @Nested
    @DisplayName("Commit with unrecognized response")
    class CommitUnrecognizedResponse {

        @Test
        void shouldLogWarningOnUnrecognizedResponse() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-unk")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            // 301 redirect - not 2xx, not transport, not 5xx, not 4xx
            when(client.commitReservation(eq("res-unk"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(301, "Redirect", Map.of()));

            // Should not throw, just log warning
            service.executeWithReservation(
                    () -> "ok", cycles, method, new Object[]{100}, this, "llm", "complete");

            verify(client, never()).releaseReservation(anyString(), any(Object.class));
            verify(retryEngine, never()).schedule(anyString(), any());
        }
    }

    // ========================================================================
    // Reservation error without ErrorResponse (fallback parsing)
    // ========================================================================

    @Nested
    @DisplayName("Reservation error without ErrorResponse")
    class ReservationErrorFallback {

        @Test
        void shouldFallbackToBodyAttribute() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));

            // Error body without proper error response structure
            Map<String, Object> body = new HashMap<>();
            body.put("message", "Something went wrong");
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(500, "Server error", body));

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "ok", cycles, method, new Object[]{100}, this, "llm", "complete"))
                    .isInstanceOf(CyclesProtocolException.class);
        }
    }

    // ========================================================================
    // Heartbeat with null expiresAtMs
    // ========================================================================

    @Nested
    @DisplayName("Heartbeat scheduling")
    class HeartbeatScheduling {

        @Test
        void shouldNotScheduleHeartbeatWhenNoExpiry() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));

            Map<String, Object> body = new HashMap<>();
            body.put("decision", "ALLOW");
            body.put("reservation_id", "res-no-exp");
            body.put("affected_scopes", List.of("tenant:t1"));
            // No expires_at_ms
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, body));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "COMMITTED",
                            "charged", Map.of("unit", "TOKENS", "amount", 1000))));

            service.executeWithReservation(
                    () -> "ok", cycles, method, new Object[]{100}, this, "llm", "complete");

            verify(heartbeatExecutor, never()).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
        }

        @Test
        void shouldNotScheduleHeartbeatWhenTtlZero() throws Throwable {
            Cycles cycles = mockCycles(false);
            when(cycles.ttlMs()).thenReturn(0L);
            Method method = dummyMethod();

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-no-ttl")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "COMMITTED",
                            "charged", Map.of("unit", "TOKENS", "amount", 1000))));

            service.executeWithReservation(
                    () -> "ok", cycles, method, new Object[]{100}, this, "llm", "complete");

            verify(heartbeatExecutor, never()).scheduleAtFixedRate(any(), anyLong(), anyLong(), any());
        }
    }

    // ========================================================================
    // Release failure handling
    // ========================================================================

    @Nested
    @DisplayName("Release failure")
    class ReleaseFailure {

        @Test
        void shouldHandleReleaseFailureGracefully() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-rel-fail")));
            when(requestBuilderService.buildRelease(anyString()))
                    .thenReturn(Map.of("idempotency_key", "rel-1"));
            when(client.releaseReservation(eq("res-rel-fail"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(500, "Release failed", Map.of()));

            RuntimeException actionError = new RuntimeException("Action failed");
            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> { throw actionError; },
                    cycles, method, new Object[]{100}, this, "llm", "complete"))
                    .isSameAs(actionError);
        }

        @Test
        void shouldHandleReleaseException() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-rel-ex")));
            when(requestBuilderService.buildRelease(anyString()))
                    .thenReturn(Map.of("idempotency_key", "rel-1"));
            when(client.releaseReservation(eq("res-rel-ex"), any(Object.class)))
                    .thenThrow(new RuntimeException("Network error"));

            RuntimeException actionError = new RuntimeException("Action failed");
            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> { throw actionError; },
                    cycles, method, new Object[]{100}, this, "llm", "complete"))
                    .isSameAs(actionError);
        }
    }

    // ========================================================================
    // Dry-run with reason_code null
    // ========================================================================

    @Nested
    @DisplayName("Dry-run deny without reason code")
    class DryRunDenyNoReasonCode {

        @Test
        void shouldDefaultToBudgetExceeded() throws Throwable {
            Cycles cycles = mockCycles(true);
            Method method = dummyMethod();

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));

            Map<String, Object> body = new HashMap<>();
            body.put("decision", "DENY");
            // No reason_code
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, body));

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "ok", cycles, method, new Object[]{100}, this, "llm", "complete"))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("BUDGET_EXCEEDED");
        }
    }

    // ========================================================================
    // DENY without reason code
    // ========================================================================

    @Nested
    @DisplayName("DENY without reason code")
    class DenyNoReasonCode {

        @Test
        void shouldDefaultToBudgetExceeded() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));

            Map<String, Object> body = new HashMap<>();
            body.put("decision", "DENY");
            // No reason_code, no retry_after_ms
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, body));

            assertThatThrownBy(() -> service.executeWithReservation(
                    () -> "ok", cycles, method, new Object[]{100}, this, "llm", "complete"))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("BUDGET_EXCEEDED");
        }
    }

    // ========================================================================
    // Context metrics populated by user
    // ========================================================================

    @Nested
    @DisplayName("Context metrics pre-populated by user")
    class ContextMetrics {

        @Test
        void shouldUseUserProvidedMetrics() throws Throwable {
            Cycles cycles = mockCycles(false);
            Method method = dummyMethod();

            when(evaluator.evaluate(anyString(), any(), any(), any(), any())).thenReturn(1000L);
            when(requestBuilderService.buildReservation(any(), anyLong(), anyString(), anyString(), any(), any(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "idem-1"));
            when(client.createReservation(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, allowResponse("res-met")));
            when(requestBuilderService.buildCommit(any(), anyLong(), any(), any()))
                    .thenReturn(Map.of("idempotency_key", "com-1"));
            when(client.commitReservation(anyString(), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "COMMITTED",
                            "charged", Map.of("unit", "TOKENS", "amount", 1000))));

            service.executeWithReservation(
                    () -> {
                        CyclesReservationContext ctx = CyclesContextHolder.get();
                        CyclesMetrics m = new CyclesMetrics();
                        m.setTokensInput(100);
                        m.setTokensOutput(50);
                        m.setLatencyMs(200); // Pre-set latency, should not be overridden
                        ctx.setMetrics(m);
                        ctx.setCommitMetadata(Map.of("trace", "abc"));
                        return "ok";
                    },
                    cycles, method, new Object[]{100}, this, "llm", "complete");

            // Verify the commit was called (metrics were included)
            verify(requestBuilderService).buildCommit(eq(cycles), eq(1000L), any(CyclesMetrics.class), eq(Map.of("trace", "abc")));
        }
    }
}
