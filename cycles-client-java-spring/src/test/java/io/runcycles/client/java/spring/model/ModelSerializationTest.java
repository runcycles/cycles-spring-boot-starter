package io.runcycles.client.java.spring.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Model Serialization (toMap/fromMap)")
class ModelSerializationTest {

    // ========================================================================
    // Request DTOs — toMap()
    // ========================================================================

    @Nested
    @DisplayName("ReservationCreateRequest")
    class ReservationCreateRequestTest {

        @Test
        void shouldSerializeAllFields() {
            var req = ReservationCreateRequest.builder()
                    .idempotencyKey("idem-1")
                    .subject(Subject.builder().tenant("t1").workspace("ws1").build())
                    .action(new Action("llm.completion", "gpt-4", List.of("prod")))
                    .estimate(new Amount(Unit.TOKENS, 1000))
                    .ttlMs(30000L)
                    .gracePeriodMs(10000L)
                    .overagePolicy(CommitOveragePolicy.ALLOW_IF_AVAILABLE)
                    .dryRun(true)
                    .metadata(Map.of("key", "value"))
                    .build();

            Map<String, Object> map = req.toMap();

            assertThat(map.get("idempotency_key")).isEqualTo("idem-1");
            assertThat(map.get("ttl_ms")).isEqualTo(30000L);
            assertThat(map.get("grace_period_ms")).isEqualTo(10000L);
            assertThat(map.get("overage_policy")).isEqualTo("ALLOW_IF_AVAILABLE");
            assertThat(map.get("dry_run")).isEqualTo(true);
            assertThat(map.get("metadata")).isEqualTo(Map.of("key", "value"));

            // Nested subject
            @SuppressWarnings("unchecked")
            Map<String, Object> subject = (Map<String, Object>) map.get("subject");
            assertThat(subject.get("tenant")).isEqualTo("t1");
            assertThat(subject.get("workspace")).isEqualTo("ws1");

            // Nested action
            @SuppressWarnings("unchecked")
            Map<String, Object> action = (Map<String, Object>) map.get("action");
            assertThat(action.get("kind")).isEqualTo("llm.completion");
            assertThat(action.get("name")).isEqualTo("gpt-4");
            assertThat(action.get("tags")).isEqualTo(List.of("prod"));

            // Nested estimate
            @SuppressWarnings("unchecked")
            Map<String, Object> estimate = (Map<String, Object>) map.get("estimate");
            assertThat(estimate.get("unit")).isEqualTo("TOKENS");
            assertThat(estimate.get("amount")).isEqualTo(1000L);
        }

        @Test
        void shouldOmitNullFields() {
            var req = ReservationCreateRequest.builder()
                    .idempotencyKey("idem-1")
                    .subject(Subject.builder().tenant("t1").build())
                    .action(new Action("test", "test", null))
                    .estimate(new Amount(Unit.TOKENS, 100))
                    .ttlMs(null)
                    .gracePeriodMs(null)
                    .overagePolicy(null)
                    .dryRun(null)
                    .build();

            Map<String, Object> map = req.toMap();

            assertThat(map).doesNotContainKey("ttl_ms");
            assertThat(map).doesNotContainKey("grace_period_ms");
            assertThat(map).doesNotContainKey("overage_policy");
            assertThat(map).doesNotContainKey("dry_run");
            assertThat(map).doesNotContainKey("metadata");
        }
    }

    @Nested
    @DisplayName("CommitRequest")
    class CommitRequestTest {

        @Test
        void shouldSerializeAllFields() {
            var metrics = new CyclesMetrics();
            metrics.setTokensInput(100);
            metrics.setTokensOutput(50);
            metrics.setLatencyMs(200);
            metrics.setModelVersion("gpt-4-0613");

            var req = CommitRequest.builder()
                    .idempotencyKey("commit-1")
                    .actual(new Amount(Unit.TOKENS, 800))
                    .metrics(metrics)
                    .metadata(Map.of("session", "abc"))
                    .build();

            Map<String, Object> map = req.toMap();

            assertThat(map.get("idempotency_key")).isEqualTo("commit-1");

            @SuppressWarnings("unchecked")
            Map<String, Object> actual = (Map<String, Object>) map.get("actual");
            assertThat(actual.get("unit")).isEqualTo("TOKENS");
            assertThat(actual.get("amount")).isEqualTo(800L);

            @SuppressWarnings("unchecked")
            Map<String, Object> metricsMap = (Map<String, Object>) map.get("metrics");
            assertThat(metricsMap.get("tokens_input")).isEqualTo(100);
            assertThat(metricsMap.get("tokens_output")).isEqualTo(50);
            assertThat(metricsMap.get("latency_ms")).isEqualTo(200);
            assertThat(metricsMap.get("model_version")).isEqualTo("gpt-4-0613");
        }
    }

