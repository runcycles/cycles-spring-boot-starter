package io.runcycles.client.java.spring.model;

import java.util.Map;

/**
 * Like {@link Amount} but allows negative values (e.g., remaining balance in overdraft state).
 * Mirrors the server's {@code SignedAmount} DTO.
 */
public class SignedAmount {
    private final Unit unit;
    private final Long amount;

    public SignedAmount(Unit unit, Long amount) {
        this.unit = unit;
        this.amount = amount;
    }

    public static SignedAmount fromMap(Map<String, Object> map) {
        if (map == null) return null;
        Unit unit = map.get("unit") instanceof String s ? Unit.fromString(s) : null;
        Long amount = map.get("amount") instanceof Number n ? n.longValue() : null;
        return new SignedAmount(unit, amount);
    }

    public Unit getUnit() { return unit; }
    public Long getAmount() { return amount; }

    @Override
    public String toString() {
        return "SignedAmount{unit=" + unit + ", amount=" + amount + '}';
    }
}
