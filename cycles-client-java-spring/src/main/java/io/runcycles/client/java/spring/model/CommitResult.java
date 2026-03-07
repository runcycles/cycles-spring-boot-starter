package io.runcycles.client.java.spring.model;

import java.util.List;
import java.util.Map;

/**
 * Typed result for {@code POST /v1/reservations/{id}/commit}.
 * Mirrors the server's {@code CommitResponse}.
 * Spec constrains status to enum {@code [COMMITTED]}.
 */
public class CommitResult {
    private final ReservationStatus status;
    private final Amount charged;
    private final Amount released;
    private final List<Balance> balances;

    private CommitResult(ReservationStatus status, Amount charged, Amount released, List<Balance> balances) {
        this.status = status;
        this.charged = charged;
        this.released = released;
        this.balances = balances;
    }

    @SuppressWarnings("unchecked")
    public static CommitResult fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new CommitResult(
                ReservationStatus.fromString(map.get("status") instanceof String s ? s : null),
                map.get("charged") instanceof Map<?, ?> m ? Amount.fromMap((Map<String, Object>) m) : null,
                map.get("released") instanceof Map<?, ?> m ? Amount.fromMap((Map<String, Object>) m) : null,
                Balance.listFromRaw(map.get("balances") instanceof List<?> l ? l : null)
        );
    }

    public ReservationStatus getStatus() { return status; }
    public Amount getCharged() { return charged; }
    public Amount getReleased() { return released; }
    public List<Balance> getBalances() { return balances; }

    @Override
    public String toString() {
        return "CommitResult{status=" + status +
                ", charged=" + charged +
                ", released=" + released + '}';
    }
}
