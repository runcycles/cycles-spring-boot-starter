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

    /**
     * Parses a {@code ReservationStatus} from its string representation.
     *
     * @param value the enum name, or {@code null}
     * @return the matching status, or {@code null} if unrecognized or {@code null}
     */
    public static ReservationStatus fromString(String value) {
        if (value == null) return null;
        try {
            return ReservationStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
