package io.runcycles.client.java.spring.autoconfigure;

import io.runcycles.client.java.spring.aspect.CyclesAspect;
import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.client.DefaultCyclesClient;
import io.runcycles.client.java.spring.context.CyclesLifecycleService;
import io.runcycles.client.java.spring.context.CyclesRequestBuilderService;
import io.runcycles.client.java.spring.evaluation.CyclesExpressionEvaluator;
import io.runcycles.client.java.spring.evaluation.CyclesValueResolutionService;
import io.runcycles.client.java.spring.retry.CommitRetryEngine;
import io.runcycles.client.java.spring.retry.InMemoryCommitRetryEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CyclesAutoConfiguration")
class CyclesAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CyclesAutoConfiguration.class));

    // ========================================================================
    // Bean creation
    // ========================================================================

    @Nested
    @DisplayName("Bean creation")
    class BeanCreation {

        @Test
        void shouldCreateAllBeans() {
            contextRunner
                    .withPropertyValues(
                            "cycles.base-url=http://localhost:7878",
                            "cycles.api-key=test-key"
                    )
                    .run(context -> {
                        assertThat(context).hasSingleBean(CyclesClient.class);
                        assertThat(context).hasSingleBean(DefaultCyclesClient.class);
                        assertThat(context).hasSingleBean(CyclesExpressionEvaluator.class);
                        assertThat(context).hasSingleBean(CyclesValueResolutionService.class);
                        assertThat(context).hasSingleBean(CyclesRequestBuilderService.class);
                        assertThat(context).hasSingleBean(CommitRetryEngine.class);
                        assertThat(context).hasSingleBean(InMemoryCommitRetryEngine.class);
                        assertThat(context).hasSingleBean(CyclesLifecycleService.class);
                        assertThat(context).hasSingleBean(CyclesAspect.class);
                    });
        }
    }

    // ========================================================================
    // Validation
    // ========================================================================

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        void shouldFailOnMissingBaseUrl() {
            contextRunner
                    .withPropertyValues("cycles.api-key=test-key")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("base-url");
                    });
        }

        @Test
        void shouldFailOnMissingApiKey() {
            contextRunner
                    .withPropertyValues("cycles.base-url=http://localhost:7878")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("api-key");
                    });
        }

        @Test
        void shouldFailOnBlankBaseUrl() {
            contextRunner
                    .withPropertyValues(
                            "cycles.base-url=  ",
                            "cycles.api-key=test-key"
                    )
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("base-url");
                    });
        }
    }

    // ========================================================================
    // ConditionalOnMissingBean
    // ========================================================================

    @Nested
    @DisplayName("ConditionalOnMissingBean")
    class ConditionalBeans {

        @Test
        void shouldAllowCustomCyclesClientOverride() {
            contextRunner
                    .withPropertyValues(
                            "cycles.base-url=http://localhost:7878",
                            "cycles.api-key=test-key"
                    )
                    .withBean("customCyclesClient", CyclesClient.class, () -> new CyclesClient() {
                        @Override public io.runcycles.client.java.spring.model.CyclesResponse<java.util.Map<String, Object>> createReservation(Object body) { return null; }
                        @Override public io.runcycles.client.java.spring.model.CyclesResponse<java.util.Map<String, Object>> commitReservation(String id, Object body) { return null; }
                        @Override public io.runcycles.client.java.spring.model.CyclesResponse<java.util.Map<String, Object>> releaseReservation(String id, Object body) { return null; }
                        @Override public io.runcycles.client.java.spring.model.CyclesResponse<java.util.Map<String, Object>> extendReservation(String id, Object body) { return null; }
                        @Override public io.runcycles.client.java.spring.model.CyclesResponse<java.util.Map<String, Object>> decide(Object body) { return null; }
                        @Override public io.runcycles.client.java.spring.model.CyclesResponse<java.util.Map<String, Object>> listReservations(java.util.Map<String, String> q) { return null; }
                        @Override public io.runcycles.client.java.spring.model.CyclesResponse<java.util.Map<String, Object>> getReservation(String id) { return null; }
                        @Override public io.runcycles.client.java.spring.model.CyclesResponse<java.util.Map<String, Object>> getBalances(java.util.Map<String, String> q) { return null; }
                        @Override public io.runcycles.client.java.spring.model.CyclesResponse<java.util.Map<String, Object>> createEvent(Object body) { return null; }
                    })
                    .run(context -> {
                        assertThat(context).hasSingleBean(CyclesClient.class);
                        // Should not be DefaultCyclesClient since we provided a custom one
                        assertThat(context).doesNotHaveBean(DefaultCyclesClient.class);
                    });
        }

        @Test
        void shouldFailOnBlankApiKey() {
            contextRunner
                    .withPropertyValues(
                            "cycles.base-url=http://localhost:7878",
                            "cycles.api-key=  "
                    )
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure())
                                .rootCause()
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("api-key");
                    });
        }

        @Test
        void shouldAllowCustomCommitRetryEngineOverride() {
            contextRunner
                    .withPropertyValues(
                            "cycles.base-url=http://localhost:7878",
                            "cycles.api-key=test-key"
                    )
                    .withBean(CommitRetryEngine.class, () -> (reservationId, body) -> { /* no-op */ })
                    .run(context -> {
                        assertThat(context).hasSingleBean(CommitRetryEngine.class);
                        assertThat(context).doesNotHaveBean(InMemoryCommitRetryEngine.class);
                    });
        }

        @Test
        void shouldAllowCustomExpressionEvaluatorOverride() {
            contextRunner
                    .withPropertyValues(
                            "cycles.base-url=http://localhost:7878",
                            "cycles.api-key=test-key"
                    )
                    .withBean(CyclesExpressionEvaluator.class, CyclesExpressionEvaluator::new)
                    .run(context -> {
                        assertThat(context).hasSingleBean(CyclesExpressionEvaluator.class);
                    });
        }

        @Test
        void shouldAllowCustomCyclesAspectOverride() {
            contextRunner
                    .withPropertyValues(
                            "cycles.base-url=http://localhost:7878",
                            "cycles.api-key=test-key"
                    )
                    .withBean(CyclesAspect.class, () -> new CyclesAspect(
                            new CyclesLifecycleService(
                                    new DefaultCyclesClient(org.springframework.web.reactive.function.client.WebClient.create()),
                                    (id, body) -> {},
                                    new CyclesRequestBuilderService(new CyclesValueResolutionService(java.util.Map.of(), new io.runcycles.client.java.spring.config.CyclesProperties())),
                                    new CyclesExpressionEvaluator()
                            )
                    ))
                    .run(context -> {
                        assertThat(context).hasSingleBean(CyclesAspect.class);
                    });
        }

        @Test
        void shouldAllowCustomLifecycleServiceOverride() {
            contextRunner
                    .withPropertyValues(
                            "cycles.base-url=http://localhost:7878",
                            "cycles.api-key=test-key"
                    )
                    .withBean(CyclesLifecycleService.class, () -> {
                        return new CyclesLifecycleService(
                                new DefaultCyclesClient(org.springframework.web.reactive.function.client.WebClient.create()),
                                (id, body) -> {},
                                new CyclesRequestBuilderService(new CyclesValueResolutionService(java.util.Map.of(), new io.runcycles.client.java.spring.config.CyclesProperties())),
                                new CyclesExpressionEvaluator()
                        );
                    })
                    .run(context -> {
                        assertThat(context).hasSingleBean(CyclesLifecycleService.class);
                    });
        }
    }
}
