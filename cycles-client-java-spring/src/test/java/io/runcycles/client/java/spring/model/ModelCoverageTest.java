package io.runcycles.client.java.spring.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Additional model coverage tests targeting toString(), getters on typed results,
 * fromMap() with missing/wrong-type fields, boolean convenience helpers, and
 * request DTO toMap() null-field branches.
 */
@DisplayName("Model Coverage Tests")
class ModelCoverageTest {

    // ========================================================================
    // EventCreateRequest
    // ========================================================================

    @Nested
    @DisplayName("EventCreateRequest coverage")
    class EventCreateRequestCoverage {

        @Test
        void gettersReturnBuilderValues() {
            var metrics = new CyclesMetrics();
            metrics.setTokensInput(10);
            var req = EventCreateRequest.builder()
                    .idempotencyKey("k1")
                    .subject(Subject.builder().tenant("t").build())
                    .action(new Action("k", "n", null))
                    .actual(new Amount(Unit.TOKENS, 100L))
                    .overagePolicy(CommitOveragePolicy.ALLOW_WITH_OVERDRAFT)
                    .metrics(metrics)
                    .clientTimeMs(123L)
                    .metadata(Map.of("a", "b"))
                    .build();

            assertThat(req.getIdempotencyKey()).isEqualTo("k1");
            assertThat(req.getSubject()).isNotNull();
            assertThat(req.getAction()).isNotNull();
            assertThat(req.getActual()).isNotNull();
            assertThat(req.getOveragePolicy()).isEqualTo(CommitOveragePolicy.ALLOW_WITH_OVERDRAFT);
            assertThat(req.getMetrics()).isNotNull();
            assertThat(req.getClientTimeMs()).isEqualTo(123L);
            assertThat(req.getMetadata()).containsEntry("a", "b");
        }

        @Test
        void toStringContainsClassName() {
            var req = EventCreateRequest.builder().idempotencyKey("k1").build();
            assertThat(req.toString()).contains("EventCreateRequest");
        }

        @Test
        void toMapIncludesMetricsWhenNonEmpty() {
            var metrics = new CyclesMetrics();
            metrics.setTokensInput(10);
            var req = EventCreateRequest.builder()
                    .subject(Subject.builder().tenant("t").build())
                    .action(new Action("k", "n", null))
                    .actual(new Amount(Unit.TOKENS, 100L))
                    .metrics(metrics)
                    .metadata(Map.of("x", "y"))
                    .build();

            Map<String, Object> map = req.toMap();
            assertThat(map).containsKey("metrics");
            assertThat(map).containsKey("metadata");
        }

        @Test
        void toMapOmitsNullFields() {
            var req = EventCreateRequest.builder().overagePolicy(null).build();
            Map<String, Object> map = req.toMap();
            assertThat(map).doesNotContainKey("idempotency_key");
            assertThat(map).doesNotContainKey("subject");
            assertThat(map).doesNotContainKey("action");
            assertThat(map).doesNotContainKey("actual");
            assertThat(map).doesNotContainKey("overage_policy");
            assertThat(map).doesNotContainKey("metrics");
            assertThat(map).doesNotContainKey("client_time_ms");
            assertThat(map).doesNotContainKey("metadata");
        }

        @Test
        void builderDefaultValues() {
            var req = EventCreateRequest.builder().build();
            assertThat(req.getIdempotencyKey()).isNull();
            assertThat(req.getMetrics()).isNull();
            assertThat(req.getClientTimeMs()).isNull();
        }
    }

    // ========================================================================
    // ReservationDetailResult
    // ========================================================================

    @Nested
    @DisplayName("ReservationDetailResult coverage")
    class ReservationDetailResultCoverage {

        @Test
        void fromMapWithMissingFields() {
            Map<String, Object> raw = Map.of();
            ReservationDetailResult r = ReservationDetailResult.fromMap(raw);

            assertThat(r.getReservationId()).isNull();
            assertThat(r.getStatus()).isNull();
            assertThat(r.getIdempotencyKey()).isNull();
            assertThat(r.getSubject()).isNull();
            assertThat(r.getAction()).isNull();
            assertThat(r.getReserved()).isNull();
            assertThat(r.getCommitted()).isNull();
            assertThat(r.getCreatedAtMs()).isNull();
            assertThat(r.getExpiresAtMs()).isNull();
            assertThat(r.getFinalizedAtMs()).isNull();
            assertThat(r.getScopePath()).isNull();
            assertThat(r.getAffectedScopes()).isEmpty();
            assertThat(r.getMetadata()).isNull();
        }

