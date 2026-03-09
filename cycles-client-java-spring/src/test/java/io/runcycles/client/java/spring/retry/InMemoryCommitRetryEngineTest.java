package io.runcycles.client.java.spring.retry;

import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.config.CyclesProperties;
import io.runcycles.client.java.spring.model.CyclesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("InMemoryCommitRetryEngine")
class InMemoryCommitRetryEngineTest {

    private CyclesClient client;
    private CyclesProperties properties;

    @BeforeEach
    void setUp() {
        client = mock(CyclesClient.class);
        properties = new CyclesProperties();
        // Use short delays for tests
        CyclesProperties.Retry retry = properties.getRetry();
        retry.setEnabled(true);
        retry.setMaxAttempts(3);
        retry.setInitialDelay(Duration.ofMillis(50));
        retry.setMultiplier(2.0);
        retry.setMaxDelay(Duration.ofMillis(500));
    }

    // ========================================================================
    // Disabled retry
    // ========================================================================

    @Nested
    @DisplayName("Disabled retry")
    class DisabledRetry {

        @Test
        void shouldNotRetryWhenDisabled() throws Exception {
            properties.getRetry().setEnabled(false);
            InMemoryCommitRetryEngine engine = new InMemoryCommitRetryEngine(client, properties);

            engine.schedule("res-1", Map.of("key", "value"));

            Thread.sleep(200);
            verify(client, never()).commitReservation(anyString(), any(Object.class));
        }
    }

    // ========================================================================
    // Successful retry
    // ========================================================================

    @Nested
    @DisplayName("Successful retry")
    class SuccessfulRetry {

        @Test
        void shouldSucceedOnFirstRetry() {
            when(client.commitReservation(eq("res-1"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "COMMITTED")));

            InMemoryCommitRetryEngine engine = new InMemoryCommitRetryEngine(client, properties);
            engine.schedule("res-1", Map.of("idempotency_key", "com-1"));

            await().atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(1)).commitReservation(eq("res-1"), any(Object.class)));
        }
    }

    // ========================================================================
    // Retryable errors
    // ========================================================================

    @Nested
    @DisplayName("Retryable errors")
    class RetryableErrors {

        @Test
        void shouldRetryOnTransportError() {
            when(client.commitReservation(eq("res-1"), any(Object.class)))
                    .thenReturn(CyclesResponse.transportError(new RuntimeException("connection reset")))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "COMMITTED")));

            InMemoryCommitRetryEngine engine = new InMemoryCommitRetryEngine(client, properties);
            engine.schedule("res-1", Map.of("idempotency_key", "com-1"));

            await().atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(2)).commitReservation(eq("res-1"), any(Object.class)));
        }

        @Test
        void shouldRetryOn5xx() {
            when(client.commitReservation(eq("res-1"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(500, "Internal error", Map.of()))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "COMMITTED")));

            InMemoryCommitRetryEngine engine = new InMemoryCommitRetryEngine(client, properties);
            engine.schedule("res-1", Map.of("idempotency_key", "com-1"));

            await().atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(2)).commitReservation(eq("res-1"), any(Object.class)));
        }

        @Test
        void shouldRetryOnException() {
            when(client.commitReservation(eq("res-1"), any(Object.class)))
                    .thenThrow(new RuntimeException("unexpected"))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "COMMITTED")));

            InMemoryCommitRetryEngine engine = new InMemoryCommitRetryEngine(client, properties);
            engine.schedule("res-1", Map.of("idempotency_key", "com-1"));

            await().atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(2)).commitReservation(eq("res-1"), any(Object.class)));
        }
    }

    // ========================================================================
    // Non-retryable errors
    // ========================================================================

    @Nested
    @DisplayName("Non-retryable errors")
    class NonRetryableErrors {

        @Test
        void shouldStopOnNonRetryable4xx() throws Exception {
            when(client.commitReservation(eq("res-1"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(400, "Bad request", Map.of()));

            InMemoryCommitRetryEngine engine = new InMemoryCommitRetryEngine(client, properties);
            engine.schedule("res-1", Map.of("idempotency_key", "com-1"));

            // Wait enough time for retries to fire if they were going to
            Thread.sleep(500);
            verify(client, times(1)).commitReservation(eq("res-1"), any(Object.class));
        }
    }

    // ========================================================================
    // Max attempts
    // ========================================================================

    @Nested
    @DisplayName("Max attempts")
    class MaxAttempts {

        @Test
        void shouldExhaustAllAttempts() {
            when(client.commitReservation(eq("res-1"), any(Object.class)))
                    .thenReturn(CyclesResponse.transportError(new RuntimeException("always fail")));

            InMemoryCommitRetryEngine engine = new InMemoryCommitRetryEngine(client, properties);
            engine.schedule("res-1", Map.of("idempotency_key", "com-1"));

            // maxAttempts=3, should try 3 times then stop
            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(3)).commitReservation(eq("res-1"), any(Object.class)));

            // Give more time to ensure no additional retries
            await().during(300, TimeUnit.MILLISECONDS)
                    .atMost(1, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(3)).commitReservation(eq("res-1"), any(Object.class)));
        }
    }

    // ========================================================================
    // Max delay capping
    // ========================================================================

    @Nested
    @DisplayName("Max delay capping")
    class MaxDelayCapping {

        @Test
        void shouldCapDelayAtMaxDelay() {
            // Configure so delay quickly exceeds maxDelay:
            // initialDelay=100ms, multiplier=10.0, maxDelay=200ms
            // Attempt 1: 100ms, Attempt 2: min(1000ms, 200ms) = 200ms, Attempt 3: min(2000ms, 200ms) = 200ms
            CyclesProperties.Retry retry = properties.getRetry();
            retry.setMaxAttempts(4);
            retry.setInitialDelay(Duration.ofMillis(100));
            retry.setMultiplier(10.0);
            retry.setMaxDelay(Duration.ofMillis(200));

            when(client.commitReservation(eq("res-1"), any(Object.class)))
                    .thenReturn(CyclesResponse.transportError(new RuntimeException("always fail")));

            InMemoryCommitRetryEngine engine = new InMemoryCommitRetryEngine(client, properties);
            engine.schedule("res-1", Map.of("idempotency_key", "com-1"));

            // All 4 attempts should complete within ~700ms (100 + 200 + 200 + 200)
            // Without capping, attempt 2 would be at 1000ms, attempt 3 at 10000ms
            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(4)).commitReservation(eq("res-1"), any(Object.class)));
        }
    }
}
