package io.runcycles.client.java.spring.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a non-negative monetary/resource amount with a unit.
 * Mirrors the server's {@code Amount} DTO.
 */
public class Amount {
    private final Unit unit;
    private final long amount;

    public Amount(Unit unit, long amount) {
        this.unit = unit;
        this.amount = amount;
    }

    public static Amount fromMap(Map<String, Object> map) {
        if (map == null) return null;
        Unit unit = map.get("unit") instanceof String s ? Unit.fromString(s) : null;
        long amount = map.get("amount") instanceof Number n ? n.longValue() : 0L;
        return new Amount(unit, amount);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (unit != null) map.put("unit", unit.name());
        map.put("amount", amount);
        return map;
    }

    public Unit getUnit() { return unit; }
    public long getAmount() { return amount; }

    @Override
    public String toString() {
        return "Amount{unit=" + unit + ", amount=" + amount + '}';
    }
}
