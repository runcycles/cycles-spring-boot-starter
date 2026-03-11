package io.runcycles.client.java.spring.model;

/**
 * Unit of measurement for budget amounts.
 * Mirrors the server's {@code Enums.UnitEnum}.
 */
public enum Unit {
    /** US dollars in microcents (1 USD = 100,000,000 microcents). */
    USD_MICROCENTS,
    /** Token-based units (e.g., LLM tokens). */
    TOKENS,
    /** Abstract credit units. */
    CREDITS,
    /** Risk point units for risk-based budgeting. */
    RISK_POINTS;

    /**
     * Parses a {@code Unit} from its string representation.
     *
     * @param value the enum name, or {@code null}
     * @return the matching unit, or {@code null} if unrecognized or {@code null}
     */
    public static Unit fromString(String value) {
        if (value == null) return null;
        try {
            return Unit.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
