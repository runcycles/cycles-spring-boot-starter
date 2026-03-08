package io.runcycles.client.java.spring.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Typed result for {@code GET /v1/reservations}.
 * Matches server's ReservationListResponse: reservations[], has_more, next_cursor.
 * Each reservation entry is parsed into a {@link ReservationSummaryResult} per the spec's ReservationSummary schema.
 */
public class ReservationListResult {
    private final List<ReservationSummaryResult> reservations;
    private final Boolean hasMore;
    private final String nextCursor;

    private ReservationListResult(List<ReservationSummaryResult> reservations, Boolean hasMore, String nextCursor) {
        this.reservations = reservations;
        this.hasMore = hasMore;
        this.nextCursor = nextCursor;
    }

    @SuppressWarnings("unchecked")
    public static ReservationListResult fromMap(Map<String, Object> map) {
        if (map == null) return null;

        List<ReservationSummaryResult> reservations = new ArrayList<>();
        if (map.get("reservations") instanceof List<?> l) {
            for (Object item : l) {
                if (item instanceof Map<?, ?> m) {
                    ReservationSummaryResult r = ReservationSummaryResult.fromMap((Map<String, Object>) m);
                    if (r != null) reservations.add(r);
                }
            }
        }

        return new ReservationListResult(
                reservations,
                map.get("has_more") instanceof Boolean b ? b : false,
                map.get("next_cursor") instanceof String s ? s : null
        );
    }

    public List<ReservationSummaryResult> getReservations() { return reservations; }
    public Boolean getHasMore() { return hasMore; }
    public String getNextCursor() { return nextCursor; }

    @Override
    public String toString() {
        return "ReservationListResult{reservations=" + reservations.size() +
                " entries, hasMore=" + hasMore +
                ", nextCursor='" + nextCursor + '\'' + '}';
    }
}