    @Nested
    @DisplayName("ReleaseRequest")
    class ReleaseRequestTest {

        @Test
        void shouldSerializeWithReason() {
            var req = ReleaseRequest.builder()
                    .idempotencyKey("rel-1")
                    .reason("user_cancelled")
                    .build();

            Map<String, Object> map = req.toMap();

            assertThat(map.get("idempotency_key")).isEqualTo("rel-1");
            assertThat(map.get("reason")).isEqualTo("user_cancelled");
        }

        @Test
        void shouldOmitNullReason() {
            var req = ReleaseRequest.builder()
                    .idempotencyKey("rel-2")
                    .build();

            Map<String, Object> map = req.toMap();

            assertThat(map).doesNotContainKey("reason");
        }
    }

    @Nested
    @DisplayName("DecisionRequest")
    class DecisionRequestTest {

        @Test
        void shouldSerializeAllFields() {
            var req = DecisionRequest.builder()
                    .idempotencyKey("dec-1")
                    .subject(Subject.builder().tenant("t1").app("my-app").build())
                    .action(new Action("llm.completion", "claude-3", null))
                    .estimate(new Amount(Unit.USD_MICROCENTS, 5000))
                    .metadata(Map.of("source", "test"))
                    .build();

            Map<String, Object> map = req.toMap();

            assertThat(map.get("idempotency_key")).isEqualTo("dec-1");
            assertThat(map).containsKey("subject");
            assertThat(map).containsKey("action");
            assertThat(map).containsKey("estimate");
            assertThat(map).containsKey("metadata");
        }
    }

    @Nested
    @DisplayName("EventCreateRequest")
    class EventCreateRequestTest {

        @Test
        void shouldSerializeAllFields() {
            var req = EventCreateRequest.builder()
                    .idempotencyKey("evt-1")
                    .subject(Subject.builder().tenant("t1").build())
                    .action(new Action("tool.search", "web-search", null))
                    .actual(new Amount(Unit.CREDITS, 10))
                    .overagePolicy(CommitOveragePolicy.ALLOW_WITH_OVERDRAFT)
                    .clientTimeMs(1700000000000L)
                    .build();

            Map<String, Object> map = req.toMap();

            assertThat(map.get("idempotency_key")).isEqualTo("evt-1");
            assertThat(map.get("overage_policy")).isEqualTo("ALLOW_WITH_OVERDRAFT");
            assertThat(map.get("client_time_ms")).isEqualTo(1700000000000L);
        }
    }

    @Nested
    @DisplayName("ReservationExtendRequest")
    class ReservationExtendRequestTest {

        @Test
        void shouldSerializeAllFields() {
            var req = ReservationExtendRequest.builder()
                    .idempotencyKey("ext-1")
                    .extendByMs(30000L)
                    .metadata(Map.of("reason", "long-running"))
                    .build();

            Map<String, Object> map = req.toMap();

            assertThat(map.get("idempotency_key")).isEqualTo("ext-1");
            assertThat(map.get("extend_by_ms")).isEqualTo(30000L);
            assertThat(map.get("metadata")).isEqualTo(Map.of("reason", "long-running"));
        }
    }

    // ========================================================================
    // Response DTOs — fromMap()
    // ========================================================================

    @Nested
    @DisplayName("ReservationResult (fromMap)")
    class ReservationResultTest {

        @Test
        void shouldDeserializeAllFields() {
            Map<String, Object> raw = Map.ofEntries(
                    Map.entry("decision", "ALLOW"),
                    Map.entry("reservation_id", "res-123"),
                    Map.entry("affected_scopes", List.of("tenant:t1", "workspace:ws1")),
                    Map.entry("expires_at_ms", 1700000060000L),
                    Map.entry("scope_path", "tenant:t1/workspace:ws1"),
                    Map.entry("reserved", Map.of("unit", "TOKENS", "amount", 1000)),
                    Map.entry("reason_code", "BUDGET_OK"),
                    Map.entry("retry_after_ms", 5000)
            );

            ReservationResult result = ReservationResult.fromMap(raw);

            assertThat(result.getDecision()).isEqualTo(Decision.ALLOW);
            assertThat(result.getReservationId()).isEqualTo("res-123");
            assertThat(result.getAffectedScopes()).containsExactly("tenant:t1", "workspace:ws1");
            assertThat(result.getExpiresAtMs()).isEqualTo(1700000060000L);
            assertThat(result.getScopePath()).isEqualTo("tenant:t1/workspace:ws1");
            assertThat(result.getReserved().getUnit()).isEqualTo(Unit.TOKENS);
            assertThat(result.getReserved().getAmount()).isEqualTo(1000L);
            assertThat(result.getReasonCode()).isEqualTo("BUDGET_OK");
            assertThat(result.getRetryAfterMs()).isEqualTo(5000);
        }

