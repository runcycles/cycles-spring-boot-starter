package io.runcycles.client.java.spring.model;

/**
 * Status for event create responses.
 * Mirrors the spec's {@code EventCreateResponse.status: enum [APPLIED]}.
 */
public enum EventStatus {
    APPLIED;

    public static EventStatus fromString(String value) {
        if (value == null) return null;
        try {
            return EventStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