        @Test
        void statusHelpers() {
            Map<String, Object> active = Map.of("status", "ACTIVE");
            ReservationDetailResult r = ReservationDetailResult.fromMap(active);
            assertThat(r.isActive()).isTrue();
            assertThat(r.isCommitted()).isFalse();
            assertThat(r.isReleased()).isFalse();
            assertThat(r.isExpired()).isFalse();

            assertThat(ReservationDetailResult.fromMap(Map.of("status", "RELEASED")).isReleased()).isTrue();
            assertThat(ReservationDetailResult.fromMap(Map.of("status", "EXPIRED")).isExpired()).isTrue();
        }

        @Test
        void toStringContainsClassName() {
            var r = ReservationDetailResult.fromMap(Map.of("reservation_id", "r1", "status", "ACTIVE"));
            assertThat(r.toString()).contains("ReservationDetailResult");
        }

        @Test
        void fromMapWithWrongTypes() {
            Map<String, Object> raw = new HashMap<>();
            raw.put("reservation_id", 123);       // not String
            raw.put("status", 456);                // not String
            raw.put("subject", "not-a-map");       // not Map
            raw.put("action", "not-a-map");
            raw.put("reserved", "not-a-map");
            raw.put("committed", "not-a-map");
            raw.put("created_at_ms", "not-number");
            raw.put("expires_at_ms", "not-number");
            raw.put("finalized_at_ms", "not-number");
            raw.put("scope_path", 999);
            raw.put("affected_scopes", "not-list");
            raw.put("metadata", "not-map");

            ReservationDetailResult r = ReservationDetailResult.fromMap(raw);
            assertThat(r.getReservationId()).isNull();
            assertThat(r.getStatus()).isNull();
            assertThat(r.getSubject()).isNull();
            assertThat(r.getAction()).isNull();
            assertThat(r.getReserved()).isNull();
            assertThat(r.getCommitted()).isNull();
            assertThat(r.getCreatedAtMs()).isNull();
            assertThat(r.getFinalizedAtMs()).isNull();
            assertThat(r.getScopePath()).isNull();
            assertThat(r.getAffectedScopes()).isEmpty();
            assertThat(r.getMetadata()).isNull();
        }
    }

    // ========================================================================
    // ReservationSummaryResult
    // ========================================================================

    @Nested
    @DisplayName("ReservationSummaryResult coverage")
    class ReservationSummaryResultCoverage {

        @Test
        void fromMapWithMissingFields() {
            ReservationSummaryResult r = ReservationSummaryResult.fromMap(Map.of());
            assertThat(r.getReservationId()).isNull();
            assertThat(r.getIdempotencyKey()).isNull();
            assertThat(r.getSubject()).isNull();
            assertThat(r.getAction()).isNull();
            assertThat(r.getReserved()).isNull();
            assertThat(r.getCreatedAtMs()).isNull();
            assertThat(r.getExpiresAtMs()).isNull();
            assertThat(r.getScopePath()).isNull();
        }

        @Test
        void statusHelpers() {
            assertThat(ReservationSummaryResult.fromMap(Map.of("status", "RELEASED")).isReleased()).isTrue();
            assertThat(ReservationSummaryResult.fromMap(Map.of("status", "EXPIRED")).isExpired()).isTrue();
            assertThat(ReservationSummaryResult.fromMap(Map.of("status", "COMMITTED")).isCommitted()).isTrue();
        }

        @Test
        void toStringContainsClassName() {
            assertThat(ReservationSummaryResult.fromMap(Map.of()).toString()).contains("ReservationSummaryResult");
        }

        @Test
        void fromMapWithWrongTypes() {
            Map<String, Object> raw = new HashMap<>();
            raw.put("reservation_id", 123);
            raw.put("idempotency_key", 456);
            raw.put("subject", "not-a-map");
            raw.put("action", "not-a-map");
            raw.put("reserved", "not-a-map");
            raw.put("created_at_ms", "not-num");
            raw.put("expires_at_ms", "not-num");
            raw.put("scope_path", 999);
            raw.put("affected_scopes", "not-list");

            ReservationSummaryResult r = ReservationSummaryResult.fromMap(raw);
            assertThat(r.getReservationId()).isNull();
            assertThat(r.getSubject()).isNull();
            assertThat(r.getReserved()).isNull();
            assertThat(r.getCreatedAtMs()).isNull();
            assertThat(r.getAffectedScopes()).isEmpty();
        }
    }

    // ========================================================================
    // ReservationCreateRequest
    // ========================================================================

    @Nested
    @DisplayName("ReservationCreateRequest coverage")
    class ReservationCreateRequestCoverage {

