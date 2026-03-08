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

    public String getIdempotencyKey() { return idempotencyKey; }
    public Amount getActual() { return actual; }
    public CyclesMetrics getMetrics() { return metrics; }
    public Map<String, Object> getMetadata() { return metadata; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (idempotencyKey != null) map.put("idempotency_key", idempotencyKey);
        if (actual != null) map.put("actual", actual.toMap());
        if (metrics != null && !metrics.isEmpty()) map.put("metrics", metrics.toMap());
        if (metadata != null && !metadata.isEmpty()) map.put("metadata", metadata);
        return map;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String idempotencyKey;
        private Amount actual;
        private CyclesMetrics metrics;
        private Map<String, Object> metadata;

        public Builder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public Builder actual(Amount actual) { this.actual = actual; return this; }
        public Builder metrics(CyclesMetrics metrics) { this.metrics = metrics; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public CommitRequest build() { return new CommitRequest(this); }
    }

    @Override
    public String toString() {
        return "CommitRequest{idempotencyKey='" + idempotencyKey + '\'' +
                ", actual=" + actual + ", metrics=" + metrics +
                ", metadata=" + metadata + '}';
    }
}
