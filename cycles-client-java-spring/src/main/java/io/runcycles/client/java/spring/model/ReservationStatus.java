package io.runcycles.client.java.spring.model;

/**
 * Reservation lifecycle state.
 * Mirrors the server's {@code Enums.ReservationStatus} and the spec's {@code ReservationStatus} enum.
 */
public enum ReservationStatus {
    /** The reservation is currently active and can be committed, extended, or released. */
    ACTIVE,
    /** The reservation has been finalized with a commit. */
    COMMITTED,
    /** The reservation has been explicitly released without committing. */
    RELEASED,
    /** The reservation expired before being committed or released. */
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
