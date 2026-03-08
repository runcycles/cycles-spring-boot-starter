package io.runcycles.client.java.spring.model;

import java.util.List;
import java.util.Map;

/**
 * Typed result for {@code POST /v1/events}.
 * Matches server's EventCreateResponse: status, event_id, balances.
 * Spec constrains status to enum {@code [APPLIED]}.
 */
public class EventResult {
    private final EventStatus status;
    private final String eventId;
    private final List<Balance> balances;

    private EventResult(EventStatus status, String eventId, List<Balance> balances) {
        this.status = status;
        this.eventId = eventId;
        this.balances = balances;
    }

    @SuppressWarnings("unchecked")
    public static EventResult fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new EventResult(
                EventStatus.fromString(map.get("status") instanceof String s ? s : null),
                map.get("event_id") instanceof String s ? s : null,
                Balance.listFromRaw(map.get("balances") instanceof List<?> l ? l : null)
        );
    }

    public EventStatus getStatus() { return status; }
    public String getEventId() { return eventId; }
    public List<Balance> getBalances() { return balances; }

    @Override
    public String toString() {
        return "EventResult{status=" + status +
                ", eventId='" + eventId + '\'' +
                ", balances=" + balances + '}';
    }
}