        @Test
        void gettersReturnBuilderValues() {
            var req = ReservationCreateRequest.builder()
                    .idempotencyKey("k1")
                    .subject(Subject.builder().tenant("t").build())
                    .action(new Action("k", "n", null))
                    .estimate(new Amount(Unit.TOKENS, 100L))
                    .ttlMs(30000L)
                    .gracePeriodMs(2000L)
                    .overagePolicy(CommitOveragePolicy.ALLOW_IF_AVAILABLE)
                    .dryRun(true)
                    .metadata(Map.of("x", "y"))
                    .build();

            assertThat(req.getIdempotencyKey()).isEqualTo("k1");
            assertThat(req.getSubject()).isNotNull();
            assertThat(req.getAction()).isNotNull();
            assertThat(req.getEstimate()).isNotNull();
            assertThat(req.getTtlMs()).isEqualTo(30000L);
            assertThat(req.getGracePeriodMs()).isEqualTo(2000L);
            assertThat(req.getOveragePolicy()).isEqualTo(CommitOveragePolicy.ALLOW_IF_AVAILABLE);
            assertThat(req.getDryRun()).isTrue();
            assertThat(req.getMetadata()).containsEntry("x", "y");
        }

        @Test
        void toStringContainsClassName() {
            assertThat(ReservationCreateRequest.builder().build().toString()).contains("ReservationCreateRequest");
        }
    }

    // ========================================================================
    // DecisionResult
    // ========================================================================

    @Nested
    @DisplayName("DecisionResult coverage")
    class DecisionResultCoverage {

        @Test
        void fromMapWithMissingFields() {
            DecisionResult r = DecisionResult.fromMap(Map.of());
            assertThat(r.getDecision()).isNull();
            assertThat(r.getCaps()).isNull();
            assertThat(r.getReasonCode()).isNull();
            assertThat(r.getRetryAfterMs()).isNull();
            assertThat(r.getAffectedScopes()).isEmpty();
        }

        @Test
        void booleanHelpers() {
            DecisionResult allow = DecisionResult.fromMap(Map.of("decision", "ALLOW"));
            assertThat(allow.isAllowed()).isTrue();
            assertThat(allow.isDenied()).isFalse();

            DecisionResult deny = DecisionResult.fromMap(Map.of("decision", "DENY"));
            assertThat(deny.isAllowed()).isFalse();
            assertThat(deny.isDenied()).isTrue();

            DecisionResult awc = DecisionResult.fromMap(Map.of("decision", "ALLOW_WITH_CAPS"));
            assertThat(awc.isAllowed()).isTrue();
            assertThat(awc.isDenied()).isFalse();
        }

        @Test
        void toStringContainsClassName() {
            assertThat(DecisionResult.fromMap(Map.of()).toString()).contains("DecisionResult");
        }

        @Test
        void fromMapWithWrongTypes() {
            Map<String, Object> raw = new HashMap<>();
            raw.put("decision", 123);
            raw.put("caps", "not-map");
            raw.put("reason_code", 456);
            raw.put("retry_after_ms", "not-num");
            raw.put("affected_scopes", "not-list");

            DecisionResult r = DecisionResult.fromMap(raw);
            assertThat(r.getDecision()).isNull();
            assertThat(r.getCaps()).isNull();
            assertThat(r.getReasonCode()).isNull();
            assertThat(r.getRetryAfterMs()).isNull();
            assertThat(r.getAffectedScopes()).isEmpty();
        }
    }

    // ========================================================================
    // DecisionRequest
    // ========================================================================

    @Nested
    @DisplayName("DecisionRequest coverage")
    class DecisionRequestCoverage {

        @Test
        void gettersReturnBuilderValues() {
            var req = DecisionRequest.builder()
                    .idempotencyKey("k1")
                    .subject(Subject.builder().tenant("t").build())
                    .action(new Action("k", "n", null))
                    .estimate(new Amount(Unit.TOKENS, 100L))
                    .metadata(Map.of("a", "b"))
                    .build();

            assertThat(req.getIdempotencyKey()).isEqualTo("k1");
            assertThat(req.getSubject()).isNotNull();
            assertThat(req.getAction()).isNotNull();
            assertThat(req.getEstimate()).isNotNull();
            assertThat(req.getMetadata()).containsEntry("a", "b");
        }

        @Test
        void toMapOmitsNullFields() {
            var req = DecisionRequest.builder().build();
            Map<String, Object> map = req.toMap();
            assertThat(map).doesNotContainKey("idempotency_key");
            assertThat(map).doesNotContainKey("subject");
            assertThat(map).doesNotContainKey("action");
            assertThat(map).doesNotContainKey("estimate");
            assertThat(map).doesNotContainKey("metadata");
        }

