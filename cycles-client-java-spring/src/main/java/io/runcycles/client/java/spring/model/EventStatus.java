package io.runcycles.client.java.spring.model;

/**
 * Status for event create responses.
 * Mirrors the spec's {@code EventCreateResponse.status: enum [APPLIED]}.
 */
public enum EventStatus {
    /** The event has been successfully applied. */
    APPLIED;

    /**
     * Parses an {@code EventStatus} from its string representation.
     *
     * @param value the enum name, or {@code null}
     * @return the matching status, or {@code null} if unrecognized or {@code null}
     */
    public static EventStatus fromString(String value) {
        if (value == null) return null;
        try {
            return EventStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
