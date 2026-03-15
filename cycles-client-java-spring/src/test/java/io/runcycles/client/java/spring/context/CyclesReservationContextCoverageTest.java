package io.runcycles.client.java.spring.context;

import io.runcycles.client.java.spring.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CyclesReservationContext coverage")
class CyclesReservationContextCoverageTest {

    @Test
    void isExpiringSoonWhenNullExpiry() {
        var ctx = new CyclesReservationContext("r1", 100, Decision.ALLOW, null,
                null, List.of(), "path", null, null);
        assertThat(ctx.isExpiringSoon(5000)).isFalse();
    }

    @Test
    void isExpiringSoonWhenFarInFuture() {
        var ctx = new CyclesReservationContext("r1", 100, Decision.ALLOW, null,
                System.currentTimeMillis() + 60000, List.of(), "path", null, null);
        assertThat(ctx.isExpiringSoon(5000)).isFalse();
    }

    @Test
    void isExpiringSoonWhenAboutToExpire() {
        var ctx = new CyclesReservationContext("r1", 100, Decision.ALLOW, null,
                System.currentTimeMillis() + 1000, List.of(), "path", null, null);
        assertThat(ctx.isExpiringSoon(5000)).isTrue();
    }

    @Test
    void updateExpiresAtMs() {
        var ctx = new CyclesReservationContext("r1", 100, Decision.ALLOW, null,
                1000L, List.of(), "path", null, null);
        ctx.updateExpiresAtMs(2000L);
        assertThat(ctx.getExpiresAtMs()).isEqualTo(2000L);
    }

    @Test
    void metricsAndMetadata() {
        var ctx = new CyclesReservationContext("r1", 100, Decision.ALLOW, null,
                null, List.of(), "path", null, null);

        assertThat(ctx.getMetrics()).isNull();
        assertThat(ctx.getCommitMetadata()).isNull();

        var metrics = new CyclesMetrics();
        metrics.setTokensInput(10);
        ctx.setMetrics(metrics);
        assertThat(ctx.getMetrics()).isSameAs(metrics);

        Map<String, Object> meta = Map.of("k", "v");
        ctx.setCommitMetadata(meta);
        assertThat(ctx.getCommitMetadata()).isSameAs(meta);
    }

    @Test
    void hasCaps() {
        var withCaps = new CyclesReservationContext("r1", 100, Decision.ALLOW_WITH_CAPS,
                Caps.fromMap(Map.of("max_tokens", 500)), null, List.of(), "path", null, null);
        assertThat(withCaps.hasCaps()).isTrue();

        var noCaps = new CyclesReservationContext("r1", 100, Decision.ALLOW,
                null, null, List.of(), "path", null, null);
        assertThat(noCaps.hasCaps()).isFalse();
    }

    @Test
    void allGetters() {
        Amount reserved = new Amount(Unit.TOKENS, 1000L);
        Balance balance = Balance.fromMap(Map.of("scope", "t1", "scope_path", "tenant:t1"));
        var ctx = new CyclesReservationContext("r1", 500, Decision.ALLOW, null,
                99999L, List.of("scope1"), "path1", reserved, List.of(balance));

        assertThat(ctx.getReservationId()).isEqualTo("r1");
        assertThat(ctx.getEstimate()).isEqualTo(500);
        assertThat(ctx.getDecision()).isEqualTo(Decision.ALLOW);
        assertThat(ctx.getCaps()).isNull();
        assertThat(ctx.getExpiresAtMs()).isEqualTo(99999L);
        assertThat(ctx.getAffectedScopes()).containsExactly("scope1");
        assertThat(ctx.getScopePath()).isEqualTo("path1");
        assertThat(ctx.getReserved()).isSameAs(reserved);
        assertThat(ctx.getBalances()).hasSize(1);
    }
}
