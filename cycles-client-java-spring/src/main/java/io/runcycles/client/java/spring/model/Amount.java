package io.runcycles.client.java.spring.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a non-negative monetary/resource amount with a unit.
 * Mirrors the server's {@code Amount} DTO.
 */
public class Amount {
    private final Unit unit;
    private final Long amount;

    public Amount(Unit unit, Long amount) {
        this.unit = unit;
        this.amount = amount;
    }

    /**
     * Deserializes an {@code Amount} from a raw API response map.
     *
     * @param map the amount section of the response, or {@code null}
     * @return the parsed {@code Amount}, or {@code null} if the input is {@code null}
     */
    public static Amount fromMap(Map<String, Object> map) {
        if (map == null) return null;
        Unit unit = map.get("unit") instanceof String s ? Unit.fromString(s) : null;
        Long amount = map.get("amount") instanceof Number n ? n.longValue() : null;
        return new Amount(unit, amount);
    }

    /**
     * Serializes this amount to a map suitable for the API request body.
     *
     * @return a mutable map of non-null fields
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (unit != null) map.put("unit", unit.name());
        if (amount != null) map.put("amount", amount);
        return map;
    }

    public Unit getUnit() { return unit; }
    public Long getAmount() { return amount; }

    @Override
    public String toString() {
        return "Amount{unit=" + unit + ", amount=" + amount + '}';
    }
}
