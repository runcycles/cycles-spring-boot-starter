package io.runcycles.client.java.spring.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Typed request DTO for the POST /v1/reservations endpoint.
 * Mirrors the server's {@code ReservationCreateRequest} schema.
 */
public class ReservationCreateRequest {
    private final String idempotencyKey;
    private final Subject subject;
    private final Action action;
    private final Amount estimate;
    private final Long ttlMs;
    private final Long gracePeriodMs;
    private final CommitOveragePolicy overagePolicy;
    private final Boolean dryRun;
    private final Map<String, Object> metadata;

    private ReservationCreateRequest(Builder builder) {
        this.idempotencyKey = builder.idempotencyKey;
        this.subject = builder.subject;
        this.action = builder.action;
        this.estimate = builder.estimate;
        this.ttlMs = builder.ttlMs;
        this.gracePeriodMs = builder.gracePeriodMs;
        this.overagePolicy = builder.overagePolicy;
        this.dryRun = builder.dryRun;
        this.metadata = builder.metadata;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public Subject getSubject() { return subject; }
    public Action getAction() { return action; }
    public Amount getEstimate() { return estimate; }
    public Long getTtlMs() { return ttlMs; }
    public Long getGracePeriodMs() { return gracePeriodMs; }
    public CommitOveragePolicy getOveragePolicy() { return overagePolicy; }
    public Boolean getDryRun() { return dryRun; }
    public Map<String, Object> getMetadata() { return metadata; }

    /**
     * Serializes this request to a map suitable for the API request body.
     *
     * @return a mutable map of non-null fields
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (idempotencyKey != null) map.put("idempotency_key", idempotencyKey);
        if (subject != null) map.put("subject", subject.toMap());
        if (action != null) map.put("action", action.toMap());
        if (estimate != null) map.put("estimate", estimate.toMap());
        if (ttlMs != null) map.put("ttl_ms", ttlMs);
        if (gracePeriodMs != null) map.put("grace_period_ms", gracePeriodMs);
        if (overagePolicy != null) map.put("overage_policy", overagePolicy.name());
        if (dryRun != null) map.put("dry_run", dryRun);
        if (metadata != null && !metadata.isEmpty()) map.put("metadata", metadata);
        return map;
    }

    /** Creates a new {@code ReservationCreateRequest} builder. */
    public static Builder builder() { return new Builder(); }

    /** Builder for constructing {@link ReservationCreateRequest} instances. */
    public static class Builder {
        private String idempotencyKey;
        private Subject subject;
        private Action action;
        private Amount estimate;
        private Long ttlMs = 60000L;
        private Long gracePeriodMs = 5000L;
        private CommitOveragePolicy overagePolicy = CommitOveragePolicy.REJECT;
        private Boolean dryRun = false;
        private Map<String, Object> metadata;

        public Builder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public Builder subject(Subject subject) { this.subject = subject; return this; }
        public Builder action(Action action) { this.action = action; return this; }
        public Builder estimate(Amount estimate) { this.estimate = estimate; return this; }
        public Builder ttlMs(Long ttlMs) { this.ttlMs = ttlMs; return this; }
        public Builder gracePeriodMs(Long gracePeriodMs) { this.gracePeriodMs = gracePeriodMs; return this; }
        public Builder overagePolicy(CommitOveragePolicy overagePolicy) { this.overagePolicy = overagePolicy; return this; }
        public Builder dryRun(Boolean dryRun) { this.dryRun = dryRun; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public ReservationCreateRequest build() { return new ReservationCreateRequest(this); }
    }

    @Override
    public String toString() {
        return "ReservationCreateRequest{idempotencyKey='" + idempotencyKey + '\'' +
                ", subject=" + subject + ", action=" + action +
                ", estimate=" + estimate + ", ttlMs=" + ttlMs +
                ", gracePeriodMs=" + gracePeriodMs + ", overagePolicy=" + overagePolicy +
                ", dryRun=" + dryRun + ", metadata=" + metadata + '}';
    }
}
