package io.runcycles.client.java.spring.model;

import java.util.List;
import java.util.Map;

/**
 * Typed result for {@code POST /v1/reservations/{id}/extend}.
 * Mirrors the server's {@code ReservationExtendResponse}.
 * Spec constrains status to enum {@code [ACTIVE]}.
 */
public class ExtendResult {
    private final ReservationStatus status;
    private final Long expiresAtMs;
    private final List<Balance> balances;

    private ExtendResult(ReservationStatus status, Long expiresAtMs, List<Balance> balances) {
        this.status = status;
        this.expiresAtMs = expiresAtMs;
        this.balances = balances;
    }

    @SuppressWarnings("unchecked")
    public static ExtendResult fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new ExtendResult(
                ReservationStatus.fromString(map.get("status") instanceof String s ? s : null),
                map.get("expires_at_ms") instanceof Number n ? n.longValue() : null,
                Balance.listFromRaw(map.get("balances") instanceof List<?> l ? l : null)
        );
    }

    public ReservationStatus getStatus() { return status; }
    public Long getExpiresAtMs() { return expiresAtMs; }
    public List<Balance> getBalances() { return balances; }

    @Override
    public String toString() {
        return "ExtendResult{status=" + status +
                ", expiresAtMs=" + expiresAtMs + '}';
    }
}
