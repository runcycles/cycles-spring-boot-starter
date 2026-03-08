package io.runcycles.client.java.spring.context;

import io.runcycles.client.java.spring.annotation.Cycles;
import io.runcycles.client.java.spring.evaluation.CyclesValueResolutionService;
import io.runcycles.client.java.spring.model.CyclesMetrics;
import io.runcycles.client.java.spring.model.CyclesProtocolException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CyclesRequestBuilderService")
class CyclesRequestBuilderServiceTest {

    private CyclesRequestBuilderService service;
    private CyclesValueResolutionService resolver;

    @BeforeEach
    void setUp() {
        resolver = mock(CyclesValueResolutionService.class);
        service = new CyclesRequestBuilderService(resolver);
    }

    private Cycles mockCycles(String tenant, String unit, long ttlMs, long gracePeriodMs,
                               String overagePolicy, boolean dryRun) {
        Cycles cycles = mock(Cycles.class);
        when(cycles.tenant()).thenReturn(tenant);
        when(cycles.workspace()).thenReturn("");
        when(cycles.app()).thenReturn("");
        when(cycles.workflow()).thenReturn("");
        when(cycles.agent()).thenReturn("");
        when(cycles.toolset()).thenReturn("");
        when(cycles.unit()).thenReturn(unit);
        when(cycles.ttlMs()).thenReturn(ttlMs);
        when(cycles.gracePeriodMs()).thenReturn(gracePeriodMs);
        when(cycles.overagePolicy()).thenReturn(overagePolicy);
        when(cycles.dryRun()).thenReturn(dryRun);
        when(cycles.dimensions()).thenReturn(new String[0]);
        when(cycles.actionTags()).thenReturn(new String[0]);

        // Resolver returns the value as-is for standard fields
        when(resolver.resolve("tenant", tenant)).thenReturn(tenant);
        when(resolver.resolve("workspace", "")).thenReturn("");
        when(resolver.resolve("app", "")).thenReturn("");
        when(resolver.resolve("workflow", "")).thenReturn("");
        when(resolver.resolve("agent", "")).thenReturn("");
        when(resolver.resolve("toolset", "")).thenReturn("");

        return cycles;
    }

    // ========================================================================
    // buildReservation
    // ========================================================================

    @Nested
    @DisplayName("buildReservation")
    class BuildReservation {

        @Test
        void shouldBuildValidReservation() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000, "REJECT", false);

            Map<String, Object> body = service.buildReservation(cycles, 1000, "llm.completion", "gpt-4", null);