        @Test
        void shouldHandleNullMap() {
            assertThat(ReservationResult.fromMap(null)).isNull();
        }
    }

    @Nested
    @DisplayName("CommitResult (fromMap)")
    class CommitResultTest {

        @Test
        void shouldDeserializeAllFields() {
            Map<String, Object> raw = Map.of(
                    "status", "COMMITTED",
                    "charged", Map.of("unit", "TOKENS", "amount", 800),
                    "released", Map.of("unit", "TOKENS", "amount", 200)
            );

            CommitResult result = CommitResult.fromMap(raw);

            assertThat(result.getStatus()).isEqualTo(CommitStatus.COMMITTED);
            assertThat(result.getCharged().getAmount()).isEqualTo(800L);
            assertThat(result.getReleased().getAmount()).isEqualTo(200L);
        }
    }

    @Nested
    @DisplayName("ReleaseResult (fromMap)")
    class ReleaseResultTest {

        @Test
        void shouldDeserializeAllFields() {
            Map<String, Object> raw = Map.of(
                    "status", "RELEASED",
                    "released", Map.of("unit", "TOKENS", "amount", 1000)
            );

            ReleaseResult result = ReleaseResult.fromMap(raw);

            assertThat(result.getStatus()).isEqualTo(ReleaseStatus.RELEASED);
            assertThat(result.getReleased().getAmount()).isEqualTo(1000L);
        }
    }

    @Nested
    @DisplayName("DecisionResult (fromMap)")
    class DecisionResultTest {

        @Test
        void shouldDeserializeAllFields() {
            Map<String, Object> raw = Map.of(
                    "decision", "ALLOW_WITH_CAPS",
                    "caps", Map.of("max_tokens", 500, "cooldown_ms", 1000),
                    "reason_code", "APPROACHING_LIMIT",
                    "retry_after_ms", 0,
                    "affected_scopes", List.of("tenant:t1")
            );

            DecisionResult result = DecisionResult.fromMap(raw);

            assertThat(result.getDecision()).isEqualTo(Decision.ALLOW_WITH_CAPS);
            assertThat(result.getCaps().getMaxTokens()).isEqualTo(500);
            assertThat(result.getCaps().getCooldownMs()).isEqualTo(1000);
            assertThat(result.getReasonCode()).isEqualTo("APPROACHING_LIMIT");
            assertThat(result.getAffectedScopes()).containsExactly("tenant:t1");
        }
    }

    @Nested
    @DisplayName("EventResult (fromMap)")
    class EventResultTest {

        @Test
        void shouldDeserializeAllFields() {
            Map<String, Object> raw = Map.of(
                    "status", "APPLIED",
                    "event_id", "evt-456"
            );

            EventResult result = EventResult.fromMap(raw);

            assertThat(result.getStatus()).isEqualTo(EventStatus.APPLIED);
            assertThat(result.getEventId()).isEqualTo("evt-456");
        }
    }

    @Nested
    @DisplayName("ExtendResult (fromMap)")
    class ExtendResultTest {

        @Test
        void shouldDeserializeAllFields() {
            Map<String, Object> raw = Map.of(
                    "status", "ACTIVE",
                    "expires_at_ms", 1700000090000L
            );

            ExtendResult result = ExtendResult.fromMap(raw);

            assertThat(result.getStatus()).isEqualTo(ExtendStatus.ACTIVE);
            assertThat(result.getExpiresAtMs()).isEqualTo(1700000090000L);
        }
    }

    @Nested
    @DisplayName("BalanceQueryResult (fromMap)")
    class BalanceQueryResultTest {

