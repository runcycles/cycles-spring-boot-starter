package io.runcycles.client.java.spring.model;

/**
 * Unit of measurement for budget amounts.
 * Mirrors the server's {@code Enums.UnitEnum}.
 */
public enum Unit {
    USD_MICROCENTS,
    TOKENS,
    CREDITS,
    RISK_POINTS;

    public static Unit fromString(String value) {
        if (value == null) return null;
        try {
            return Unit.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
