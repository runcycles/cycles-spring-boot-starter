package io.runcycles.client.java.spring.model;

import java.util.Map;

/**
 * Like {@link Amount} but allows negative values (e.g., remaining balance in overdraft state).
 * Mirrors the server's {@code SignedAmount} DTO.
 */
public class SignedAmount {
    private final Unit unit;
    private final Long amount;

    /**
     * Creates a new signed amount.
     *
     * @param unit   the unit of measure
     * @param amount the quantity (may be negative)
     */
    public SignedAmount(Unit unit, Long amount) {
        this.unit = unit;
        this.amount = amount;
    }

    /**
     * Deserializes a {@code SignedAmount} from a raw API response map.
     *
     * @param map the signed-amount section of the response, or {@code null}
     * @return the parsed {@code SignedAmount}, or {@code null} if the input is {@code null}
     */
    public static SignedAmount fromMap(Map<String, Object> map) {
        if (map == null) return null;
        Unit unit = map.get("unit") instanceof String s ? Unit.fromString(s) : null;
        Long amount = map.get("amount") instanceof Number n ? n.longValue() : null;
        return new SignedAmount(unit, amount);
    }

    /** Returns the unit of measure. */
    public Unit getUnit() { return unit; }
    /** Returns the quantity (may be negative). */
    public Long getAmount() { return amount; }

    @Override
    public String toString() {
        return "SignedAmount{unit=" + unit + ", amount=" + amount + '}';
    }
}