        @Test
        void shouldDeserializeWithPagination() {
            Map<String, Object> raw = Map.of(
                    "balances", List.of(
                            Map.of("scope", "tenant:t1",
                                    "scope_path", "tenant:t1",
                                    "remaining", Map.of("unit", "TOKENS", "amount", 95000))
                    ),
                    "has_more", true,
                    "next_cursor", "cursor-abc"
            );

            BalanceQueryResult result = BalanceQueryResult.fromMap(raw);

            assertThat(result.getBalances()).hasSize(1);
            assertThat(result.getHasMore()).isTrue();
            assertThat(result.getNextCursor()).isEqualTo("cursor-abc");
        }
    }

    @Nested
    @DisplayName("ErrorResponse (fromMap)")
    class ErrorResponseTest {

        @Test
        void shouldDeserializeAllFields() {
            Map<String, Object> raw = Map.of(
                    "error", "BUDGET_EXCEEDED",
                    "message", "Insufficient budget",
                    "request_id", "req-789",
                    "details", Map.of("scope", "tenant:t1")
            );

            ErrorResponse result = ErrorResponse.fromMap(raw);

            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.BUDGET_EXCEEDED);
            assertThat(result.getMessage()).isEqualTo("Insufficient budget");
            assertThat(result.getRequestId()).isEqualTo("req-789");
            assertThat(result.getDetails()).containsEntry("scope", "tenant:t1");
        }

