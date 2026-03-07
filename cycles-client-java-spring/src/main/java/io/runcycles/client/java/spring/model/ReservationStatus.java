package io.runcycles.client.java.spring.model;

/**
 * Reservation lifecycle state.
 * Mirrors the server's {@code Enums.ReservationStatus} and the spec's {@code ReservationStatus} enum.
 */
public enum ReservationStatus {
    ACTIVE,
    COMMITTED,
    RELEASED,
    EXPIRED;

    public static ReservationStatus fromString(String value) {
        if (value == null) return null;
        try {
            return ReservationStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
