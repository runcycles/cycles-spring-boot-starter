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

    /** Returns the idempotency key. */
    public String getIdempotencyKey() { return idempotencyKey; }
    /** Returns the subject. */
    public Subject getSubject() { return subject; }
    /** Returns the action. */
    public Action getAction() { return action; }
    /** Returns the estimated amount. */
    public Amount getEstimate() { return estimate; }
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
        if (estimate != null) map.put("estimate", estimate.toMap());
        if (metadata != null && !metadata.isEmpty()) map.put("metadata", metadata);
        return map;
    }

    /**
     * Creates a new {@code DecisionRequest} builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() { return new Builder(); }

    /** Builder for constructing {@link DecisionRequest} instances. */
    public static class Builder {
        private String idempotencyKey;
        private Subject subject;
        private Action action;
        private Amount estimate;
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
         * Sets the custom metadata.
         *
         * @param metadata the metadata map
         * @return this builder
         */
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        /**
         * Builds a new {@link DecisionRequest} from this builder's state.
         *
         * @return the constructed request
         */
        public DecisionRequest build() { return new DecisionRequest(this); }
    }

    @Override
    public String toString() {
        return "DecisionRequest{idempotencyKey='" + idempotencyKey + '\'' +
                ", subject=" + subject + ", action=" + action +
                ", estimate=" + estimate + ", metadata=" + metadata + '}';
    }
}
