package io.runcycles.client.java.spring.model;

import java.util.List;
import java.util.Map;

/**
 * Typed result for {@code POST /v1/reservations}.
 * Mirrors the server's {@code ReservationCreateResponse}.
 */
public class ReservationResult {
    private final Decision decision;
    private final String reservationId;
    private final List<String> affectedScopes;
    private final Long expiresAtMs;
    private final String scopePath;
    private final Amount reserved;
    private final Caps caps;
    private final String reasonCode;
    private final Long retryAfterMs;
    private final List<Balance> balances;

    private ReservationResult(Decision decision, String reservationId,
                              List<String> affectedScopes, Long expiresAtMs,
                              String scopePath, Amount reserved, Caps caps,
                              String reasonCode, Long retryAfterMs,
                              List<Balance> balances) {
        this.decision = decision;
        this.reservationId = reservationId;
        this.affectedScopes = affectedScopes;
        this.expiresAtMs = expiresAtMs;
        this.scopePath = scopePath;
        this.reserved = reserved;
        this.caps = caps;
        this.reasonCode = reasonCode;
        this.retryAfterMs = retryAfterMs;
        this.balances = balances;
    }

    @SuppressWarnings("unchecked")
    public static ReservationResult fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new ReservationResult(
                Decision.fromString(map.get("decision") instanceof String s ? s : null),
                map.get("reservation_id") instanceof String s ? s : null,
                map.get("affected_scopes") instanceof List<?> l ? (List<String>) l : List.of(),
                map.get("expires_at_ms") instanceof Number n ? n.longValue() : null,
                map.get("scope_path") instanceof String s ? s : null,
                map.get("reserved") instanceof Map<?, ?> m ? Amount.fromMap((Map<String, Object>) m) : null,
                Caps.fromMap(map.get("caps") instanceof Map<?, ?> m ? (Map<String, Object>) m : null),
                map.get("reason_code") instanceof String s ? s : null,
                map.get("retry_after_ms") instanceof Number n ? n.longValue() : null,
                Balance.listFromRaw(map.get("balances") instanceof List<?> l ? l : null)
        );
    }

    public Decision getDecision() { return decision; }
    public String getReservationId() { return reservationId; }
    public List<String> getAffectedScopes() { return affectedScopes; }
    public Long getExpiresAtMs() { return expiresAtMs; }
    public String getScopePath() { return scopePath; }
    public Amount getReserved() { return reserved; }
    public Caps getCaps() { return caps; }
    public String getReasonCode() { return reasonCode; }
    public Long getRetryAfterMs() { return retryAfterMs; }
    public List<Balance> getBalances() { return balances; }

    public boolean isAllowed() { return decision == Decision.ALLOW || decision == Decision.ALLOW_WITH_CAPS; }
    public boolean isDenied() { return decision == Decision.DENY; }

    @Override
    public String toString() {
        return "ReservationResult{decision=" + decision +
                ", reservationId='" + reservationId + '\'' +
                ", scopePath='" + scopePath + '\'' +
                ", expiresAtMs=" + expiresAtMs + '}';
    }
}
