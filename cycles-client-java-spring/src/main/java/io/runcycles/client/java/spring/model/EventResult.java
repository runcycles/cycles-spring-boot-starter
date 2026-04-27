package io.runcycles.client.java.spring.model;

import java.util.List;
import java.util.Map;

/**
 * Typed result for {@code POST /v1/events}.
 * Matches server's EventCreateResponse: status, event_id, charged, balances.
 * Spec constrains status to enum {@code [APPLIED]}.
 */
public class EventResult {
    private final EventStatus status;
    private final String eventId;
    private final Amount charged;
    private final List<Balance> balances;

    private EventResult(EventStatus status, String eventId, Amount charged, List<Balance> balances) {
        this.status = status;
        this.eventId = eventId;
        this.charged = charged;
        this.balances = balances;
    }

    /**
     * Deserializes the {@code POST /v1/events} response body.
     *
     * @param map the parsed JSON body, or {@code null}
     * @return the typed result, or {@code null} when {@code map} is {@code null}
     */
    @SuppressWarnings("unchecked")
    public static EventResult fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new EventResult(
                EventStatus.fromString(map.get("status") instanceof String s ? s : null),
                map.get("event_id") instanceof String s ? s : null,
                Amount.fromMap(map.get("charged") instanceof Map<?, ?> m ? (Map<String, Object>) m : null),
                Balance.listFromRaw(map.get("balances") instanceof List<?> l ? l : null)
        );
    }

    /**
     * Returns the server-reported event status (currently always {@code APPLIED}).
     *
     * @return the event status
     */
    public EventStatus getStatus() { return status; }

    /**
     * Returns the server-assigned event identifier.
     *
     * @return the event id, or {@code null} if absent
     */
    public String getEventId() { return eventId; }

    /**
     * Returns the amount charged for this event.
     *
     * @return the charged amount, or {@code null} if absent
     */
    public Amount getCharged() { return charged; }

    /**
     * Returns the post-event balances for the affected scopes.
     *
     * @return the balances, or {@code null} if not returned
     */
    public List<Balance> getBalances() { return balances; }

    @Override
    public String toString() {
        return "EventResult{status=" + status +
                ", eventId='" + eventId + '\'' +
                ", charged=" + charged +
                ", balances=" + balances + '}';
    }
}
