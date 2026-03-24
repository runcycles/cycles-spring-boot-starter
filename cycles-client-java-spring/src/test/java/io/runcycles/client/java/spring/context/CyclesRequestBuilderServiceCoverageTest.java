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

@DisplayName("CyclesRequestBuilderService coverage")
class CyclesRequestBuilderServiceCoverageTest {

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

        when(resolver.resolve("tenant", tenant)).thenReturn(tenant);
        when(resolver.resolve("workspace", "")).thenReturn("");
        when(resolver.resolve("app", "")).thenReturn("");
        when(resolver.resolve("workflow", "")).thenReturn("");
        when(resolver.resolve("agent", "")).thenReturn("");
        when(resolver.resolve("toolset", "")).thenReturn("");

        return cycles;
    }

    @Nested
    @DisplayName("buildReservation - ttlMs zero (skips ttl)")
    class TtlMsZero {
        @Test
        void shouldNotIncludeTtlWhenZero() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 0, 5000, "ALLOW_IF_AVAILABLE", false);
            Map<String, Object> body = service.buildReservation(cycles, 100, "llm", "gpt", null);
            assertThat(body).doesNotContainKey("ttl_ms");
        }
    }

    @Nested
    @DisplayName("buildReservation - gracePeriodMs negative (skips grace period)")
    class GracePeriodNegative {
        @Test
        void shouldNotIncludeGracePeriodWhenNegative() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, -1, "ALLOW_IF_AVAILABLE", false);
            Map<String, Object> body = service.buildReservation(cycles, 100, "llm", "gpt", null);
            assertThat(body).doesNotContainKey("grace_period_ms");
        }
    }

    @Nested
    @DisplayName("buildReservation - dimensions")
    class Dimensions {
        @Test
        void shouldIncludeDimensions() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000, "ALLOW_IF_AVAILABLE", false);
            when(cycles.dimensions()).thenReturn(new String[]{"region=us-east", "team=backend"});

            Map<String, Object> body = service.buildReservation(cycles, 100, "llm", "gpt", null);

            @SuppressWarnings("unchecked")
            Map<String, Object> subject = (Map<String, Object>) body.get("subject");
            assertThat(subject).containsKey("dimensions");
        }
    }

    @Nested
    @DisplayName("buildEvent - with metrics and metadata")
    class BuildEventMetrics {
        @Test
        void shouldIncludeMetricsAndMetadata() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000, "ALLOW_IF_AVAILABLE", false);
            var metrics = new CyclesMetrics();
            metrics.setTokensInput(10);
            Map<String, Object> metadata = Map.of("session", "abc");

            Map<String, Object> body = service.buildEvent(
                    cycles, 100, "tool", "search", metrics, null, metadata);

            assertThat(body).containsKey("metrics");
            assertThat(body).containsKey("metadata");
            assertThat(body).doesNotContainKey("client_time_ms");
        }
    }

    @Nested
    @DisplayName("buildDecision - with metadata")
    class BuildDecisionMetadata {
        @Test
        void shouldIncludeMetadata() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000, "ALLOW_IF_AVAILABLE", false);
            Map<String, Object> metadata = Map.of("session", "abc");

            Map<String, Object> body = service.buildDecision(
                    cycles, 100, "llm", "gpt", metadata);

            assertThat(body).containsKey("metadata");
        }
    }

    @Nested
    @DisplayName("buildCommit - with metadata")
    class BuildCommitMetadata {
        @Test
        void shouldIncludeMetadata() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000, "ALLOW_IF_AVAILABLE", false);
            Map<String, Object> metadata = Map.of("session", "abc");

            Map<String, Object> body = service.buildCommit(cycles, 100, null, metadata);

            assertThat(body).containsKey("metadata");
            assertThat(body).doesNotContainKey("metrics");
        }
    }

    @Nested
    @DisplayName("buildExtend - with metadata")
    class BuildExtendMetadata {
        @Test
        void shouldIncludeMetadata() {
            Map<String, Object> metadata = Map.of("reason", "long-running");
            Map<String, Object> body = service.buildExtend(30000, metadata);
            assertThat(body).containsKey("metadata");
        }
    }

    @Nested
    @DisplayName("Null annotation checks")
    class NullAnnotation {
        @Test
        void buildReservationRejectsNull() {
            assertThatThrownBy(() -> service.buildReservation(null, 100, "k", "n", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void buildCommitRejectsNull() {
            assertThatThrownBy(() -> service.buildCommit(null, 100, null, null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void buildDecisionRejectsNull() {
            assertThatThrownBy(() -> service.buildDecision(null, 100, "k", "n", null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void buildEventRejectsNull() {
            assertThatThrownBy(() -> service.buildEvent(null, 100, "k", "n", null, null, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Mandatory validation - missing actionKind/actionName")
    class MandatoryValidation {
        @Test
        void rejectBlankActionKind() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000, "ALLOW_IF_AVAILABLE", false);
            assertThatThrownBy(() -> service.buildReservation(cycles, 100, "", "gpt", null))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("actionKind");
        }

        @Test
        void rejectBlankActionName() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000, "ALLOW_IF_AVAILABLE", false);
            assertThatThrownBy(() -> service.buildReservation(cycles, 100, "llm", "", null))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("actionName");
        }

        @Test
        void rejectBlankUnit() {
            Cycles cycles = mockCycles("test-tenant", "", 60000, 5000, "ALLOW_IF_AVAILABLE", false);
            assertThatThrownBy(() -> service.buildReservation(cycles, 100, "llm", "gpt", null))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("unit");
        }
    }

    @Nested
    @DisplayName("buildEvent - negative actual")
    class BuildEventNegativeActual {
        @Test
        void shouldReject() {
            Cycles cycles = mockCycles("test-tenant", "TOKENS", 60000, 5000, "ALLOW_IF_AVAILABLE", false);
            assertThatThrownBy(() -> service.buildEvent(cycles, -1, "tool", "search", null, null, null))
                    .isInstanceOf(CyclesProtocolException.class);
        }
    }
}
