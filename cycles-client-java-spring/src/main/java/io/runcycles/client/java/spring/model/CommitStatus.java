package io.runcycles.client.java.spring.model;

/**
 * Status for commit responses.
 * Mirrors the spec's {@code CommitResponse.status: enum [COMMITTED]}.
 */
public enum CommitStatus {
    COMMITTED;

    /**
     * Parses a {@code CommitStatus} from its string representation.
     *
     * @param value the enum name, or {@code null}
     * @return the matching status, or {@code null} if unrecognized or {@code null}
     */
    public static CommitStatus fromString(String value) {
        if (value == null) return null;
        try {
            return CommitStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