        @Test
        void toStringContainsClassName() {
            assertThat(DecisionRequest.builder().build().toString()).contains("DecisionRequest");
        }
    }

    // ========================================================================
    // CommitRequest
    // ========================================================================

    @Nested
    @DisplayName("CommitRequest coverage")
    class CommitRequestCoverage {

        @Test
        void gettersReturnBuilderValues() {
            var metrics = new CyclesMetrics();
            metrics.setLatencyMs(100);
            var req = CommitRequest.builder()
                    .idempotencyKey("k1")
                    .actual(new Amount(Unit.TOKENS, 800L))
                    .metrics(metrics)
                    .metadata(Map.of("x", "y"))
                    .build();

            assertThat(req.getIdempotencyKey()).isEqualTo("k1");
            assertThat(req.getActual()).isNotNull();
            assertThat(req.getMetrics()).isNotNull();
            assertThat(req.getMetadata()).containsEntry("x", "y");
        }

        @Test
        void toMapOmitsNullFields() {
            var req = CommitRequest.builder().build();
            Map<String, Object> map = req.toMap();
            assertThat(map).doesNotContainKey("idempotency_key");
            assertThat(map).doesNotContainKey("actual");
            assertThat(map).doesNotContainKey("metrics");
            assertThat(map).doesNotContainKey("metadata");
        }

        @Test
        void toStringContainsClassName() {
            assertThat(CommitRequest.builder().build().toString()).contains("CommitRequest");
        }
    }

    // ========================================================================
    // ErrorResponse
    // ========================================================================

    @Nested
    @DisplayName("ErrorResponse coverage")
    class ErrorResponseCoverage {

        @Test
        void fromMapWithNoErrorField() {
            assertThat(ErrorResponse.fromMap(Map.of("message", "no error field"))).isNull();
        }

        @Test
        void toExceptionWithNullMessage() {
            Map<String, Object> raw = Map.of("error", "BUDGET_EXCEEDED");
            ErrorResponse err = ErrorResponse.fromMap(raw);
            CyclesProtocolException ex = err.toException(409);
            assertThat(ex.getMessage()).contains("BUDGET_EXCEEDED");
        }

        @Test
        void toExceptionWithMessage() {
            Map<String, Object> raw = Map.of("error", "BUDGET_EXCEEDED", "message", "No budget");
            ErrorResponse err = ErrorResponse.fromMap(raw);
            CyclesProtocolException ex = err.toException(409);
            assertThat(ex.getMessage()).isEqualTo("No budget");
        }

        @Test
        void toStringContainsClassName() {
            ErrorResponse err = ErrorResponse.fromMap(Map.of("error", "BUDGET_EXCEEDED"));
            assertThat(err.toString()).contains("ErrorResponse");
        }

        @Test
        void fromMapWithWrongTypes() {
            Map<String, Object> raw = new HashMap<>();
            raw.put("error", "BUDGET_EXCEEDED");
            raw.put("message", 123);          // not String
            raw.put("request_id", 456);        // not String
            raw.put("details", "not-map");     // not Map

            ErrorResponse err = ErrorResponse.fromMap(raw);
            assertThat(err.getErrorCode()).isEqualTo(ErrorCode.BUDGET_EXCEEDED);
            assertThat(err.getMessage()).isNull();
            assertThat(err.getRequestId()).isNull();
            assertThat(err.getDetails()).isNull();
        }
    }

    // ========================================================================
    // CommitResult
    // ========================================================================

    @Nested
    @DisplayName("CommitResult coverage")
    class CommitResultCoverage {

        @Test
        void fromMapWithMissingFields() {
            CommitResult r = CommitResult.fromMap(Map.of());
            assertThat(r.getStatus()).isNull();
            assertThat(r.getCharged()).isNull();
            assertThat(r.getReleased()).isNull();
            assertThat(r.getBalances()).isNull();
        }

        @Test
        void toStringContainsClassName() {
            assertThat(CommitResult.fromMap(Map.of()).toString()).contains("CommitResult");
        }

        @Test
        void fromMapWithWrongTypes() {
            Map<String, Object> raw = new HashMap<>();
            raw.put("status", 123);
            raw.put("charged", "not-map");
            raw.put("released", "not-map");
            raw.put("balances", "not-list");

            CommitResult r = CommitResult.fromMap(raw);
            assertThat(r.getStatus()).isNull();
            assertThat(r.getCharged()).isNull();
            assertThat(r.getReleased()).isNull();
            assertThat(r.getBalances()).isNull();
        }

