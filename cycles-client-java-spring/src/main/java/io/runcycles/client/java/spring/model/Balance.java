package io.runcycles.client.java.spring.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Current budget state for a scope.
 * Mirrors the server's {@code Balance} DTO.
 */
public class Balance {
    private final String scope;
    private final String scopePath;
    private final SignedAmount remaining;
    private final Amount reserved;
    private final Amount spent;
    private final Amount allocated;
    private final Amount debt;
    private final Amount overdraftLimit;
    private final Boolean isOverLimit;

    private Balance(String scope, String scopePath, SignedAmount remaining,
                    Amount reserved, Amount spent, Amount allocated,
                    Amount debt, Amount overdraftLimit, Boolean isOverLimit) {
        this.scope = scope;
        this.scopePath = scopePath;
        this.remaining = remaining;
        this.reserved = reserved;
        this.spent = spent;
        this.allocated = allocated;
        this.debt = debt;
        this.overdraftLimit = overdraftLimit;
        this.isOverLimit = isOverLimit;
    }

    @SuppressWarnings("unchecked")
    public static Balance fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new Balance(
                map.get("scope") instanceof String s ? s : null,
                map.get("scope_path") instanceof String s ? s : null,
                map.get("remaining") instanceof Map<?, ?> m ? SignedAmount.fromMap((Map<String, Object>) m) : null,
                map.get("reserved") instanceof Map<?, ?> m ? Amount.fromMap((Map<String, Object>) m) : null,
                map.get("spent") instanceof Map<?, ?> m ? Amount.fromMap((Map<String, Object>) m) : null,
                map.get("allocated") instanceof Map<?, ?> m ? Amount.fromMap((Map<String, Object>) m) : null,
                map.get("debt") instanceof Map<?, ?> m ? Amount.fromMap((Map<String, Object>) m) : null,
                map.get("overdraft_limit") instanceof Map<?, ?> m ? Amount.fromMap((Map<String, Object>) m) : null,
                map.get("is_over_limit") instanceof Boolean b ? b : null
        );
    }

    /**
     * Parse a list of balance maps into typed Balance objects.
     */
    @SuppressWarnings("unchecked")
    public static List<Balance> listFromRaw(List<?> rawList) {
        if (rawList == null) return null;
        List<Balance> result = new ArrayList<>();
        for (Object item : rawList) {
            if (item instanceof Map<?, ?> m) {
                Balance b = fromMap((Map<String, Object>) m);
                if (b != null) result.add(b);
            }
        }
        return result;
    }

    public String getScope() { return scope; }
    public String getScopePath() { return scopePath; }
    public SignedAmount getRemaining() { return remaining; }
    public Amount getReserved() { return reserved; }
    public Amount getSpent() { return spent; }
    public Amount getAllocated() { return allocated; }
    public Amount getDebt() { return debt; }
    public Amount getOverdraftLimit() { return overdraftLimit; }
    public Boolean getIsOverLimit() { return isOverLimit; }

    @Override
    public String toString() {
        return "Balance{scope='" + scope + '\'' +
                ", scopePath='" + scopePath + '\'' +
                ", remaining=" + remaining +
                ", reserved=" + reserved +
                ", isOverLimit=" + isOverLimit + '}';
    }
}
