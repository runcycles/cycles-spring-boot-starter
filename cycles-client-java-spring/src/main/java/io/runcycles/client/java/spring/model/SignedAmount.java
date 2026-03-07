package io.runcycles.client.java.spring.model;

import java.util.Map;

/**
 * Like {@link Amount} but allows negative values (e.g., remaining balance in overdraft state).
 * Mirrors the server's {@code SignedAmount} DTO.
 */
public class SignedAmount {
    private final Unit unit;
    private final long amount;

    public SignedAmount(Unit unit, long amount) {
        this.unit = unit;
        this.amount = amount;
    }

    public static SignedAmount fromMap(Map<String, Object> map) {
        if (map == null) return null;
        Unit unit = map.get("unit") instanceof String s ? Unit.fromString(s) : null;
        long amount = map.get("amount") instanceof Number n ? n.longValue() : 0L;
        return new SignedAmount(unit, amount);
    }

    public Unit getUnit() { return unit; }
    public long getAmount() { return amount; }

    @Override
    public String toString() {
        return "SignedAmount{unit=" + unit + ", amount=" + amount + '}';
    }
}
