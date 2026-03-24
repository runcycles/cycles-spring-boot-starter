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

    /**
     * Returns the idempotency key.
     *
     * @return The idempotency key
     */
    public String getIdempotencyKey() { return idempotencyKey; }
    /**
     * Returns the subject.
     *
     * @return The subject
     */
    public Subject getSubject() { return subject; }
    /**
     * Returns the action.
     *
     * @return The action
     */
    public Action getAction() { return action; }
    /**
     * Returns the estimated amount.
     *
     * @return The estimated amount
     */
    public Amount getEstimate() { return estimate; }
    /**
     * Returns the TTL in milliseconds.
     *
     * @return The ttl in milliseconds
     */
    public Long getTtlMs() { return ttlMs; }
    /**
     * Returns the grace period in milliseconds.
     *
     * @return The grace period in milliseconds
     */
    public Long getGracePeriodMs() { return gracePeriodMs; }
    /**
     * Returns the overage policy.
     *
     * @return The overage policy
     */
    public CommitOveragePolicy getOveragePolicy() { return overagePolicy; }
    /**
     * Returns whether this is a dry-run request.
     *
     * @return Whether this is a dry-run request
     */
    public Boolean getDryRun() { return dryRun; }
    /**
     * Returns the custom metadata.
     *
     * @return The custom metadata
     */
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

    /**
     * Creates a new {@code ReservationCreateRequest} builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() { return new Builder(); }

    /** Builder for constructing {@link ReservationCreateRequest} instances. */
    public static class Builder {
        private String idempotencyKey;
        private Subject subject;
        private Action action;
        private Amount estimate;
        private Long ttlMs = 60000L;
        private Long gracePeriodMs = 5000L;
        private CommitOveragePolicy overagePolicy = CommitOveragePolicy.ALLOW_IF_AVAILABLE;
        private Boolean dryRun = false;
        private Map<String, Object> metadata;

        /** Creates a new builder with default values. */
        Builder() {}

        /**
         * Sets the idempotency key.
         *
         * @param idempotencyKey the idempotency key
         * @return this builder
         */
        public Builder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }

        /**
         * Sets the subject.
         *
         * @param subject the subject identifying the entity
         * @return this builder
         */
        public Builder subject(Subject subject) { this.subject = subject; return this; }

        /**
         * Sets the action.
         *
         * @param action the action being performed
         * @return this builder
         */
        public Builder action(Action action) { this.action = action; return this; }

        /**
         * Sets the estimated amount.
         *
         * @param estimate the estimated budget amount
         * @return this builder
         */
        public Builder estimate(Amount estimate) { this.estimate = estimate; return this; }

        /**
         * Sets the TTL in milliseconds.
         *
         * @param ttlMs the time-to-live in milliseconds
         * @return this builder
         */
        public Builder ttlMs(Long ttlMs) { this.ttlMs = ttlMs; return this; }

        /**
         * Sets the grace period in milliseconds.
         *
         * @param gracePeriodMs the grace period in milliseconds
         * @return this builder
         */
        public Builder gracePeriodMs(Long gracePeriodMs) { this.gracePeriodMs = gracePeriodMs; return this; }

        /**
         * Sets the overage policy.
         *
         * @param overagePolicy the commit overage policy
         * @return this builder
         */
        public Builder overagePolicy(CommitOveragePolicy overagePolicy) { this.overagePolicy = overagePolicy; return this; }

        /**
         * Sets whether this is a dry-run request.
         *
         * @param dryRun {@code true} to simulate without persisting
         * @return this builder
         */
        public Builder dryRun(Boolean dryRun) { this.dryRun = dryRun; return this; }

        /**
         * Sets the custom metadata.
         *
         * @param metadata the metadata map
         * @return this builder
         */
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        /**
         * Builds a new {@link ReservationCreateRequest} from this builder's state.
         *
         * @return the constructed request
         */
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
