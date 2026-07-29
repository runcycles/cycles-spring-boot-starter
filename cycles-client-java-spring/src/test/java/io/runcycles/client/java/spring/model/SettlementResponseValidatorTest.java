package io.runcycles.client.java.spring.model;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementResponseValidatorTest {

    @Test
    void commitAcceptsEvidenceAndRejectsInvalidOptionalValuesAndUnits() {
        Map<String, Object> body = new HashMap<>();
        body.put("status", "COMMITTED");
        body.put("charged", Map.of("unit", "USD_MICROCENTS", "amount", 1));
        body.put("cycles_evidence", Map.of(
                "evidence_id", "a".repeat(64),
                "cycles_evidence_url", "https://cycles.example/v1/evidence/id"));

        assertThat(SettlementResponseValidator.isCommitSuccess(
                CyclesResponse.success(200, body))).isTrue();

        body.put("balances", null);
        assertThat(SettlementResponseValidator.isCommitSuccess(
                CyclesResponse.success(200, body))).isFalse();

        body.remove("balances");
        body.put("charged", Map.of("unit", "FUTURE_UNIT", "amount", 1));
        assertThat(SettlementResponseValidator.isCommitSuccess(
                CyclesResponse.success(200, body))).isFalse();
    }

    @Test
    void eventFollowsExactWireSchema() {
        assertThat(SettlementResponseValidator.isEventSuccess(
                CyclesResponse.success(201, Map.of(
                        "status", "APPLIED", "event_id", "")))).isTrue();

        Map<String, Object> body = new HashMap<>();
        body.put("status", "APPLIED");
        body.put("event_id", "event-1");
        body.put("charged", null);
        assertThat(SettlementResponseValidator.isEventSuccess(
                CyclesResponse.success(201, body))).isFalse();
    }
}
