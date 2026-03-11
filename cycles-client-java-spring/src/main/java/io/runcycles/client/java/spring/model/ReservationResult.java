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
    private final Integer retryAfterMs;
    private final List<Balance> balances;

    private ReservationResult(Decision decision, String reservationId,
                              List<String> affectedScopes, Long expiresAtMs,
                              String scopePath, Amount reserved, Caps caps,
                              String reasonCode, Integer retryAfterMs,
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

    /**
     * Deserializes a {@code ReservationResult} from a raw API response map.
     *
     * @param map the response body map, or {@code null}
     * @return the parsed result, or {@code null} if the input is {@code null}
     */
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
                map.get("retry_after_ms") instanceof Number n ? n.intValue() : null,
                Balance.listFromRaw(map.get("balances") instanceof List<?> l ? l : null)
        );
    }

    /**
     * Returns the server's decision.
     *
     * @return The server's decision
     */
    public Decision getDecision() { return decision; }
    /**
     * Returns the server-assigned reservation ID.
     *
     * @return The server-assigned reservation id
     */
    public String getReservationId() { return reservationId; }
    /**
     * Returns the list of affected budget scopes.
     *
     * @return The list of affected budget scopes
     */
    public List<String> getAffectedScopes() { return affectedScopes; }
    /**
     * Returns the expiration time in epoch milliseconds.
     *
     * @return The expiration time in epoch milliseconds
     */
    public Long getExpiresAtMs() { return expiresAtMs; }
    /**
     * Returns the fully-qualified scope path.
     *
     * @return The fully-qualified scope path
     */
    public String getScopePath() { return scopePath; }
    /**
     * Returns the reserved amount.
     *
     * @return The reserved amount
     */
    public Amount getReserved() { return reserved; }
    /**
     * Returns the capability constraints, or {@code null} if none.
     *
     * @return The capability constraints, or {@code null} if none
     */
    public Caps getCaps() { return caps; }
    /**
     * Returns the reason code for the decision, or {@code null}.
     *
     * @return The reason code for the decision, or {@code null}
     */
    public String getReasonCode() { return reasonCode; }
    /**
     * Returns the suggested retry delay in milliseconds, or {@code null}.
     *
     * @return The suggested retry delay in milliseconds, or {@code null}
     */
    public Integer getRetryAfterMs() { return retryAfterMs; }
    /**
     * Returns the updated balances after the reservation.
     *
     * @return The updated balances after the reservation
     */
    public List<Balance> getBalances() { return balances; }

    /**
     * Returns {@code true} if the decision is {@code ALLOW} or {@code ALLOW_WITH_CAPS}.
     *
     * @return {@code true} if the decision is {@code allow} or {@code allow_with_caps}
     */
    public boolean isAllowed() { return decision == Decision.ALLOW || decision == Decision.ALLOW_WITH_CAPS; }
    /**
     * Returns {@code true} if the decision is {@code DENY}.
     *
     * @return {@code true} if the decision is {@code deny}
     */
    public boolean isDenied() { return decision == Decision.DENY; }

    @Override
    public String toString() {
        return "ReservationResult{decision=" + decision +
                ", reservationId='" + reservationId + '\'' +
                ", scopePath='" + scopePath + '\'' +
                ", expiresAtMs=" + expiresAtMs + '}';
    }
}
