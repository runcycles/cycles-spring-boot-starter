package io.runcycles.client.java.spring.model;

import java.util.List;
import java.util.Map;

/**
 * Typed result for {@code POST /v1/reservations/{id}/release}.
 * Mirrors the server's {@code ReleaseResponse}.
 * Spec constrains status to enum {@code [RELEASED]}.
 */
public class ReleaseResult {
    private final ReleaseStatus status;
    private final Amount released;
    private final List<Balance> balances;

    private ReleaseResult(ReleaseStatus status, Amount released, List<Balance> balances) {
        this.status = status;
        this.released = released;
        this.balances = balances;
    }

    /**
     * Deserializes a {@code ReleaseResult} from a raw API response map.
     *
     * @param map the response body map, or {@code null}
     * @return the parsed result, or {@code null} if the input is {@code null}
     */
    @SuppressWarnings("unchecked")
    public static ReleaseResult fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new ReleaseResult(
                ReleaseStatus.fromString(map.get("status") instanceof String s ? s : null),
                map.get("released") instanceof Map<?, ?> m ? Amount.fromMap((Map<String, Object>) m) : null,
                Balance.listFromRaw(map.get("balances") instanceof List<?> l ? l : null)
        );
    }

    /** Returns the release status. */
    public ReleaseStatus getStatus() { return status; }
    /** Returns the amount released back to budget. */
    public Amount getReleased() { return released; }
    /** Returns the updated balances after the release. */
    public List<Balance> getBalances() { return balances; }

    @Override
    public String toString() {
        return "ReleaseResult{status=" + status +
                ", released=" + released + '}';
    }
}
