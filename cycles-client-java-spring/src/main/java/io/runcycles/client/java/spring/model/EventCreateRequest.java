package io.runcycles.client.java.spring.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Typed request DTO for the POST /v1/events endpoint.
 * Mirrors the server's {@code EventCreateRequest} schema.
 */
public class EventCreateRequest {
    private final String idempotencyKey;
    private final Subject subject;
    private final Action action;
    private final Amount actual;
    private final CommitOveragePolicy overagePolicy;
    private final CyclesMetrics metrics;
    private final Long clientTimeMs;
    private final Map<String, Object> metadata;

    private EventCreateRequest(Builder builder) {
        this.idempotencyKey = builder.idempotencyKey;
        this.subject = builder.subject;
        this.action = builder.action;
        this.actual = builder.actual;
        this.overagePolicy = builder.overagePolicy;
        this.metrics = builder.metrics;
        this.clientTimeMs = builder.clientTimeMs;
        this.metadata = builder.metadata;
    }

    /** Returns the idempotency key. */
    public String getIdempotencyKey() { return idempotencyKey; }
    /** Returns the subject. */
    public Subject getSubject() { return subject; }
    /** Returns the action. */
    public Action getAction() { return action; }
    /** Returns the actual usage amount. */
    public Amount getActual() { return actual; }
    /** Returns the overage policy. */
    public CommitOveragePolicy getOveragePolicy() { return overagePolicy; }
    /** Returns the metrics to record with this event. */
    public CyclesMetrics getMetrics() { return metrics; }
    /** Returns the client-side timestamp in epoch milliseconds. */
    public Long getClientTimeMs() { return clientTimeMs; }
    /** Returns the custom metadata. */
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
        if (actual != null) map.put("actual", actual.toMap());
        if (overagePolicy != null) map.put("overage_policy", overagePolicy.name());
        if (metrics != null && !metrics.isEmpty()) map.put("metrics", metrics.toMap());
        if (clientTimeMs != null) map.put("client_time_ms", clientTimeMs);
        if (metadata != null && !metadata.isEmpty()) map.put("metadata", metadata);
        return map;
    }

    /**
     * Creates a new {@code EventCreateRequest} builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() { return new Builder(); }

    /** Builder for constructing {@link EventCreateRequest} instances. */
    public static class Builder {
        private String idempotencyKey;
        private Subject subject;
        private Action action;
        private Amount actual;
        private CommitOveragePolicy overagePolicy = CommitOveragePolicy.REJECT;
        private CyclesMetrics metrics;
        private Long clientTimeMs;
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
         * Sets the actual usage amount.
         *
         * @param actual the actual amount consumed
         * @return this builder
         */
        public Builder actual(Amount actual) { this.actual = actual; return this; }

        /**
         * Sets the overage policy.
         *
         * @param overagePolicy the commit overage policy
         * @return this builder
         */
        public Builder overagePolicy(CommitOveragePolicy overagePolicy) { this.overagePolicy = overagePolicy; return this; }

        /**
         * Sets the metrics to record with this event.
         *
         * @param metrics the metrics
         * @return this builder
         */
        public Builder metrics(CyclesMetrics metrics) { this.metrics = metrics; return this; }

        /**
         * Sets the client-side timestamp.
         *
         * @param clientTimeMs the timestamp in epoch milliseconds
         * @return this builder
         */
        public Builder clientTimeMs(Long clientTimeMs) { this.clientTimeMs = clientTimeMs; return this; }

        /**
         * Sets the custom metadata.
         *
         * @param metadata the metadata map
         * @return this builder
         */
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        /**
         * Builds a new {@link EventCreateRequest} from this builder's state.
         *
         * @return the constructed request
         */
        public EventCreateRequest build() { return new EventCreateRequest(this); }
    }

    @Override
    public String toString() {
        return "EventCreateRequest{idempotencyKey='" + idempotencyKey + '\'' +
                ", subject=" + subject + ", action=" + action +
                ", actual=" + actual + ", overagePolicy=" + overagePolicy +
                ", metrics=" + metrics + ", clientTimeMs=" + clientTimeMs +
                ", metadata=" + metadata + '}';
    }
}
