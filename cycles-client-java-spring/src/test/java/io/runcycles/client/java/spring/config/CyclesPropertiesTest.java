package io.runcycles.client.java.spring.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CyclesProperties")
class CyclesPropertiesTest {

    @Nested
    @DisplayName("Default values")
    class DefaultValues {

        @Test
        void httpDefaults() {
            var props = new CyclesProperties();
            assertThat(props.getHttp().getConnectTimeout()).isEqualTo(Duration.ofSeconds(2));
            assertThat(props.getHttp().getReadTimeout()).isEqualTo(Duration.ofSeconds(5));
        }

        @Test
        void retryDefaults() {
            var props = new CyclesProperties();
            CyclesProperties.Retry retry = props.getRetry();
            assertThat(retry.isEnabled()).isTrue();
            assertThat(retry.getMaxAttempts()).isEqualTo(5);
            assertThat(retry.getInitialDelay()).isEqualTo(Duration.ofMillis(500));
            assertThat(retry.getMultiplier()).isEqualTo(2.0);
            assertThat(retry.getMaxDelay()).isEqualTo(Duration.ofSeconds(30));
        }

        @Test
        void protocolFieldsAreNullByDefault() {
            var props = new CyclesProperties();
            assertThat(props.getBaseUrl()).isNull();
            assertThat(props.getApiKey()).isNull();
            assertThat(props.getTenant()).isNull();
            assertThat(props.getWorkspace()).isNull();
            assertThat(props.getApp()).isNull();
            assertThat(props.getWorkflow()).isNull();
            assertThat(props.getAgent()).isNull();
            assertThat(props.getToolset()).isNull();
        }
    }

    @Nested
    @DisplayName("Setters")
    class Setters {

        @Test
        void httpSetters() {
            var props = new CyclesProperties();
            props.getHttp().setConnectTimeout(Duration.ofSeconds(10));
            props.getHttp().setReadTimeout(Duration.ofSeconds(30));

            assertThat(props.getHttp().getConnectTimeout()).isEqualTo(Duration.ofSeconds(10));
            assertThat(props.getHttp().getReadTimeout()).isEqualTo(Duration.ofSeconds(30));
        }

        @Test
        void retrySetters() {
            var props = new CyclesProperties();
            CyclesProperties.Retry retry = props.getRetry();
            retry.setEnabled(false);
            retry.setMaxAttempts(10);
            retry.setInitialDelay(Duration.ofMillis(100));
            retry.setMultiplier(3.0);
            retry.setMaxDelay(Duration.ofMinutes(1));

            assertThat(retry.isEnabled()).isFalse();
            assertThat(retry.getMaxAttempts()).isEqualTo(10);
            assertThat(retry.getInitialDelay()).isEqualTo(Duration.ofMillis(100));
            assertThat(retry.getMultiplier()).isEqualTo(3.0);
            assertThat(retry.getMaxDelay()).isEqualTo(Duration.ofMinutes(1));
        }

        @Test
        void protocolFieldSetters() {
            var props = new CyclesProperties();
            props.setBaseUrl("http://localhost:7878");
            props.setApiKey("key-123");
            props.setTenant("my-tenant");
            props.setWorkspace("my-ws");
            props.setApp("my-app");
            props.setWorkflow("my-wf");
            props.setAgent("my-agent");
            props.setToolset("my-toolset");

            assertThat(props.getBaseUrl()).isEqualTo("http://localhost:7878");
            assertThat(props.getApiKey()).isEqualTo("key-123");
            assertThat(props.getTenant()).isEqualTo("my-tenant");
            assertThat(props.getWorkspace()).isEqualTo("my-ws");
            assertThat(props.getApp()).isEqualTo("my-app");
            assertThat(props.getWorkflow()).isEqualTo("my-wf");
            assertThat(props.getAgent()).isEqualTo("my-agent");
            assertThat(props.getToolset()).isEqualTo("my-toolset");
        }
    }
}
