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
                    .estimate(new Amount(Unit.TOKENS, 1000L))
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
                    .estimate(new Amount(Unit.TOKENS, 100L))
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
                    .actual(new Amount(Unit.TOKENS, 800L))
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
                    .estimate(new Amount(Unit.USD_MICROCENTS, 5000L))
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
                    .actual(new Amount(Unit.CREDITS, 10L))
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
            Amount original = new Amount(Unit.USD_MICROCENTS, 5000000L);
            Map<String, Object> map = original.toMap();
            Amount restored = Amount.fromMap(map);

            assertThat(restored.getUnit()).isEqualTo(Unit.USD_MICROCENTS);
            assertThat(restored.getAmount()).isEqualTo(5000000L);
        }

        @Test
        void shouldSerializeUnitAsString() {
            Map<String, Object> map = new Amount(Unit.TOKENS, 100L).toMap();
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

        // --- Null/blank/unknown coverage for all enums ---

        @Test
        void enumFromStringWithNull() {
            assertThat(Unit.fromString(null)).isNull();
            assertThat(Decision.fromString(null)).isNull();
            assertThat(ErrorCode.fromString(null)).isNull();
            assertThat(CommitOveragePolicy.fromString(null)).isNull();
            assertThat(CommitStatus.fromString(null)).isNull();
            assertThat(EventStatus.fromString(null)).isNull();
            assertThat(ExtendStatus.fromString(null)).isNull();
            assertThat(ReservationStatus.fromString(null)).isNull();
            assertThat(ReleaseStatus.fromString(null)).isNull();
        }

        @Test
        void enumFromStringWithUnknownValue() {
            assertThat(Unit.fromString("UNKNOWN_UNIT")).isNull();
            assertThat(Decision.fromString("THROTTLE")).isNull();
            assertThat(CommitOveragePolicy.fromString("MAYBE")).isNull();
            assertThat(CommitStatus.fromString("PENDING")).isNull();
            assertThat(EventStatus.fromString("REJECTED")).isNull();
            assertThat(ExtendStatus.fromString("EXPIRED")).isNull();
            assertThat(ReservationStatus.fromString("UNKNOWN")).isNull();
            assertThat(ReleaseStatus.fromString("PENDING")).isNull();
        }

        @Test
        void commitStatusFromString() {
            assertThat(CommitStatus.fromString("COMMITTED")).isEqualTo(CommitStatus.COMMITTED);
        }

        @Test
        void eventStatusFromString() {
            assertThat(EventStatus.fromString("APPLIED")).isEqualTo(EventStatus.APPLIED);
        }

        @Test
        void extendStatusFromString() {
            assertThat(ExtendStatus.fromString("ACTIVE")).isEqualTo(ExtendStatus.ACTIVE);
        }

        @Test
        void reservationStatusFromString() {
            assertThat(ReservationStatus.fromString("ACTIVE")).isEqualTo(ReservationStatus.ACTIVE);
            assertThat(ReservationStatus.fromString("COMMITTED")).isEqualTo(ReservationStatus.COMMITTED);
            assertThat(ReservationStatus.fromString("RELEASED")).isEqualTo(ReservationStatus.RELEASED);
            assertThat(ReservationStatus.fromString("EXPIRED")).isEqualTo(ReservationStatus.EXPIRED);
        }

        @Test
        void releaseStatusFromString() {
            assertThat(ReleaseStatus.fromString("RELEASED")).isEqualTo(ReleaseStatus.RELEASED);
        }

        @Test
        void errorCodeIsRetryable() {
            assertThat(ErrorCode.INTERNAL_ERROR.isRetryable()).isTrue();
            assertThat(ErrorCode.UNKNOWN.isRetryable()).isTrue();
            assertThat(ErrorCode.BUDGET_EXCEEDED.isRetryable()).isFalse();
            assertThat(ErrorCode.INVALID_REQUEST.isRetryable()).isFalse();
        }
    }

    // ========================================================================
    // Action (toMap/fromMap)
    // ========================================================================

    @Nested
    @DisplayName("Action (toMap/fromMap)")
    class ActionTest {

        @Test
        void shouldRoundTrip() {
            Action original = new Action("llm.completion", "gpt-4", List.of("prod", "billing"));
            Map<String, Object> map = original.toMap();
            Action restored = Action.fromMap(map);

            assertThat(restored.getKind()).isEqualTo("llm.completion");
            assertThat(restored.getName()).isEqualTo("gpt-4");
            assertThat(restored.getTags()).containsExactly("prod", "billing");
        }

        @Test
        void shouldOmitNullFields() {
            Action action = new Action("kind", null, null);
            Map<String, Object> map = action.toMap();

            assertThat(map).containsKey("kind");
            assertThat(map).doesNotContainKey("name");
            assertThat(map).doesNotContainKey("tags");
        }

        @Test
        void fromMapWithNull() {
            assertThat(Action.fromMap(null)).isNull();
        }

        @Test
        void fromMapWithMissingFields() {
            Action action = Action.fromMap(Map.of());
            assertThat(action.getKind()).isNull();
            assertThat(action.getName()).isNull();
            assertThat(action.getTags()).isNull();
        }
    }

    // ========================================================================
    // Caps (fromMap)
    // ========================================================================

    @Nested
    @DisplayName("Caps (fromMap)")
    class CapsTest {

        @Test
        void shouldDeserializeAllFields() {
            Map<String, Object> raw = Map.of(
                    "max_tokens", 500,
                    "max_steps_remaining", 10,
                    "tool_allowlist", List.of("search", "compute"),
                    "tool_denylist", List.of("admin"),
                    "cooldown_ms", 2000
            );

            Caps caps = Caps.fromMap(raw);

            assertThat(caps.getMaxTokens()).isEqualTo(500);
            assertThat(caps.getMaxStepsRemaining()).isEqualTo(10);
            assertThat(caps.getToolAllowlist()).containsExactly("search", "compute");
            assertThat(caps.getToolDenylist()).containsExactly("admin");
            assertThat(caps.getCooldownMs()).isEqualTo(2000);
        }

        @Test
        void fromMapWithNull() {
            assertThat(Caps.fromMap(null)).isNull();
        }

        @Test
        void fromMapWithEmptyMap() {
            Caps caps = Caps.fromMap(Map.of());
            assertThat(caps.getMaxTokens()).isNull();
            assertThat(caps.getMaxStepsRemaining()).isNull();
            assertThat(caps.getToolAllowlist()).isNull();
            assertThat(caps.getToolDenylist()).isNull();
            assertThat(caps.getCooldownMs()).isNull();
        }

        @Test
        void isToolAllowedWithAllowlist() {
            Caps caps = Caps.fromMap(Map.of("tool_allowlist", List.of("search")));
            assertThat(caps.isToolAllowed("search")).isTrue();
            assertThat(caps.isToolAllowed("admin")).isFalse();
        }

        @Test
        void isToolAllowedWithDenylist() {
            Caps caps = Caps.fromMap(Map.of("tool_denylist", List.of("admin")));
            assertThat(caps.isToolAllowed("search")).isTrue();
            assertThat(caps.isToolAllowed("admin")).isFalse();
        }

        @Test
        void isToolAllowedWithNeitherList() {
            Caps caps = Caps.fromMap(Map.of());
            assertThat(caps.isToolAllowed("anything")).isTrue();
        }
    }

    // ========================================================================
    // SignedAmount (fromMap)
    // ========================================================================

    @Nested
    @DisplayName("SignedAmount (fromMap)")
    class SignedAmountTest {

        @Test
        void shouldDeserializePositive() {
            Map<String, Object> raw = Map.of("unit", "TOKENS", "amount", 500);
            SignedAmount sa = SignedAmount.fromMap(raw);
            assertThat(sa.getUnit()).isEqualTo(Unit.TOKENS);
            assertThat(sa.getAmount()).isEqualTo(500L);
        }

        @Test
        void shouldDeserializeNegative() {
            Map<String, Object> raw = Map.of("unit", "TOKENS", "amount", -500);
            SignedAmount sa = SignedAmount.fromMap(raw);
            assertThat(sa.getAmount()).isEqualTo(-500L);
        }

        @Test
        void fromMapWithNull() {
            assertThat(SignedAmount.fromMap(null)).isNull();
        }

        @Test
        void fromMapWithMissingFields() {
            SignedAmount sa = SignedAmount.fromMap(Map.of());
            assertThat(sa.getUnit()).isNull();
            assertThat(sa.getAmount()).isEqualTo(0L);
        }
    }

    // ========================================================================
    // Response DTOs — fromMap() with null
    // ========================================================================

    @Nested
    @DisplayName("Response DTOs null handling")
    class ResponseDtoNullHandling {

        @Test
        void commitResultFromNull() {
            assertThat(CommitResult.fromMap(null)).isNull();
        }

        @Test
        void releaseResultFromNull() {
            assertThat(ReleaseResult.fromMap(null)).isNull();
        }

        @Test
        void decisionResultFromNull() {
            assertThat(DecisionResult.fromMap(null)).isNull();
        }

        @Test
        void eventResultFromNull() {
            assertThat(EventResult.fromMap(null)).isNull();
        }

        @Test
        void extendResultFromNull() {
            assertThat(ExtendResult.fromMap(null)).isNull();
        }

        @Test
        void errorResponseFromNull() {
            assertThat(ErrorResponse.fromMap(null)).isNull();
        }

        @Test
        void balanceQueryResultFromNull() {
            assertThat(BalanceQueryResult.fromMap(null)).isNull();
        }

        @Test
        void balanceFromNull() {
            assertThat(Balance.fromMap(null)).isNull();
        }

        @Test
        void amountFromNull() {
            assertThat(Amount.fromMap(null)).isNull();
        }

        @Test
        void subjectFromNull() {
            assertThat(Subject.fromMap(null)).isNull();
        }
    }

    // ========================================================================
    // ReservationSummaryResult (fromMap)
    // ========================================================================

    @Nested
    @DisplayName("ReservationSummaryResult (fromMap)")
    class ReservationSummaryResultTest {

        @Test
        void shouldDeserializeAllFields() {
            Map<String, Object> raw = Map.ofEntries(
                    Map.entry("reservation_id", "res-1"),
                    Map.entry("status", "ACTIVE"),
                    Map.entry("idempotency_key", "idem-1"),
                    Map.entry("subject", Map.of("tenant", "t1")),
                    Map.entry("action", Map.of("kind", "llm", "name", "gpt-4")),
                    Map.entry("reserved", Map.of("unit", "TOKENS", "amount", 1000)),
                    Map.entry("created_at_ms", 1700000000000L),
                    Map.entry("expires_at_ms", 1700000060000L),
                    Map.entry("scope_path", "tenant:t1"),
                    Map.entry("affected_scopes", List.of("tenant:t1"))
            );

            ReservationSummaryResult result = ReservationSummaryResult.fromMap(raw);

            assertThat(result.getReservationId()).isEqualTo("res-1");
            assertThat(result.getStatus()).isEqualTo(ReservationStatus.ACTIVE);
            assertThat(result.isActive()).isTrue();
            assertThat(result.isCommitted()).isFalse();
        }

        @Test
        void fromMapWithNull() {
            assertThat(ReservationSummaryResult.fromMap(null)).isNull();
        }
    }

    // ========================================================================
    // ReservationDetailResult (fromMap)
    // ========================================================================

    @Nested
    @DisplayName("ReservationDetailResult (fromMap)")
    class ReservationDetailResultTest {

        @Test
        void shouldDeserializeAllFields() {
            Map<String, Object> raw = Map.ofEntries(
                    Map.entry("reservation_id", "res-1"),
                    Map.entry("status", "COMMITTED"),
                    Map.entry("idempotency_key", "idem-1"),
                    Map.entry("subject", Map.of("tenant", "t1")),
                    Map.entry("action", Map.of("kind", "llm", "name", "gpt-4")),
                    Map.entry("reserved", Map.of("unit", "TOKENS", "amount", 1000)),
                    Map.entry("committed", Map.of("unit", "TOKENS", "amount", 800)),
                    Map.entry("created_at_ms", 1700000000000L),
                    Map.entry("expires_at_ms", 1700000060000L),
                    Map.entry("finalized_at_ms", 1700000050000L),
                    Map.entry("scope_path", "tenant:t1"),
                    Map.entry("affected_scopes", List.of("tenant:t1")),
                    Map.entry("metadata", Map.of("key", "value"))
            );

            ReservationDetailResult result = ReservationDetailResult.fromMap(raw);

            assertThat(result.getReservationId()).isEqualTo("res-1");
            assertThat(result.getStatus()).isEqualTo(ReservationStatus.COMMITTED);
            assertThat(result.isCommitted()).isTrue();
            assertThat(result.isActive()).isFalse();
            assertThat(result.getCommitted().getAmount()).isEqualTo(800L);
            assertThat(result.getFinalizedAtMs()).isEqualTo(1700000050000L);
            assertThat(result.getMetadata()).containsEntry("key", "value");
        }

        @Test
        void fromMapWithNull() {
            assertThat(ReservationDetailResult.fromMap(null)).isNull();
        }
    }

    // ========================================================================
    // ReservationListResult (fromMap)
    // ========================================================================

    @Nested
    @DisplayName("ReservationListResult (fromMap)")
    class ReservationListResultTest {

        @Test
        void shouldDeserializeWithReservations() {
            Map<String, Object> raw = Map.of(
                    "reservations", List.of(
                            Map.of("reservation_id", "res-1", "status", "ACTIVE",
                                    "affected_scopes", List.of("tenant:t1")),
                            Map.of("reservation_id", "res-2", "status", "COMMITTED",
                                    "affected_scopes", List.of("tenant:t1"))
                    ),
                    "has_more", true,
                    "next_cursor", "cursor-xyz"
            );

            ReservationListResult result = ReservationListResult.fromMap(raw);

            assertThat(result.getReservations()).hasSize(2);
            assertThat(result.getHasMore()).isTrue();
            assertThat(result.getNextCursor()).isEqualTo("cursor-xyz");
        }

        @Test
        void fromMapWithNull() {
            assertThat(ReservationListResult.fromMap(null)).isNull();
        }

        @Test
        void fromMapWithEmptyReservations() {
            Map<String, Object> raw = Map.of("reservations", List.of());
            ReservationListResult result = ReservationListResult.fromMap(raw);
            assertThat(result.getReservations()).isEmpty();
            assertThat(result.getHasMore()).isFalse();
            assertThat(result.getNextCursor()).isNull();
        }
    }

    // ========================================================================
    // DryRunResult
    // ========================================================================

    @Nested
    @DisplayName("DryRunResult")
    class DryRunResultTest {

        @Test
        void shouldExposeAllFields() {
            var dryRun = new DryRunResult(
                    Decision.ALLOW_WITH_CAPS,
                    Caps.fromMap(Map.of("max_tokens", 100)),
                    List.of("tenant:t1"),
                    "tenant:t1",
                    new Amount(Unit.TOKENS, 500L),
                    List.of(),
                    "APPROACHING_LIMIT",
                    3000
            );

            assertThat(dryRun.getDecision()).isEqualTo(Decision.ALLOW_WITH_CAPS);
            assertThat(dryRun.isAllowed()).isTrue();
            assertThat(dryRun.hasCaps()).isTrue();
            assertThat(dryRun.getCaps().getMaxTokens()).isEqualTo(100);
            assertThat(dryRun.getAffectedScopes()).containsExactly("tenant:t1");
            assertThat(dryRun.getScopePath()).isEqualTo("tenant:t1");
            assertThat(dryRun.getReserved().getAmount()).isEqualTo(500L);
            assertThat(dryRun.getReasonCode()).isEqualTo("APPROACHING_LIMIT");
            assertThat(dryRun.getRetryAfterMs()).isEqualTo(3000);
        }

        @Test
        void denyIsNotAllowed() {
            var dryRun = new DryRunResult(Decision.DENY, null, List.of(), null, null, null, null, null);
            assertThat(dryRun.isAllowed()).isFalse();
            assertThat(dryRun.hasCaps()).isFalse();
        }
    }

    // ========================================================================
    // CyclesMetrics extended
    // ========================================================================

    @Nested
    @DisplayName("CyclesMetrics (extended)")
    class CyclesMetricsExtendedTest {

        @Test
        void shouldBeNonEmptyWithOnlyCustomMetrics() {
            var metrics = new CyclesMetrics();
            metrics.putCustom("hit_rate", 0.95);
            assertThat(metrics.isEmpty()).isFalse();
        }

        @Test
        void toMapShouldOmitCustomWhenEmpty() {
            var metrics = new CyclesMetrics();
            metrics.setLatencyMs(100);
            Map<String, Object> map = metrics.toMap();
            assertThat(map).doesNotContainKey("custom");
            assertThat(map.get("latency_ms")).isEqualTo(100);
        }
    }
}
