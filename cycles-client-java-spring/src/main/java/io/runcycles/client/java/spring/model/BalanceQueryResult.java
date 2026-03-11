package io.runcycles.client.java.spring.model;

import java.util.List;
import java.util.Map;

/**
 * Typed result for {@code GET /v1/balances}.
 * Matches server's BalanceResponse: balances[], has_more, next_cursor.
 */
public class BalanceQueryResult {
    private final List<Balance> balances;
    private final Boolean hasMore;
    private final String nextCursor;

    private BalanceQueryResult(List<Balance> balances, Boolean hasMore, String nextCursor) {
        this.balances = balances;
        this.hasMore = hasMore;
        this.nextCursor = nextCursor;
    }

    /**
     * Deserializes a {@code BalanceQueryResult} from a raw API response map.
     *
     * @param map the response body map, or {@code null}
     * @return the parsed result, or {@code null} if the input is {@code null}
     */
    @SuppressWarnings("unchecked")
    public static BalanceQueryResult fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new BalanceQueryResult(
                Balance.listFromRaw(map.get("balances") instanceof List<?> l ? l : null),
                map.get("has_more") instanceof Boolean b ? b : false,
                map.get("next_cursor") instanceof String s ? s : null
        );
    }

    /** Returns the list of balances. */
    public List<Balance> getBalances() { return balances; }
    /** Returns whether more results are available for pagination. */
    public Boolean getHasMore() { return hasMore; }
    /** Returns the cursor for fetching the next page. */
    public String getNextCursor() { return nextCursor; }

    @Override
    public String toString() {
        return "BalanceQueryResult{balances=" + (balances != null ? balances.size() : 0) +
                " entries, hasMore=" + hasMore +
                ", nextCursor='" + nextCursor + '\'' + '}';
    }
}
