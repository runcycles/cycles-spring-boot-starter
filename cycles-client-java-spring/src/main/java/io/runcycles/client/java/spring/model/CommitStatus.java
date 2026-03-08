package io.runcycles.client.java.spring.model;

/**
 * Status for commit responses.
 * Mirrors the spec's {@code CommitResponse.status: enum [COMMITTED]}.
 */
public enum CommitStatus {
    COMMITTED;

    public static CommitStatus fromString(String value) {
        if (value == null) return null;
        try {
            return CommitStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
