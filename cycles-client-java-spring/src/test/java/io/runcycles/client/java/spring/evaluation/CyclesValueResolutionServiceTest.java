package io.runcycles.client.java.spring.evaluation;

import io.runcycles.client.java.spring.config.CyclesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CyclesValueResolutionService")
class CyclesValueResolutionServiceTest {

    private CyclesProperties properties;
    private Map<String, CyclesFieldResolver> resolvers;

    @BeforeEach
    void setUp() {
        properties = new CyclesProperties();
        resolvers = new HashMap<>();
    }

    private CyclesValueResolutionService createService() {
        return new CyclesValueResolutionService(resolvers, properties);
    }

    // ========================================================================
    // Priority: annotation -> config -> resolver
    // ========================================================================

    @Nested
    @DisplayName("Resolution priority")
    class ResolutionPriority {

        @Test
        void shouldReturnAnnotationValueFirst() {
            properties.setTenant("config-tenant");
            resolvers.put("tenant", () -> "resolver-tenant");

            CyclesValueResolutionService service = createService();
            String result = service.resolve("tenant", "annotation-tenant");

            assertThat(result).isEqualTo("annotation-tenant");
        }

        @Test
        void shouldFallBackToConfigWhenAnnotationBlank() {
            properties.setTenant("config-tenant");
            resolvers.put("tenant", () -> "resolver-tenant");

            CyclesValueResolutionService service = createService();
            String result = service.resolve("tenant", "");

            assertThat(result).isEqualTo("config-tenant");
        }

        @Test
        void shouldFallBackToResolverWhenBothBlank() {
            resolvers.put("tenant", () -> "resolver-tenant");

            CyclesValueResolutionService service = createService();
            String result = service.resolve("tenant", "");

            assertThat(result).isEqualTo("resolver-tenant");
        }

        @Test
        void shouldReturnNullWhenAllEmpty() {
            CyclesValueResolutionService service = createService();
            String result = service.resolve("tenant", "");

            assertThat(result).isNull();
        }

        @Test
        void shouldReturnNullForAnnotationNull() {
            CyclesValueResolutionService service = createService();
            String result = service.resolve("tenant", null);

            assertThat(result).isNull();
        }
    }

    // ========================================================================
    // Config field mapping
    // ========================================================================

    @Nested
    @DisplayName("Config field mapping")
    class ConfigFieldMapping {

        @Test
        void shouldResolveTenantFromConfig() {
            properties.setTenant("t1");
            CyclesValueResolutionService service = createService();
            assertThat(service.resolve("tenant", "")).isEqualTo("t1");
        }

        @Test
        void shouldResolveWorkspaceFromConfig() {
            properties.setWorkspace("ws1");
            CyclesValueResolutionService service = createService();
            assertThat(service.resolve("workspace", "")).isEqualTo("ws1");
        }

        @Test
        void shouldResolveAppFromConfig() {
            properties.setApp("app1");
            CyclesValueResolutionService service = createService();
            assertThat(service.resolve("app", "")).isEqualTo("app1");
        }

        @Test
        void shouldResolveWorkflowFromConfig() {
            properties.setWorkflow("wf1");
            CyclesValueResolutionService service = createService();
            assertThat(service.resolve("workflow", "")).isEqualTo("wf1");
        }

        @Test
        void shouldResolveAgentFromConfig() {
            properties.setAgent("agent1");
            CyclesValueResolutionService service = createService();
            assertThat(service.resolve("agent", "")).isEqualTo("agent1");
        }

        @Test
        void shouldResolveToolsetFromConfig() {
            properties.setToolset("tools1");
            CyclesValueResolutionService service = createService();
            assertThat(service.resolve("toolset", "")).isEqualTo("tools1");
        }

        @Test
        void shouldReturnNullForUnknownField() {
            CyclesValueResolutionService service = createService();
            assertThat(service.resolve("unknown_field", "")).isNull();
        }
    }

    // ========================================================================
    // Resolver integration
    // ========================================================================

    @Nested
    @DisplayName("Resolver integration")
    class ResolverIntegration {

        @Test
        void shouldCallResolverDynamically() {
            // Resolver returns different values on successive calls
            var counter = new int[]{0};
            resolvers.put("tenant", () -> "dynamic-" + (++counter[0]));

            CyclesValueResolutionService service = createService();
            assertThat(service.resolve("tenant", "")).isEqualTo("dynamic-1");
            assertThat(service.resolve("tenant", "")).isEqualTo("dynamic-2");
        }

        @Test
        void shouldIgnoreResolverWhenAnnotationProvided() {
            var called = new boolean[]{false};
            resolvers.put("tenant", () -> { called[0] = true; return "resolver-val"; });

            CyclesValueResolutionService service = createService();
            service.resolve("tenant", "annotation-val");

            assertThat(called[0]).isFalse();
        }
    }
}
