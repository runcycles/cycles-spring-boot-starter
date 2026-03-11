package io.runcycles.client.java.spring.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Typed request DTO for the POST /v1/decide endpoint.
 * Mirrors the server's {@code DecisionRequest} schema.
 */
public class DecisionRequest {
    private final String idempotencyKey;
    private final Subject subject;
    private final Action action;
    private final Amount estimate;
    private final Map<String, Object> metadata;

    private DecisionRequest(Builder builder) {
        this.idempotencyKey = builder.idempotencyKey;
        this.subject = builder.subject;
        this.action = builder.action;
        this.estimate = builder.estimate;
        this.metadata = builder.metadata;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public Subject getSubject() { return subject; }
    public Action getAction() { return action; }
    public Amount getEstimate() { return estimate; }
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
        if (metadata != null && !metadata.isEmpty()) map.put("metadata", metadata);
        return map;
    }

    /** Creates a new {@code DecisionRequest} builder. */
    public static Builder builder() { return new Builder(); }

    /** Builder for constructing {@link DecisionRequest} instances. */
    public static class Builder {
        private String idempotencyKey;
        private Subject subject;
        private Action action;
        private Amount estimate;
        private Map<String, Object> metadata;

        public Builder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public Builder subject(Subject subject) { this.subject = subject; return this; }
        public Builder action(Action action) { this.action = action; return this; }
        public Builder estimate(Amount estimate) { this.estimate = estimate; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public DecisionRequest build() { return new DecisionRequest(this); }
    }

    @Override
    public String toString() {
        return "DecisionRequest{idempotencyKey='" + idempotencyKey + '\'' +
                ", subject=" + subject + ", action=" + action +
                ", estimate=" + estimate + ", metadata=" + metadata + '}';
    }
}
