package io.runcycles.client.java.spring.model;

/**
 * How the server handles commits/events where actual exceeds reserved/available budget.
 * Mirrors the spec's {@code CommitOveragePolicy} enum.
 */
public enum CommitOveragePolicy {
    /** Reject the commit if actual exceeds reserved. */
    REJECT,
    /** Allow if sufficient unreserved budget is available. */
    ALLOW_IF_AVAILABLE,
    /** Allow and record overdraft debt. */
    ALLOW_WITH_OVERDRAFT;

    /**
     * Parses a {@code CommitOveragePolicy} from its string representation.
     *
     * @param value the enum name, or {@code null}
     * @return the matching policy, or {@code null} if unrecognized or {@code null}
     */
    public static CommitOveragePolicy fromString(String value) {
        if (value == null) return null;
        try {
            return CommitOveragePolicy.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