        @Test
        void fromMapWithBalances() {
            Map<String, Object> raw = Map.of(
                    "status", "COMMITTED",
                    "charged", Map.of("unit", "TOKENS", "amount", 100),
                    "balances", List.of(Map.of("scope", "t1", "scope_path", "tenant:t1"))
            );
            CommitResult r = CommitResult.fromMap(raw);
            assertThat(r.getBalances()).hasSize(1);
        }
    }

    // ========================================================================
    // EventResult
    // ========================================================================

    @Nested
    @DisplayName("EventResult coverage")
    class EventResultCoverage {

        @Test
        void fromMapWithMissingFields() {
            EventResult r = EventResult.fromMap(Map.of());
            assertThat(r.getStatus()).isNull();
            assertThat(r.getEventId()).isNull();
            assertThat(r.getBalances()).isNull();
        }

        @Test
        void toStringContainsClassName() {
            assertThat(EventResult.fromMap(Map.of()).toString()).contains("EventResult");
        }

        @Test
        void fromMapWithWrongTypes() {
            Map<String, Object> raw = new HashMap<>();
            raw.put("status", 123);
            raw.put("event_id", 456);
            raw.put("balances", "not-list");

            EventResult r = EventResult.fromMap(raw);
            assertThat(r.getStatus()).isNull();
            assertThat(r.getEventId()).isNull();
            assertThat(r.getBalances()).isNull();
        }
    }

    // ========================================================================
    // ReleaseResult
    // ========================================================================

    @Nested
    @DisplayName("ReleaseResult coverage")
    class ReleaseResultCoverage {

        @Test
        void fromMapWithMissingFields() {
            ReleaseResult r = ReleaseResult.fromMap(Map.of());
            assertThat(r.getStatus()).isNull();
            assertThat(r.getReleased()).isNull();
            assertThat(r.getBalances()).isNull();
        }

        @Test
        void toStringContainsClassName() {
            assertThat(ReleaseResult.fromMap(Map.of()).toString()).contains("ReleaseResult");
        }

        @Test
        void fromMapWithWrongTypes() {
            Map<String, Object> raw = new HashMap<>();
            raw.put("status", 123);
            raw.put("released", "not-map");
            raw.put("balances", "not-list");

            ReleaseResult r = ReleaseResult.fromMap(raw);
            assertThat(r.getStatus()).isNull();
            assertThat(r.getReleased()).isNull();
            assertThat(r.getBalances()).isNull();
        }
    }

    // ========================================================================
    // ExtendResult
    // ========================================================================

    @Nested
    @DisplayName("ExtendResult coverage")
    class ExtendResultCoverage {

        @Test
        void fromMapWithMissingFields() {
            ExtendResult r = ExtendResult.fromMap(Map.of());
            assertThat(r.getStatus()).isNull();
            assertThat(r.getExpiresAtMs()).isNull();
            assertThat(r.getBalances()).isNull();
        }

        @Test
        void toStringContainsClassName() {
            assertThat(ExtendResult.fromMap(Map.of()).toString()).contains("ExtendResult");
        }

        @Test
        void fromMapWithWrongTypes() {
            Map<String, Object> raw = new HashMap<>();
            raw.put("status", 123);
            raw.put("expires_at_ms", "not-num");
            raw.put("balances", "not-list");

            ExtendResult r = ExtendResult.fromMap(raw);
            assertThat(r.getStatus()).isNull();
            assertThat(r.getExpiresAtMs()).isNull();
            assertThat(r.getBalances()).isNull();
        }
    }

    // ========================================================================
    // BalanceQueryResult
    // ========================================================================

    @Nested
    @DisplayName("BalanceQueryResult coverage")
    class BalanceQueryResultCoverage {

        @Test
        void fromMapWithMissingFields() {
            BalanceQueryResult r = BalanceQueryResult.fromMap(Map.of());
            assertThat(r.getBalances()).isNull();
            assertThat(r.getHasMore()).isFalse();
            assertThat(r.getNextCursor()).isNull();
        }

        @Test
        void toStringContainsClassName() {
            assertThat(BalanceQueryResult.fromMap(Map.of()).toString()).contains("BalanceQueryResult");
        }

        @Test
        void fromMapWithWrongTypes() {
            Map<String, Object> raw = new HashMap<>();
            raw.put("balances", "not-list");
            raw.put("has_more", "not-bool");
            raw.put("next_cursor", 123);

            BalanceQueryResult r = BalanceQueryResult.fromMap(raw);
            assertThat(r.getBalances()).isNull();
            assertThat(r.getHasMore()).isFalse();
            assertThat(r.getNextCursor()).isNull();
        }
    }

    // ========================================================================
    // ReservationExtendRequest
    // ========================================================================

    @Nested
    @DisplayName("ReservationExtendRequest coverage")
    class ReservationExtendRequestCoverage {

