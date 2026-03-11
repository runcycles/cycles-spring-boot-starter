package io.runcycles.client.java.spring.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Typed request DTO for the POST /v1/reservations/{id}/commit endpoint.
 * Mirrors the server's {@code CommitRequest} schema.
 */
public class CommitRequest {
    private final String idempotencyKey;
    private final Amount actual;
    private final CyclesMetrics metrics;
    private final Map<String, Object> metadata;

    private CommitRequest(Builder builder) {
        this.idempotencyKey = builder.idempotencyKey;
        this.actual = builder.actual;
        this.metrics = builder.metrics;
        this.metadata = builder.metadata;
    }

    /**
     * Returns the idempotency key.
     *
     * @return The idempotency key
     */
    public String getIdempotencyKey() { return idempotencyKey; }
    /**
     * Returns the actual usage amount.
     *
     * @return The actual usage amount
     */
    public Amount getActual() { return actual; }
    /**
     * Returns the metrics to record with this commit.
     *
     * @return The metrics to record with this commit
     */
    public CyclesMetrics getMetrics() { return metrics; }
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
        if (actual != null) map.put("actual", actual.toMap());
        if (metrics != null && !metrics.isEmpty()) map.put("metrics", metrics.toMap());
        if (metadata != null && !metadata.isEmpty()) map.put("metadata", metadata);
        return map;
    }

    /**
     * Creates a new {@code CommitRequest} builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() { return new Builder(); }

    /** Builder for constructing {@link CommitRequest} instances. */
    public static class Builder {
        private String idempotencyKey;
        private Amount actual;
        private CyclesMetrics metrics;
        private Map<String, Object> metadata;

        /** Creates a new, empty builder. */
        Builder() {}

        /**
         * Sets the idempotency key.
         *
         * @param idempotencyKey the idempotency key
         * @return this builder
         */
        public Builder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }

        /**
         * Sets the actual usage amount.
         *
         * @param actual the actual amount consumed
         * @return this builder
         */
        public Builder actual(Amount actual) { this.actual = actual; return this; }

        /**
         * Sets the metrics to record with this commit.
         *
         * @param metrics the metrics
         * @return this builder
         */
        public Builder metrics(CyclesMetrics metrics) { this.metrics = metrics; return this; }

        /**
         * Sets the custom metadata.
         *
         * @param metadata the metadata map
         * @return this builder
         */
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        /**
         * Builds a new {@link CommitRequest} from this builder's state.
         *
         * @return the constructed request
         */
        public CommitRequest build() { return new CommitRequest(this); }
    }

    @Override
    public String toString() {
        return "CommitRequest{idempotencyKey='" + idempotencyKey + '\'' +
                ", actual=" + actual + ", metrics=" + metrics +
                ", metadata=" + metadata + '}';
    }
}
