package io.runcycles.client.java.spring.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Typed request DTO for the POST /v1/reservations/{id}/extend endpoint.
 * Mirrors the server's {@code ReservationExtendRequest} schema.
 */
public class ReservationExtendRequest {
    private final String idempotencyKey;
    private final Long extendByMs;
    private final Map<String, Object> metadata;

    private ReservationExtendRequest(Builder builder) {
        this.idempotencyKey = builder.idempotencyKey;
        this.extendByMs = builder.extendByMs;
        this.metadata = builder.metadata;
    }

    /** Returns the idempotency key. */
    public String getIdempotencyKey() { return idempotencyKey; }
    /** Returns the extension duration in milliseconds. */
    public Long getExtendByMs() { return extendByMs; }
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
        if (extendByMs != null) map.put("extend_by_ms", extendByMs);
        if (metadata != null && !metadata.isEmpty()) map.put("metadata", metadata);
        return map;
    }

    /**
     * Creates a new {@code ReservationExtendRequest} builder.
     *
     * @return a new builder instance
     */
    public static Builder builder() { return new Builder(); }

    /** Builder for constructing {@link ReservationExtendRequest} instances. */
    public static class Builder {
        private String idempotencyKey;
        private Long extendByMs;
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
         * Sets the extension duration in milliseconds.
         *
         * @param extendByMs the duration to extend the reservation by
         * @return this builder
         */
        public Builder extendByMs(Long extendByMs) { this.extendByMs = extendByMs; return this; }

        /**
         * Sets the custom metadata.
         *
         * @param metadata the metadata map
         * @return this builder
         */
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }

        /**
         * Builds a new {@link ReservationExtendRequest} from this builder's state.
         *
         * @return the constructed request
         */
        public ReservationExtendRequest build() { return new ReservationExtendRequest(this); }
    }

    @Override
    public String toString() {
        return "ReservationExtendRequest{idempotencyKey='" + idempotencyKey + '\'' +
                ", extendByMs=" + extendByMs + ", metadata=" + metadata + '}';
    }
}
