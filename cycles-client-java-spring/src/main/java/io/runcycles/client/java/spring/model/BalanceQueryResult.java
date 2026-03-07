package io.runcycles.client.java.spring.model;

import java.util.List;
import java.util.Map;

/**
 * Typed result for {@code GET /v1/balances}.
 * Matches server's BalanceResponse: balances[], has_more, next_cursor.
 */
public class BalanceQueryResult {
    private final List<Map<String, Object>> balances;
    private final Boolean hasMore;
    private final String nextCursor;

    private BalanceQueryResult(List<Map<String, Object>> balances, Boolean hasMore, String nextCursor) {
        this.balances = balances;
        this.hasMore = hasMore;
        this.nextCursor = nextCursor;
    }

    @SuppressWarnings("unchecked")
    public static BalanceQueryResult fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new BalanceQueryResult(
                map.get("balances") instanceof List<?> l ? (List<Map<String, Object>>) l : List.of(),
                map.get("has_more") instanceof Boolean b ? b : false,
                map.get("next_cursor") instanceof String s ? s : null
        );
    }

    public List<Map<String, Object>> getBalances() { return balances; }
    public Boolean getHasMore() { return hasMore; }
    public String getNextCursor() { return nextCursor; }

    @Override
    public String toString() {
        return "BalanceQueryResult{balances=" + (balances != null ? balances.size() : 0) +
                " entries, hasMore=" + hasMore +
                ", nextCursor='" + nextCursor + '\'' + '}';
    }
}
