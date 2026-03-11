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

    public String getReservationId() { return reservationId; }
    public ReservationStatus getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Subject getSubject() { return subject; }
    public Action getAction() { return action; }
    public Amount getReserved() { return reserved; }
    public Long getCreatedAtMs() { return createdAtMs; }
    public Long getExpiresAtMs() { return expiresAtMs; }
    public String getScopePath() { return scopePath; }
    public List<String> getAffectedScopes() { return affectedScopes; }

    /** Returns {@code true} if the reservation status is {@code ACTIVE}. */
    public boolean isActive() { return status == ReservationStatus.ACTIVE; }
    /** Returns {@code true} if the reservation status is {@code COMMITTED}. */
    public boolean isCommitted() { return status == ReservationStatus.COMMITTED; }
    /** Returns {@code true} if the reservation status is {@code RELEASED}. */
    public boolean isReleased() { return status == ReservationStatus.RELEASED; }
    /** Returns {@code true} if the reservation status is {@code EXPIRED}. */
    public boolean isExpired() { return status == ReservationStatus.EXPIRED; }

    @Override
    public String toString() {
        return "ReservationSummaryResult{reservationId='" + reservationId + '\'' +
                ", status=" + status +
                ", scopePath='" + scopePath + '\'' +
                ", expiresAtMs=" + expiresAtMs + '}';
    }
}
