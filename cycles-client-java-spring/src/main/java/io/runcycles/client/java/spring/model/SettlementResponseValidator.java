package io.runcycles.client.java.spring.model;

import java.math.BigInteger;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strict terminal-success validation for spend settlement responses.
 *
 * <p>A malformed or wrong-status 2xx is ambiguous and must be retried with the
 * original idempotency key; permissive DTO mapping is not sufficient proof that
 * the server applied settlement.
 */
public final class SettlementResponseValidator {

    private static final Set<String> UNITS = Set.of(
            "USD_MICROCENTS", "TOKENS", "CREDITS", "RISK_POINTS");
    private static final Set<String> COMMIT_FIELDS = Set.of(
            "status", "charged", "released", "balances", "cycles_evidence");
    private static final Set<String> EVENT_FIELDS = Set.of(
            "status", "event_id", "charged", "balances");
    private static final Set<String> AMOUNT_FIELDS = Set.of("unit", "amount");
    private static final Set<String> BALANCE_FIELDS = Set.of(
            "scope", "scope_path", "remaining", "reserved", "spent", "allocated",
            "debt", "overdraft_limit", "is_over_limit");
    private static final Set<String> EVIDENCE_FIELDS = Set.of(
            "evidence_id", "cycles_evidence_url");

    private SettlementResponseValidator() {
    }

    public static boolean isCommitSuccess(CyclesResponse<Map<String, Object>> response) {
        Map<String, Object> body = response.getBody();
        return response.getStatus() == 200
                && body != null
                && exactKeys(body, Set.of("status", "charged"), COMMIT_FIELDS)
                && "COMMITTED".equals(body.get("status"))
                && isAmount(body.get("charged"), false)
                && optionalAmount(body, "released")
                && optionalBalances(body, "balances")
                && optionalEvidence(body, "cycles_evidence");
    }

    public static boolean isEventSuccess(CyclesResponse<Map<String, Object>> response) {
        Map<String, Object> body = response.getBody();
        return response.getStatus() == 201
                && body != null
                && exactKeys(body, Set.of("status", "event_id"), EVENT_FIELDS)
                && "APPLIED".equals(body.get("status"))
                && body.get("event_id") instanceof String id
                && optionalAmount(body, "charged")
                && optionalBalances(body, "balances");
    }

    private static boolean exactKeys(Map<?, ?> value, Set<String> required, Set<String> allowed) {
        return value.keySet().containsAll(required)
                && value.keySet().stream().allMatch(key -> key instanceof String && allowed.contains(key));
    }

    private static boolean optionalAmount(Map<String, Object> body, String key) {
        return !body.containsKey(key) || isAmount(body.get(key), false);
    }

    private static boolean isAmount(Object value, boolean signed) {
        if (!(value instanceof Map<?, ?> amount)
                || !exactKeys(amount, Set.of("unit", "amount"), AMOUNT_FIELDS)
                || !(amount.get("unit") instanceof String unit)
                || !UNITS.contains(unit)
                || !isIntegralLong(amount.get("amount"))) {
            return false;
        }
        return signed || ((Number) amount.get("amount")).longValue() >= 0;
    }

    private static boolean isIntegralLong(Object value) {
        if (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long) {
            return true;
        }
        return value instanceof BigInteger integer && integer.bitLength() <= 63;
    }

    private static boolean optionalBalances(Map<String, Object> body, String key) {
        if (!body.containsKey(key)) {
            return true;
        }
        Object value = body.get(key);
        if (!(value instanceof List<?> balances)) {
            return false;
        }
        return balances.stream().allMatch(SettlementResponseValidator::isBalance);
    }

    private static boolean optionalEvidence(Map<String, Object> body, String key) {
        if (!body.containsKey(key)) {
            return true;
        }
        Object value = body.get(key);
        if (!(value instanceof Map<?, ?> evidence)
                || !exactKeys(evidence, EVIDENCE_FIELDS, EVIDENCE_FIELDS)
                || !(evidence.get("evidence_id") instanceof String id)
                || !id.matches("^[0-9a-f]{64}$")
                || !(evidence.get("cycles_evidence_url") instanceof String url)) {
            return false;
        }
        try {
            return new URI(url).isAbsolute();
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static boolean isBalance(Object value) {
        if (!(value instanceof Map<?, ?> balance)
                || !exactKeys(balance, Set.of("scope", "scope_path", "remaining"), BALANCE_FIELDS)
                || !(balance.get("scope") instanceof String)
                || !(balance.get("scope_path") instanceof String)
                || !isAmount(balance.get("remaining"), true)) {
            return false;
        }
        for (String key : List.of(
                "reserved", "spent", "allocated", "debt", "overdraft_limit")) {
            if (balance.containsKey(key) && !isAmount(balance.get(key), false)) {
                return false;
            }
        }
        return !balance.containsKey("is_over_limit")
                || balance.get("is_over_limit") instanceof Boolean;
    }
}
