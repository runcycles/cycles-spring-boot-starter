package io.runcycles.client.java.spring.model;

import java.util.List;
import java.util.Map;

/**
 * Typed result for {@code POST /v1/reservations/{id}/release}.
 * Mirrors the server's {@code ReleaseResponse}.
 */
public class ReleaseResult {
    private final String status;
    private final Amount released;
    private final List<Balance> balances;

    private ReleaseResult(String status, Amount released, List<Balance> balances) {
        this.status = status;
        this.released = released;
        this.balances = balances;
    }

    @SuppressWarnings("unchecked")
    public static ReleaseResult fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new ReleaseResult(
                map.get("status") instanceof String s ? s : null,
                map.get("released") instanceof Map<?, ?> m ? Amount.fromMap((Map<String, Object>) m) : null,
                Balance.listFromRaw(map.get("balances") instanceof List<?> l ? l : null)
        );
    }

    public String getStatus() { return status; }
    public Amount getReleased() { return released; }
    public List<Balance> getBalances() { return balances; }

    @Override
    public String toString() {
        return "ReleaseResult{status='" + status + '\'' +
                ", released=" + released + '}';
    }
}