        @Test
        void gettersReturnBuilderValues() {
            var req = ReservationExtendRequest.builder()
                    .idempotencyKey("k1")
                    .extendByMs(5000L)
                    .metadata(Map.of("a", "b"))
                    .build();

            assertThat(req.getIdempotencyKey()).isEqualTo("k1");
            assertThat(req.getExtendByMs()).isEqualTo(5000L);
            assertThat(req.getMetadata()).containsEntry("a", "b");
        }

        @Test
        void toMapOmitsNullFields() {
            var req = ReservationExtendRequest.builder().build();
            Map<String, Object> map = req.toMap();
            assertThat(map).doesNotContainKey("idempotency_key");
            assertThat(map).doesNotContainKey("extend_by_ms");
            assertThat(map).doesNotContainKey("metadata");
        }

        @Test
        void toStringContainsClassName() {
            assertThat(ReservationExtendRequest.builder().build().toString()).contains("ReservationExtendRequest");
        }
    }

    // ========================================================================
    // ReleaseRequest
    // ========================================================================

    @Nested
    @DisplayName("ReleaseRequest coverage")
    class ReleaseRequestCoverage {

        @Test
        void getters() {
            var req = ReleaseRequest.builder()
                    .idempotencyKey("k1")
                    .reason("done")
                    .build();

            assertThat(req.getIdempotencyKey()).isEqualTo("k1");
            assertThat(req.getReason()).isEqualTo("done");
        }

        @Test
        void toMapOmitsNullFields() {
            var req = ReleaseRequest.builder().build();
            Map<String, Object> map = req.toMap();
            assertThat(map).doesNotContainKey("idempotency_key");
            assertThat(map).doesNotContainKey("reason");
        }

        @Test
        void toStringContainsClassName() {
            assertThat(ReleaseRequest.builder().build().toString()).contains("ReleaseRequest");
        }
    }

    // ========================================================================
    // ReservationListResult
    // ========================================================================

    @Nested
    @DisplayName("ReservationListResult coverage")
    class ReservationListResultCoverage {

        @Test
        void toStringContainsEntries() {
            var r = ReservationListResult.fromMap(Map.of("reservations", List.of()));
            assertThat(r.toString()).contains("ReservationListResult");
        }

        @Test
        void fromMapWithNonMapItems() {
            Map<String, Object> raw = Map.of("reservations", List.of("not-a-map", 123));
            ReservationListResult r = ReservationListResult.fromMap(raw);
            assertThat(r.getReservations()).isEmpty();
        }

        @Test
        void fromMapWithWrongTypes() {
            Map<String, Object> raw = new HashMap<>();
            raw.put("reservations", "not-list");
            raw.put("has_more", "not-bool");
            raw.put("next_cursor", 123);

            ReservationListResult r = ReservationListResult.fromMap(raw);
            assertThat(r.getReservations()).isEmpty();
            assertThat(r.getHasMore()).isFalse();
            assertThat(r.getNextCursor()).isNull();
        }
    }

    // ========================================================================
    // ReservationResult
    // ========================================================================

    @Nested
    @DisplayName("ReservationResult coverage")
    class ReservationResultCoverage {

        @Test
        void fromMapWithMissingFields() {
            ReservationResult r = ReservationResult.fromMap(Map.of());
            assertThat(r.getDecision()).isNull();
            assertThat(r.getReservationId()).isNull();
            assertThat(r.getAffectedScopes()).isEmpty();
            assertThat(r.getExpiresAtMs()).isNull();
            assertThat(r.getScopePath()).isNull();
            assertThat(r.getReserved()).isNull();
            assertThat(r.getCaps()).isNull();
            assertThat(r.getReasonCode()).isNull();
            assertThat(r.getRetryAfterMs()).isNull();
            assertThat(r.getBalances()).isNull();
        }

        @Test
        void booleanHelpers() {
            ReservationResult allow = ReservationResult.fromMap(Map.of("decision", "ALLOW"));
            assertThat(allow.isAllowed()).isTrue();
            assertThat(allow.isDenied()).isFalse();

            ReservationResult deny = ReservationResult.fromMap(Map.of("decision", "DENY"));
            assertThat(deny.isAllowed()).isFalse();
            assertThat(deny.isDenied()).isTrue();
        }

        @Test
        void toStringContainsClassName() {
            assertThat(ReservationResult.fromMap(Map.of()).toString()).contains("ReservationResult");
        }

