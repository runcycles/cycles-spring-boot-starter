package io.runcycles.client.java.spring.model;

import java.util.List;
import java.util.Map;

/**
 * Typed result for {@code GET /v1/reservations/{reservation_id}}.
 * Matches server's ReservationDetail: all ReservationSummary fields + committed, finalized_at_ms, metadata.
 */
public class ReservationDetailResult {
    private final String reservationId;
    private final String status;
    private final String idempotencyKey;
    private final Map<String, Object> subject;
    private final Map<String, Object> action;
    private final Map<String, Object> reserved;
    private final Map<String, Object> committed;
    private final Long createdAtMs;
    private final Long expiresAtMs;
    private final Long finalizedAtMs;
    private final String scopePath;
    private final List<String> affectedScopes;
    private final Map<String, Object> metadata;

    private ReservationDetailResult(String reservationId, String status, String idempotencyKey,
                                    Map<String, Object> subject, Map<String, Object> action,
                                    Map<String, Object> reserved, Map<String, Object> committed,
                                    Long createdAtMs, Long expiresAtMs, Long finalizedAtMs,
                                    String scopePath, List<String> affectedScopes,
                                    Map<String, Object> metadata) {
        this.reservationId = reservationId;
        this.status = status;
        this.idempotencyKey = idempotencyKey;
        this.subject = subject;
        this.action = action;
        this.reserved = reserved;
        this.committed = committed;
        this.createdAtMs = createdAtMs;
        this.expiresAtMs = expiresAtMs;
        this.finalizedAtMs = finalizedAtMs;
        this.scopePath = scopePath;
        this.affectedScopes = affectedScopes;
        this.metadata = metadata;
    }

    @SuppressWarnings("unchecked")
    public static ReservationDetailResult fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new ReservationDetailResult(
                map.get("reservation_id") instanceof String s ? s : null,
                map.get("status") instanceof String s ? s : null,
                map.get("idempotency_key") instanceof String s ? s : null,
                map.get("subject") instanceof Map<?, ?> m ? (Map<String, Object>) m : null,
                map.get("action") instanceof Map<?, ?> m ? (Map<String, Object>) m : null,
                map.get("reserved") instanceof Map<?, ?> m ? (Map<String, Object>) m : null,
                map.get("committed") instanceof Map<?, ?> m ? (Map<String, Object>) m : null,
                map.get("created_at_ms") instanceof Number n ? n.longValue() : null,
                map.get("expires_at_ms") instanceof Number n ? n.longValue() : null,
                map.get("finalized_at_ms") instanceof Number n ? n.longValue() : null,
                map.get("scope_path") instanceof String s ? s : null,
                map.get("affected_scopes") instanceof List<?> l ? (List<String>) l : List.of(),
                map.get("metadata") instanceof Map<?, ?> m ? (Map<String, Object>) m : null
        );
    }

    public String getReservationId() { return reservationId; }
    public String getStatus() { return status; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Map<String, Object> getSubject() { return subject; }
    public Map<String, Object> getAction() { return action; }
    public Map<String, Object> getReserved() { return reserved; }
    public Map<String, Object> getCommitted() { return committed; }
    public Long getCreatedAtMs() { return createdAtMs; }
    public Long getExpiresAtMs() { return expiresAtMs; }
    public Long getFinalizedAtMs() { return finalizedAtMs; }
    public String getScopePath() { return scopePath; }
    public List<String> getAffectedScopes() { return affectedScopes; }
    public Map<String, Object> getMetadata() { return metadata; }

    public boolean isActive() { return "ACTIVE".equals(status); }
    public boolean isCommitted() { return "COMMITTED".equals(status); }
    public boolean isReleased() { return "RELEASED".equals(status); }
    public boolean isExpired() { return "EXPIRED".equals(status); }

    @Override
    public String toString() {
        return "ReservationDetailResult{reservationId='" + reservationId + '\'' +
                ", status='" + status + '\'' +
                ", scopePath='" + scopePath + '\'' +
                ", expiresAtMs=" + expiresAtMs + '}';
    }
}
