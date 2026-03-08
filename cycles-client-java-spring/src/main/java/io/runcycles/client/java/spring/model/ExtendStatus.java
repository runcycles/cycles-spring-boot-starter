package io.runcycles.client.java.spring.model;

/**
 * Status for extend responses.
 * Mirrors the spec's {@code ReservationExtendResponse.status: enum [ACTIVE]}.
 */
public enum ExtendStatus {
    ACTIVE;

    public static ExtendStatus fromString(String value) {
        if (value == null) return null;
        try {
            return ExtendStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
