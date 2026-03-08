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

    public String getIdempotencyKey() { return idempotencyKey; }
    public Subject getSubject() { return subject; }
    public Action getAction() { return action; }
    public Amount getActual() { return actual; }
    public CommitOveragePolicy getOveragePolicy() { return overagePolicy; }
    public CyclesMetrics getMetrics() { return metrics; }
    public Long getClientTimeMs() { return clientTimeMs; }
    public Map<String, Object> getMetadata() { return metadata; }

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

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String idempotencyKey;
        private Subject subject;
        private Action action;
        private Amount actual;
        private CommitOveragePolicy overagePolicy = CommitOveragePolicy.REJECT;
        private CyclesMetrics metrics;
        private Long clientTimeMs;
        private Map<String, Object> metadata;

        public Builder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public Builder subject(Subject subject) { this.subject = subject; return this; }
        public Builder action(Action action) { this.action = action; return this; }
        public Builder actual(Amount actual) { this.actual = actual; return this; }
        public Builder overagePolicy(CommitOveragePolicy overagePolicy) { this.overagePolicy = overagePolicy; return this; }
        public Builder metrics(CyclesMetrics metrics) { this.metrics = metrics; return this; }
        public Builder clientTimeMs(Long clientTimeMs) { this.clientTimeMs = clientTimeMs; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
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
