package io.runcycles.client.java.spring.context;

import io.runcycles.client.java.spring.model.Amount;
import io.runcycles.client.java.spring.model.CyclesMetrics;
import io.runcycles.client.java.spring.model.Decision;
import io.runcycles.client.java.spring.model.Unit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CyclesContextHolder")
class CyclesContextHolderTest {

    @AfterEach
    void tearDown() {
        CyclesContextHolder.clear();
    }

    @Nested
    @DisplayName("ThreadLocal lifecycle")
    class ThreadLocalLifecycle {

        @Test
        void shouldReturnNullWhenNotSet() {
            assertThat(CyclesContextHolder.get()).isNull();
        }

        @Test
        void shouldSetAndGet() {
            CyclesReservationContext ctx = new CyclesReservationContext(
                    "res-1", 1000, Decision.ALLOW, null, null, null, null, null, null);

            CyclesContextHolder.set(ctx);

            assertThat(CyclesContextHolder.get()).isSameAs(ctx);
        }

        @Test
        void shouldClear() {
            CyclesReservationContext ctx = new CyclesReservationContext(
                    "res-1", 1000, Decision.ALLOW, null, null, null, null, null, null);

            CyclesContextHolder.set(ctx);
            CyclesContextHolder.clear();

            assertThat(CyclesContextHolder.get()).isNull();
        }

        @Test
        void shouldIsolateAcrossThreads() throws Exception {
            CyclesReservationContext mainCtx = new CyclesReservationContext(
                    "res-main", 1000, Decision.ALLOW, null, null, null, null, null, null);
            CyclesContextHolder.set(mainCtx);

            AtomicReference<CyclesReservationContext> threadCtx = new AtomicReference<>();
            Thread thread = new Thread(() -> threadCtx.set(CyclesContextHolder.get()));
            thread.start();
            thread.join();

            assertThat(threadCtx.get()).isNull();
            assertThat(CyclesContextHolder.get()).isSameAs(mainCtx);
        }
    }

    @Nested
    @DisplayName("CyclesReservationContext")
    class ReservationContextTest {

        @Test
        void shouldExposeAllConstructorFields() {
            Amount reserved = new Amount(Unit.TOKENS, 1000L);
            CyclesReservationContext ctx = new CyclesReservationContext(
                    "res-1", 500, Decision.ALLOW_WITH_CAPS, null,
                    1700000060000L, List.of("tenant:t1"), "tenant:t1",
                    reserved, null);

            assertThat(ctx.getReservationId()).isEqualTo("res-1");
            assertThat(ctx.getEstimate()).isEqualTo(500);
            assertThat(ctx.getDecision()).isEqualTo(Decision.ALLOW_WITH_CAPS);
            assertThat(ctx.getExpiresAtMs()).isEqualTo(1700000060000L);
            assertThat(ctx.getAffectedScopes()).containsExactly("tenant:t1");
            assertThat(ctx.getScopePath()).isEqualTo("tenant:t1");
            assertThat(ctx.getReserved()).isSameAs(reserved);
        }

        @Test
        void shouldUpdateExpiresAtMs() {
            CyclesReservationContext ctx = new CyclesReservationContext(
                    "res-1", 1000, Decision.ALLOW, null, 1000L, null, null, null, null);

            ctx.updateExpiresAtMs(2000L);

            assertThat(ctx.getExpiresAtMs()).isEqualTo(2000L);
        }

        @Test
        void shouldReportHasCaps() {
            CyclesReservationContext withCaps = new CyclesReservationContext(
                    "res-1", 1000, Decision.ALLOW_WITH_CAPS,
                    io.runcycles.client.java.spring.model.Caps.fromMap(Map.of("max_tokens", 500)),
                    null, null, null, null, null);
            CyclesReservationContext withoutCaps = new CyclesReservationContext(
                    "res-2", 1000, Decision.ALLOW, null, null, null, null, null, null);

            assertThat(withCaps.hasCaps()).isTrue();
            assertThat(withoutCaps.hasCaps()).isFalse();
        }

        @Test
        void shouldSetAndGetMetrics() {
            CyclesReservationContext ctx = new CyclesReservationContext(
                    "res-1", 1000, Decision.ALLOW, null, null, null, null, null, null);

            assertThat(ctx.getMetrics()).isNull();

            CyclesMetrics metrics = new CyclesMetrics();
            metrics.setTokensInput(100);
            ctx.setMetrics(metrics);

            assertThat(ctx.getMetrics()).isSameAs(metrics);
            assertThat(ctx.getMetrics().getTokensInput()).isEqualTo(100);
        }

        @Test
        void shouldSetAndGetCommitMetadata() {
            CyclesReservationContext ctx = new CyclesReservationContext(
                    "res-1", 1000, Decision.ALLOW, null, null, null, null, null, null);

            assertThat(ctx.getCommitMetadata()).isNull();

            Map<String, Object> metadata = Map.of("session_id", "abc");
            ctx.setCommitMetadata(metadata);

            assertThat(ctx.getCommitMetadata()).isEqualTo(metadata);
        }

        @Test
        void shouldCheckIsExpiringSoon() {
            long futureMs = System.currentTimeMillis() + 5000;
            CyclesReservationContext ctx = new CyclesReservationContext(
                    "res-1", 1000, Decision.ALLOW, null, futureMs, null, null, null, null);

            // 10s threshold, 5s remaining -> expiring soon
            assertThat(ctx.isExpiringSoon(10000)).isTrue();
            // 1s threshold, 5s remaining -> not expiring soon
            assertThat(ctx.isExpiringSoon(1000)).isFalse();
        }

        @Test
        void shouldHandleNullExpiresAtMs() {
            CyclesReservationContext ctx = new CyclesReservationContext(
                    "res-1", 1000, Decision.ALLOW, null, null, null, null, null, null);

            assertThat(ctx.isExpiringSoon(10000)).isFalse();
        }
    }
}