        @Test
        void shouldHandleUnknownErrorCode() {
            Map<String, Object> raw = Map.of(
                    "error", "SOME_FUTURE_ERROR",
                    "message", "Unknown",
                    "request_id", "req-000"
            );

            ErrorResponse result = ErrorResponse.fromMap(raw);

            assertThat(result.getErrorCode()).isEqualTo(ErrorCode.UNKNOWN);
        }
    }

    // ========================================================================
    // Nested Models
    // ========================================================================

    @Nested
    @DisplayName("Subject")
    class SubjectTest {

        @Test
        void shouldRoundTrip() {
            Subject original = Subject.builder()
                    .tenant("t1").workspace("ws").app("app1")
                    .workflow("wf").agent("agent1").toolset("tools")
                    .dimensions(Map.of("cost_center", "eng"))
                    .build();

            Map<String, Object> map = original.toMap();
            Subject restored = Subject.fromMap(map);

            assertThat(restored.getTenant()).isEqualTo("t1");
            assertThat(restored.getWorkspace()).isEqualTo("ws");
            assertThat(restored.getApp()).isEqualTo("app1");
            assertThat(restored.getWorkflow()).isEqualTo("wf");
            assertThat(restored.getAgent()).isEqualTo("agent1");
            assertThat(restored.getToolset()).isEqualTo("tools");
            assertThat(restored.getDimensions()).containsEntry("cost_center", "eng");
        }

        @Test
        void shouldOmitNullFields() {
            Subject s = Subject.builder().tenant("t1").build();
            Map<String, Object> map = s.toMap();

            assertThat(map).containsKey("tenant");
            assertThat(map).doesNotContainKey("workspace");
            assertThat(map).doesNotContainKey("dimensions");
        }

        @Test
        void shouldValidateStandardField() {
            assertThat(Subject.builder().tenant("t1").build().hasAtLeastOneStandardField()).isTrue();
            assertThat(Subject.builder().dimensions(Map.of("k", "v")).build().hasAtLeastOneStandardField()).isFalse();
        }
    }

    @Nested
    @DisplayName("Amount")
    class AmountTest {

        @Test
        void shouldRoundTrip() {
            Amount original = new Amount(Unit.USD_MICROCENTS, 5000000);
            Map<String, Object> map = original.toMap();
            Amount restored = Amount.fromMap(map);

            assertThat(restored.getUnit()).isEqualTo(Unit.USD_MICROCENTS);
            assertThat(restored.getAmount()).isEqualTo(5000000L);
        }

        @Test
        void shouldSerializeUnitAsString() {
            Map<String, Object> map = new Amount(Unit.TOKENS, 100).toMap();
            assertThat(map.get("unit")).isEqualTo("TOKENS");
        }
    }

    @Nested
    @DisplayName("Balance (fromMap)")
    class BalanceTest {

        @Test
        void shouldDeserializeWithDebtFields() {
            Map<String, Object> raw = Map.ofEntries(
                    Map.entry("scope", "tenant:t1"),
                    Map.entry("scope_path", "tenant:t1"),
                    Map.entry("remaining", Map.of("unit", "TOKENS", "amount", -500)),
                    Map.entry("reserved", Map.of("unit", "TOKENS", "amount", 1000)),
                    Map.entry("spent", Map.of("unit", "TOKENS", "amount", 8000)),
                    Map.entry("allocated", Map.of("unit", "TOKENS", "amount", 10000)),
                    Map.entry("debt", Map.of("unit", "TOKENS", "amount", 1500)),
                    Map.entry("overdraft_limit", Map.of("unit", "TOKENS", "amount", 5000)),
                    Map.entry("is_over_limit", false)
            );

            Balance balance = Balance.fromMap(raw);

            assertThat(balance.getScope()).isEqualTo("tenant:t1");
            assertThat(balance.getScopePath()).isEqualTo("tenant:t1");
            assertThat(balance.getRemaining().getAmount()).isEqualTo(-500L);
            assertThat(balance.getReserved().getAmount()).isEqualTo(1000L);
            assertThat(balance.getSpent().getAmount()).isEqualTo(8000L);
            assertThat(balance.getAllocated().getAmount()).isEqualTo(10000L);
            assertThat(balance.getDebt().getAmount()).isEqualTo(1500L);
            assertThat(balance.getOverdraftLimit().getAmount()).isEqualTo(5000L);
            assertThat(balance.getIsOverLimit()).isFalse();
        }
    }

    @Nested
    @DisplayName("CyclesMetrics")
    class CyclesMetricsTest {

        @Test
        void shouldSerializeAllFields() {
            var metrics = new CyclesMetrics();
            metrics.setTokensInput(100);
            metrics.setTokensOutput(50);
            metrics.setLatencyMs(200);
            metrics.setModelVersion("v1");
            metrics.putCustom("cache_hit", true);

            Map<String, Object> map = metrics.toMap();

            assertThat(map.get("tokens_input")).isEqualTo(100);
            assertThat(map.get("tokens_output")).isEqualTo(50);
            assertThat(map.get("latency_ms")).isEqualTo(200);
            assertThat(map.get("model_version")).isEqualTo("v1");
            @SuppressWarnings("unchecked")
            Map<String, Object> custom = (Map<String, Object>) map.get("custom");
            assertThat(custom).containsEntry("cache_hit", true);
        }

        @Test
        void shouldReportEmpty() {
            assertThat(new CyclesMetrics().isEmpty()).isTrue();

            var metrics = new CyclesMetrics();
            metrics.setTokensInput(1);
            assertThat(metrics.isEmpty()).isFalse();
        }
    }

    // ========================================================================
    // Enums
    // ========================================================================

    @Nested
    @DisplayName("Enums")
    class EnumTests {

        @Test
        void unitFromString() {
            assertThat(Unit.fromString("USD_MICROCENTS")).isEqualTo(Unit.USD_MICROCENTS);
            assertThat(Unit.fromString("TOKENS")).isEqualTo(Unit.TOKENS);
            assertThat(Unit.fromString("CREDITS")).isEqualTo(Unit.CREDITS);
            assertThat(Unit.fromString("RISK_POINTS")).isEqualTo(Unit.RISK_POINTS);
        }

        @Test
        void decisionFromString() {
            assertThat(Decision.fromString("ALLOW")).isEqualTo(Decision.ALLOW);
            assertThat(Decision.fromString("ALLOW_WITH_CAPS")).isEqualTo(Decision.ALLOW_WITH_CAPS);
            assertThat(Decision.fromString("DENY")).isEqualTo(Decision.DENY);
        }

        @Test
        void errorCodeFromString() {
            assertThat(ErrorCode.fromString("BUDGET_EXCEEDED")).isEqualTo(ErrorCode.BUDGET_EXCEEDED);
            assertThat(ErrorCode.fromString("OVERDRAFT_LIMIT_EXCEEDED")).isEqualTo(ErrorCode.OVERDRAFT_LIMIT_EXCEEDED);
            assertThat(ErrorCode.fromString("UNKNOWN_FUTURE")).isEqualTo(ErrorCode.UNKNOWN);
        }

        @Test
        void commitOveragePolicyFromString() {
            assertThat(CommitOveragePolicy.fromString("REJECT")).isEqualTo(CommitOveragePolicy.REJECT);
            assertThat(CommitOveragePolicy.fromString("ALLOW_IF_AVAILABLE")).isEqualTo(CommitOveragePolicy.ALLOW_IF_AVAILABLE);
            assertThat(CommitOveragePolicy.fromString("ALLOW_WITH_OVERDRAFT")).isEqualTo(CommitOveragePolicy.ALLOW_WITH_OVERDRAFT);
        }
    }
}
