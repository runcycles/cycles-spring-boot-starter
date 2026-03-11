package io.runcycles.client.java.spring.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Typed request DTO for the POST /v1/reservations/{id}/release endpoint.
 * Mirrors the server's {@code ReleaseRequest} schema.
 */
public class ReleaseRequest {
    private final String idempotencyKey;
    private final String reason;

    private ReleaseRequest(Builder builder) {
        this.idempotencyKey = builder.idempotencyKey;
        this.reason = builder.reason;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public String getReason() { return reason; }

    /**
     * Serializes this request to a map suitable for the API request body.
     *
     * @return a mutable map of non-null fields
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (idempotencyKey != null) map.put("idempotency_key", idempotencyKey);
        if (reason != null) map.put("reason", reason);
        return map;
    }

    /** Creates a new {@code ReleaseRequest} builder. */
    public static Builder builder() { return new Builder(); }

    /** Builder for constructing {@link ReleaseRequest} instances. */
    public static class Builder {
        private String idempotencyKey;
        private String reason;

        public Builder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public Builder reason(String reason) { this.reason = reason; return this; }
        public ReleaseRequest build() { return new ReleaseRequest(this); }
    }

    @Override
    public String toString() {
        return "ReleaseRequest{idempotencyKey='" + idempotencyKey + '\'' +
                ", reason='" + reason + '\'' + '}';
    }
}
