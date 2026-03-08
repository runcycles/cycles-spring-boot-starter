package io.runcycles.client.java.spring.model;

/**
 * Status for release responses.
 * Mirrors the spec's {@code ReleaseResponse.status: enum [RELEASED]}.
 */
public enum ReleaseStatus {
    RELEASED;

    public static ReleaseStatus fromString(String value) {
        if (value == null) return null;
        try {
            return ReleaseStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
