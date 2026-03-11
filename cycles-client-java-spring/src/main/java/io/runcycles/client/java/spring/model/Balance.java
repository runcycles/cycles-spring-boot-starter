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

    /**
     * Deserializes a {@code Balance} from a raw API response map.
     *
     * @param map the balance section of the response, or {@code null}
     * @return the parsed {@code Balance}, or {@code null} if the input is {@code null}
     */
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
     * Parses a list of raw balance maps into typed {@code Balance} objects.
     *
     * @param rawList the raw list from the API response, or {@code null}
     * @return a list of parsed balances, or {@code null} if the input is {@code null}
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

    /**
     * Returns the scope identifier (e.g., tenant, workspace).
     *
     * @return The scope identifier (e.g., tenant, workspace)
     */
    public String getScope() { return scope; }
    /**
     * Returns the fully-qualified scope path.
     *
     * @return The fully-qualified scope path
     */
    public String getScopePath() { return scopePath; }
    /**
     * Returns the remaining balance (may be negative in overdraft).
     *
     * @return The remaining balance (may be negative in overdraft)
     */
    public SignedAmount getRemaining() { return remaining; }
    /**
     * Returns the total amount currently reserved.
     *
     * @return The total amount currently reserved
     */
    public Amount getReserved() { return reserved; }
    /**
     * Returns the total amount spent (committed).
     *
     * @return The total amount spent (committed)
     */
    public Amount getSpent() { return spent; }
    /**
     * Returns the total allocated budget.
     *
     * @return The total allocated budget
     */
    public Amount getAllocated() { return allocated; }
    /**
     * Returns the outstanding debt amount.
     *
     * @return The outstanding debt amount
     */
    public Amount getDebt() { return debt; }
    /**
     * Returns the overdraft limit for this scope.
     *
     * @return The overdraft limit for this scope
     */
    public Amount getOverdraftLimit() { return overdraftLimit; }
    /**
     * Returns whether the scope has exceeded its budget limit.
     *
     * @return Whether the scope has exceeded its budget limit
     */
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
