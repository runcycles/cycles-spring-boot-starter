package io.runcycles.client.java.spring.model;

/**
 * Status for extend responses.
 * Mirrors the spec's {@code ReservationExtendResponse.status: enum [ACTIVE]}.
 */
public enum ExtendStatus {
    ACTIVE;

    /**
     * Parses an {@code ExtendStatus} from its string representation.
     *
     * @param value the enum name, or {@code null}
     * @return the matching status, or {@code null} if unrecognized or {@code null}
     */
    public static ExtendStatus fromString(String value) {
        if (value == null) return null;
        try {
            return ExtendStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