            assertThat(body.get("idempotency_key")).isNotNull();
            assertThat(body.get("subject")).isNotNull();
            assertThat(body.get("action")).isNotNull();
            assertThat(body.get("estimate")).isNotNull();
            assertThat(body.get("ttl_ms")).isEqualTo(60000L);
            assertThat(body.get("grace_period_ms")).isEqualTo(5000L);
            // REJECT is default, should not be in body
            assertThat(body).doesNotContainKey("overage_policy");
            assertThat(body).doesNotContainKey("dry_run");
        }

        @Test
        void shouldIncludeNonDefaultOveragePolicy() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000,
                    "ALLOW_WITH_OVERDRAFT", false);

            Map<String, Object> body = service.buildReservation(cycles, 1000, "llm.completion", "gpt-4", null);

            assertThat(body.get("overage_policy")).isEqualTo("ALLOW_WITH_OVERDRAFT");
        }

        @Test
        void shouldIncludeDryRunWhenTrue() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000, "REJECT", true);

            Map<String, Object> body = service.buildReservation(cycles, 1000, "llm.completion", "gpt-4", null);

            assertThat(body.get("dry_run")).isEqualTo(true);
        }

        @Test
        void shouldRejectMissingTenant() {
            Cycles cycles = mockCycles("", "TOKENS", 60000, 5000, "REJECT", false);
            when(resolver.resolve("tenant", "")).thenReturn("");

            assertThatThrownBy(() ->
                    service.buildReservation(cycles, 1000, "llm.completion", "gpt-4", null))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("tenant");
        }

        @Test
        void shouldRejectInvalidUnit() {
            Cycles cycles = mockCycles("test-tenant", "INVALID_UNIT", 60000, 5000, "REJECT", false);

            assertThatThrownBy(() ->
                    service.buildReservation(cycles, 1000, "llm.completion", "gpt-4", null))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("unit");
        }

        @Test
        void shouldRejectTtlMsBelowMinimum() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 500, 5000, "REJECT", false);

            assertThatThrownBy(() ->
                    service.buildReservation(cycles, 1000, "llm.completion", "gpt-4", null))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("ttl_ms");
        }

        @Test
        void shouldRejectTtlMsAboveMaximum() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 86400001, 5000, "REJECT", false);

            assertThatThrownBy(() ->
                    service.buildReservation(cycles, 1000, "llm.completion", "gpt-4", null))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("ttl_ms");
        }

        @Test
        void shouldRejectGracePeriodAboveMaximum() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 60001, "REJECT", false);

            assertThatThrownBy(() ->
                    service.buildReservation(cycles, 1000, "llm.completion", "gpt-4", null))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("grace_period_ms");
        }

        @Test
        void shouldRejectNegativeAmount() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000, "REJECT", false);

            assertThatThrownBy(() ->
                    service.buildReservation(cycles, -1, "llm.completion", "gpt-4", null))
                    .isInstanceOf(CyclesProtocolException.class);
        }

        @Test
        void shouldRejectInvalidOveragePolicy() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000, "INVALID_POLICY", false);

            assertThatThrownBy(() ->
                    service.buildReservation(cycles, 1000, "llm.completion", "gpt-4", null))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("overage_policy");
        }

        @Test
        void shouldIncludeMetadata() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000, "REJECT", false);
            Map<String, Object> metadata = Map.of("session_id", "abc123");

            Map<String, Object> body = service.buildReservation(cycles, 1000, "llm.completion", "gpt-4", metadata);

            assertThat(body.get("metadata")).isEqualTo(metadata);
        }
    }

    // ========================================================================
    // buildCommit
    // ========================================================================

    @Nested
    @DisplayName("buildCommit")
    class BuildCommit {

        @Test
        void shouldBuildValidCommit() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000, "REJECT", false);

            Map<String, Object> body = service.buildCommit(cycles, 800, null, null);

            assertThat(body.get("idempotency_key")).isNotNull();
            assertThat(body.get("actual")).isNotNull();

            @SuppressWarnings("unchecked")
            Map<String, Object> actual = (Map<String, Object>) body.get("actual");
            assertThat(actual.get("unit")).isEqualTo("TOKENS");
            assertThat(actual.get("amount")).isEqualTo(800L);
        }

        @Test
        void shouldIncludeMetrics() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000, "REJECT", false);
            var metrics = new CyclesMetrics();
            metrics.setTokensInput(100);

            Map<String, Object> body = service.buildCommit(cycles, 800, metrics, null);

            assertThat(body).containsKey("metrics");
        }
    }

    // ========================================================================
    // buildRelease
    // ========================================================================

    @Nested
    @DisplayName("buildRelease")
    class BuildRelease {

        @Test
        void shouldBuildWithReason() {
            Map<String, Object> body = service.buildRelease("user_cancelled");

            assertThat(body.get("idempotency_key")).isNotNull();
            assertThat(body.get("reason")).isEqualTo("user_cancelled");
        }

        @Test
        void shouldOmitBlankReason() {
            Map<String, Object> body = service.buildRelease("");

            assertThat(body).doesNotContainKey("reason");
        }
    }

    // ========================================================================
    // buildExtend
    // ========================================================================

    @Nested
    @DisplayName("buildExtend")
    class BuildExtend {

        @Test
        void shouldBuildValidExtend() {
            Map<String, Object> body = service.buildExtend(30000, null);

            assertThat(body.get("idempotency_key")).isNotNull();
            assertThat(body.get("extend_by_ms")).isEqualTo(30000L);
        }

        @Test
        void shouldRejectExtendBelowMinimum() {
            assertThatThrownBy(() -> service.buildExtend(0, null))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("extend_by_ms");
        }

        @Test
        void shouldRejectExtendAboveMaximum() {
            assertThatThrownBy(() -> service.buildExtend(86400001, null))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("extend_by_ms");
        }
    }

    // ========================================================================
    // buildDecision
    // ========================================================================

    @Nested
    @DisplayName("buildDecision")
    class BuildDecision {

        @Test
        void shouldBuildValidDecision() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000, "REJECT", false);

            Map<String, Object> body = service.buildDecision(cycles, 1000, "llm.completion", "gpt-4", null);

            assertThat(body.get("idempotency_key")).isNotNull();
            assertThat(body.get("subject")).isNotNull();
            assertThat(body.get("action")).isNotNull();
            assertThat(body.get("estimate")).isNotNull();
            // Decision does not include ttl_ms, overage_policy, dry_run
            assertThat(body).doesNotContainKey("ttl_ms");
            assertThat(body).doesNotContainKey("overage_policy");
            assertThat(body).doesNotContainKey("dry_run");
        }
    }

    // ========================================================================
    // buildEvent
    // ========================================================================

    @Nested
    @DisplayName("buildEvent")
    class BuildEvent {

        @Test
        void shouldBuildValidEvent() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000, "REJECT", false);

            Map<String, Object> body = service.buildEvent(
                    cycles, 500, "tool.search", "web-search", null, 1700000000000L, null);

            assertThat(body.get("idempotency_key")).isNotNull();
            assertThat(body.get("subject")).isNotNull();
            assertThat(body.get("action")).isNotNull();
            assertThat(body.get("actual")).isNotNull();
            assertThat(body.get("client_time_ms")).isEqualTo(1700000000000L);
            // REJECT is default, should not be in body
            assertThat(body).doesNotContainKey("overage_policy");
        }

        @Test
        void shouldIncludeNonDefaultOveragePolicy() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000,
                    "ALLOW_IF_AVAILABLE", false);

            Map<String, Object> body = service.buildEvent(
                    cycles, 500, "tool.search", "web-search", null, null, null);

            assertThat(body.get("overage_policy")).isEqualTo("ALLOW_IF_AVAILABLE");
        }
    }

    // ========================================================================
    // parseDimensions
    // ========================================================================

    @Nested
    @DisplayName("parseDimensions")
    class ParseDimensions {

        @Test
        void shouldParseValidDimensions() {
            Map<String, String> result = CyclesRequestBuilderService.parseDimensions(
                    new String[]{"cost_center=eng", "project=alpha"});

            assertThat(result).containsEntry("cost_center", "eng");
            assertThat(result).containsEntry("project", "alpha");
        }

        @Test
        void shouldReturnEmptyForNull() {
            assertThat(CyclesRequestBuilderService.parseDimensions(null)).isEmpty();
            assertThat(CyclesRequestBuilderService.parseDimensions(new String[0])).isEmpty();
        }

        @Test
        void shouldRejectInvalidFormat() {
            assertThatThrownBy(() ->
                    CyclesRequestBuilderService.parseDimensions(new String[]{"no_equals_sign"}))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("Invalid dimension");
        }
    }
}
