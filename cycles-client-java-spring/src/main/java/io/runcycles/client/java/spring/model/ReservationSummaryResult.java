package io.runcycles.client.java.spring.model;

import java.util.List;
import java.util.Map;

/**
 * Typed result for reservation entries in {@code GET /v1/reservations} list responses.
 * Mirrors the server's {@code ReservationSummary} schema (subset of ReservationDetail,
 * without committed, finalized_at_ms, or metadata).
 */
public class ReservationSummaryResult {
    private final String reservationId;
    private final ReservationStatus status;
    private final String idempotencyKey;
    private final Subject subject;
    private final Action action;
    private final Amount reserved;
    private final Long createdAtMs;
    private final Long expiresAtMs;
    private final String scopePath;
    private final List<String> affectedScopes;

    private ReservationSummaryResult(String reservationId, ReservationStatus status, String idempotencyKey,
                                     Subject subject, Action action, Amount reserved,
                                     Long createdAtMs, Long expiresAtMs,
                                     String scopePath, List<String> affectedScopes) {
        this.reservationId = reservationId;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.subject = subject;
        this.action = action;
        this.reserved = reserved;
        this.createdAtMs = createdAtMs;
        this.expiresAtMs = expiresAtMs;
        this.scopePath = scopePath;
        this.affectedScopes = affectedScopes;
    }

    /**
     * Deserializes a {@code ReservationSummaryResult} from a raw API response map.
     *
     * @param map the response body map, or {@code null}
     * @return the parsed result, or {@code null} if the input is {@code null}
     */
    @SuppressWarnings("unchecked")
    public static ReservationSummaryResult fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new ReservationSummaryResult(
                map.get("reservation_id") instanceof String s ? s : null,
                ReservationStatus.fromString(map.get("status") instanceof String s ? s : null),
                map.get("idempotency_key") instanceof String s ? s : null,
                map.get("subject") instanceof Map<?, ?> m ? Subject.fromMap((Map<String, Object>) m) : null,
                map.get("action") instanceof Map<?, ?> m ? Action.fromMap((Map<String, Object>) m) : null,
                map.get("reserved") instanceof Map<?, ?> m ? Amount.fromMap((Map<String, Object>) m) : null,
                map.get("created_at_ms") instanceof Number n ? n.longValue() : null,
                map.get("expires_at_ms") instanceof Number n ? n.longValue() : null,
                map.get("scope_path") instanceof String s ? s : null,
                map.get("affected_scopes") instanceof List<?> l ? (List<String>) l : List.of()
        );
    }

    /**
     * Returns the reservation ID.
     *
     * @return The reservation id
     */
    public String getReservationId() { return reservationId; }
    /**
     * Returns the reservation lifecycle status.
     *
     * @return The reservation lifecycle status
     */
    public ReservationStatus getStatus() { return status; }
    /**
     * Returns the idempotency key.
     *
     * @return The idempotency key
     */
    public String getIdempotencyKey() { return idempotencyKey; }
    /**
     * Returns the subject that owns this reservation.
     *
     * @return The subject that owns this reservation
     */
    public Subject getSubject() { return subject; }
    /**
     * Returns the action being budgeted.
     *
     * @return The action being budgeted
     */
    public Action getAction() { return action; }
    /**
     * Returns the reserved amount.
     *
     * @return The reserved amount
     */
    public Amount getReserved() { return reserved; }
    /**
     * Returns the creation time in epoch milliseconds.
     *
     * @return The creation time in epoch milliseconds
     */
    public Long getCreatedAtMs() { return createdAtMs; }
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
     * Returns the list of affected budget scopes.
     *
     * @return The list of affected budget scopes
     */
    public List<String> getAffectedScopes() { return affectedScopes; }

    /**
     * Returns {@code true} if the reservation status is {@code ACTIVE}.
     *
     * @return {@code true} if the reservation status is {@code active}
     */
    public boolean isActive() { return status == ReservationStatus.ACTIVE; }
    /**
     * Returns {@code true} if the reservation status is {@code COMMITTED}.
     *
     * @return {@code true} if the reservation status is {@code committed}
     */
    public boolean isCommitted() { return status == ReservationStatus.COMMITTED; }
    /**
     * Returns {@code true} if the reservation status is {@code RELEASED}.
     *
     * @return {@code true} if the reservation status is {@code released}
     */
    public boolean isReleased() { return status == ReservationStatus.RELEASED; }
    /**
     * Returns {@code true} if the reservation status is {@code EXPIRED}.
     *
     * @return {@code true} if the reservation status is {@code expired}
     */
    public boolean isExpired() { return status == ReservationStatus.EXPIRED; }

    @Override
    public String toString() {
        return "ReservationSummaryResult{reservationId='" + reservationId + '\'' +
                ", status=" + status +
                ", scopePath='" + scopePath + '\'' +
                ", expiresAtMs=" + expiresAtMs + '}';
    }
}
