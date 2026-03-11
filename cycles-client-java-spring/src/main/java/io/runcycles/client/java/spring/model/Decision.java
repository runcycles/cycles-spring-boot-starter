package io.runcycles.client.java.spring.model;

/**
 * Server decision for a reservation or decision request.
 * Mirrors the spec's {@code Decision} enum.
 */
public enum Decision {
    /** The action is fully allowed within budget. */
    ALLOW,
    /** The action is allowed but with capability constraints (see {@link Caps}). */
    ALLOW_WITH_CAPS,
    /** The action is denied — budget exceeded or policy violation. */
    DENY;

    /**
     * Parses a {@code Decision} from its string representation.
     *
     * @param value the enum name, or {@code null}
     * @return the matching {@code Decision}, or {@code null} if unrecognized or {@code null}
     */
    public static Decision fromString(String value) {
        if (value == null) return null;
        try {
            return Decision.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