        @Test
        void fromMapWithWrongTypes() {
            Map<String, Object> raw = new HashMap<>();
            raw.put("decision", 123);
            raw.put("reservation_id", 456);
            raw.put("affected_scopes", "not-list");
            raw.put("expires_at_ms", "not-num");
            raw.put("scope_path", 789);
            raw.put("reserved", "not-map");
            raw.put("caps", "not-map");
            raw.put("reason_code", 111);
            raw.put("retry_after_ms", "not-num");
            raw.put("balances", "not-list");

            ReservationResult r = ReservationResult.fromMap(raw);
            assertThat(r.getDecision()).isNull();
            assertThat(r.getReservationId()).isNull();
            assertThat(r.getAffectedScopes()).isEmpty();
            assertThat(r.getReserved()).isNull();
            assertThat(r.getCaps()).isNull();
            assertThat(r.getReasonCode()).isNull();
            assertThat(r.getRetryAfterMs()).isNull();
            assertThat(r.getBalances()).isNull();
        }
    }

    // ========================================================================
    // CyclesMetrics
    // ========================================================================

    @Nested
    @DisplayName("CyclesMetrics coverage")
    class CyclesMetricsCoverage {

        @Test
        void toStringContainsClassName() {
            assertThat(new CyclesMetrics().toString()).contains("CyclesMetrics");
        }

        @Test
        void setAndGetCustom() {
            var m = new CyclesMetrics();
            Map<String, Object> custom = Map.of("a", 1);
            m.setCustom(custom);
            assertThat(m.getCustom()).isEqualTo(custom);
        }

        @Test
        void isEmptyWithOnlyModelVersion() {
            var m = new CyclesMetrics();
            m.setModelVersion("v1");
            assertThat(m.isEmpty()).isFalse();
        }

        @Test
        void isEmptyWithOnlyOutputTokens() {
            var m = new CyclesMetrics();
            m.setTokensOutput(50);
            assertThat(m.isEmpty()).isFalse();
        }

        @Test
        void isEmptyWithOnlyLatency() {
            var m = new CyclesMetrics();
            m.setLatencyMs(100);
            assertThat(m.isEmpty()).isFalse();
        }

        @Test
        void isEmptyWithEmptyCustomMap() {
            var m = new CyclesMetrics();
            m.setCustom(new HashMap<>());
            assertThat(m.isEmpty()).isTrue();
        }

        @Test
        void toMapOmitsNullStandardFields() {
            var m = new CyclesMetrics();
            assertThat(m.toMap()).isEmpty();
        }

        @Test
        void putCustomInitializesMap() {
            var m = new CyclesMetrics();
            m.putCustom("k", "v");
            assertThat(m.getCustom()).containsEntry("k", "v");
        }
    }

    // ========================================================================
    // SignedAmount
    // ========================================================================

    @Nested
    @DisplayName("SignedAmount coverage")
    class SignedAmountCoverage {

        @Test
        void toStringContainsClassName() {
            assertThat(new SignedAmount(Unit.TOKENS, 500L).toString()).contains("SignedAmount");
        }
    }

    // ========================================================================
    // Amount
    // ========================================================================

    @Nested
    @DisplayName("Amount coverage")
    class AmountCoverage {

        @Test
        void toStringContainsClassName() {
            assertThat(new Amount(Unit.TOKENS, 100L).toString()).contains("Amount");
        }

        @Test
        void toMapWithNullFields() {
            Amount a = new Amount(null, null);
            Map<String, Object> map = a.toMap();
            assertThat(map).isEmpty();
        }

        @Test
        void fromMapWithMissingFields() {
            Amount a = Amount.fromMap(Map.of());
            assertThat(a.getUnit()).isNull();
            assertThat(a.getAmount()).isNull();
        }
    }

    // ========================================================================
    // Balance
    // ========================================================================

    @Nested
    @DisplayName("Balance coverage")
    class BalanceCoverage {

        @Test
        void toStringContainsClassName() {
            Balance b = Balance.fromMap(Map.of("scope", "t1", "scope_path", "tenant:t1"));
            assertThat(b.toString()).contains("Balance");
        }

        @Test
        void fromMapWithMissingFields() {
            Balance b = Balance.fromMap(Map.of());
            assertThat(b.getScope()).isNull();
            assertThat(b.getScopePath()).isNull();
            assertThat(b.getRemaining()).isNull();
            assertThat(b.getReserved()).isNull();
            assertThat(b.getSpent()).isNull();
            assertThat(b.getAllocated()).isNull();
            assertThat(b.getDebt()).isNull();
            assertThat(b.getOverdraftLimit()).isNull();
            assertThat(b.getIsOverLimit()).isNull();
        }

        @Test
        void listFromRawWithNonMapItems() {
            List<Balance> list = Balance.listFromRaw(List.of("not-a-map", 123));
            assertThat(list).isEmpty();
        }

        @Test
        void listFromRawWithNull() {
            assertThat(Balance.listFromRaw(null)).isNull();
        }

        @Test
        void fromMapWithWrongTypes() {
            Map<String, Object> raw = new HashMap<>();
            raw.put("scope", 123);
            raw.put("scope_path", 456);
            raw.put("remaining", "not-map");
            raw.put("reserved", "not-map");
            raw.put("spent", "not-map");
            raw.put("allocated", "not-map");
            raw.put("debt", "not-map");
            raw.put("overdraft_limit", "not-map");
            raw.put("is_over_limit", "not-bool");

            Balance b = Balance.fromMap(raw);
            assertThat(b.getScope()).isNull();
            assertThat(b.getRemaining()).isNull();
            assertThat(b.getIsOverLimit()).isNull();
        }
    }

    // ========================================================================
    // Subject
    // ========================================================================

    @Nested
    @DisplayName("Subject coverage")
    class SubjectCoverage {

        @Test
        void toStringContainsClassName() {
            assertThat(Subject.builder().tenant("t").build().toString()).contains("Subject");
        }

        @Test
        void hasAtLeastOneStandardFieldWithBlankValues() {
            assertThat(Subject.builder().tenant("").workspace("").build().hasAtLeastOneStandardField()).isFalse();
            assertThat(Subject.builder().tenant("  ").build().hasAtLeastOneStandardField()).isFalse();
        }

        @Test
        void hasAtLeastOneStandardFieldWithEachField() {
            assertThat(Subject.builder().workspace("ws").build().hasAtLeastOneStandardField()).isTrue();
            assertThat(Subject.builder().app("app").build().hasAtLeastOneStandardField()).isTrue();
            assertThat(Subject.builder().workflow("wf").build().hasAtLeastOneStandardField()).isTrue();
            assertThat(Subject.builder().agent("ag").build().hasAtLeastOneStandardField()).isTrue();
            assertThat(Subject.builder().toolset("ts").build().hasAtLeastOneStandardField()).isTrue();
        }

        @Test
        void toMapOmitsDimensionsWhenEmpty() {
            Subject s = Subject.builder().tenant("t").dimensions(Map.of()).build();
            Map<String, Object> map = s.toMap();
            assertThat(map).doesNotContainKey("dimensions");
        }

        @Test
        void fromMapWithWrongTypes() {
            Map<String, Object> raw = new HashMap<>();
            raw.put("tenant", 123);
            raw.put("workspace", 456);
            raw.put("app", 789);
            raw.put("workflow", true);
            raw.put("agent", List.of());
            raw.put("toolset", Map.of());
            raw.put("dimensions", "not-map");

            Subject s = Subject.fromMap(raw);
            assertThat(s.getTenant()).isNull();
            assertThat(s.getWorkspace()).isNull();
            assertThat(s.getDimensions()).isNull();
        }
    }

    // ========================================================================
    // Action
    // ========================================================================

    @Nested
    @DisplayName("Action coverage")
    class ActionCoverage {

        @Test
        void toStringContainsClassName() {
            assertThat(new Action("k", "n", null).toString()).contains("Action");
        }
    }

    // ========================================================================
    // Caps
    // ========================================================================

    @Nested
    @DisplayName("Caps coverage")
    class CapsCoverage {

        @Test
        void toStringContainsClassName() {
            assertThat(Caps.fromMap(Map.of()).toString()).contains("Caps");
        }

        @Test
        void isToolAllowedWithBothLists() {
            // When both lists present, allowlist takes precedence
            Caps caps = Caps.fromMap(Map.of(
                    "tool_allowlist", List.of("search"),
                    "tool_denylist", List.of("admin")));
            assertThat(caps.isToolAllowed("search")).isTrue();
            assertThat(caps.isToolAllowed("admin")).isFalse();
        }
    }

    // ========================================================================
    // DryRunResult
    // ========================================================================

    @Nested
    @DisplayName("DryRunResult coverage")
    class DryRunResultCoverage {

        @Test
        void toStringContainsClassName() {
            var r = new DryRunResult(Decision.ALLOW, null, List.of(), "p", null, null, null, null);
            assertThat(r.toString()).contains("DryRunResult");
        }

        @Test
        void toStringWithBalances() {
            var r = new DryRunResult(Decision.ALLOW, null, List.of(), "p",
                    null, List.of(), null, null);
            assertThat(r.toString()).contains("0 entries");
        }

        @Test
        void getters() {
            var r = new DryRunResult(Decision.ALLOW, null, List.of("s1"), "path",
                    new Amount(Unit.TOKENS, 100L), List.of(), "CODE", 5000);
            assertThat(r.getBalances()).isEmpty();
        }
    }
}
