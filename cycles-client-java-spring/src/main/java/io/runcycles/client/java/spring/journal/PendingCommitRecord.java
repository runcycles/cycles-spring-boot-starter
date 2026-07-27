package io.runcycles.client.java.spring.journal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One journaled pending commit (or its {@code POST /v1/events} fallback).
 *
 * <p>Serialized as a single JSON object with snake_case keys ({@code version},
 * {@code reservation_id}, {@code base_url}, {@code mode}, {@code commit_body},
 * {@code event_fallback_body}, {@code recorded_at_ms}, {@code not_before_ms}) —
 * byte-compatible with the journal format of the Python and TypeScript SDKs so
 * records can be settled by any of them.
 */
public final class PendingCommitRecord {

    /** Journal record format version. */
    static final int RECORD_VERSION = 1;

    /** Commit-mode marker: retry {@code POST .../commit}. */
    public static final String MODE_COMMIT = "commit";
    /** Event-mode marker: deliver spend via {@code POST /v1/events}. */
    public static final String MODE_EVENT = "event";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final String reservationId;
    private final String baseUrl;
    private final String mode;
    private final Map<String, Object> commitBody;
    private final Map<String, Object> eventFallbackBody;
    private final long recordedAtMs;
    private final Long notBeforeMs;

    /**
     * Creates a new journal record.
     *
     * @param reservationId     the reservation being settled (non-empty)
     * @param baseUrl           the Cycles server the record was made against
     * @param mode              {@link #MODE_COMMIT} or {@link #MODE_EVENT}
     * @param commitBody        the commit request body (required in commit mode)
     * @param eventFallbackBody the event fallback body (required in event mode)
     * @param recordedAtMs      epoch millis when the record was made
     * @param notBeforeMs       absolute wall-clock floor (ms) for the next attempt,
     *                          set from a 429's {@code Retry-After}; {@code null} if none
     */
    public PendingCommitRecord(String reservationId,
                               String baseUrl,
                               String mode,
                               Map<String, Object> commitBody,
                               Map<String, Object> eventFallbackBody,
                               long recordedAtMs,
                               Long notBeforeMs) {
        this.reservationId = reservationId;
        this.baseUrl = baseUrl;
        this.mode = mode;
        this.commitBody = commitBody;
        this.eventFallbackBody = eventFallbackBody;
        this.recordedAtMs = recordedAtMs;
        this.notBeforeMs = notBeforeMs;
    }

    /**
     * Returns the reservation id this record settles.
     *
     * @return the reservation id
     */
    public String getReservationId() { return reservationId; }

    /**
     * Returns the base URL of the server the record was made against.
     *
     * @return the base URL
     */
    public String getBaseUrl() { return baseUrl; }

    /**
     * Returns the delivery mode.
     *
     * @return {@link #MODE_COMMIT} or {@link #MODE_EVENT}
     */
    public String getMode() { return mode; }

    /**
     * Returns the commit request body.
     *
     * @return the commit body, or {@code null} in event mode
     */
    public Map<String, Object> getCommitBody() { return commitBody; }

    /**
     * Returns the {@code POST /v1/events} fallback body.
     *
     * @return the event fallback body, or {@code null} when none was built
     */
    public Map<String, Object> getEventFallbackBody() { return eventFallbackBody; }

    /**
     * Returns the epoch millis when the record was made.
     *
     * @return the recording timestamp in epoch millis
     */
    public long getRecordedAtMs() { return recordedAtMs; }

    /**
     * Returns the absolute wall-clock floor for the next attempt. Absolute so it
     * survives a process restart mid-wait.
     *
     * @return the floor in epoch millis, or {@code null} if none
     */
    public Long getNotBeforeMs() { return notBeforeMs; }

    /**
     * Serializes this record to its on-disk JSON form.
     *
     * @return the JSON string
     * @throws JsonProcessingException if serialization fails
     */
    public String toJson() throws JsonProcessingException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("version", RECORD_VERSION);
        data.put("reservation_id", reservationId);
        data.put("base_url", baseUrl);
        data.put("mode", mode);
        data.put("commit_body", commitBody);
        data.put("event_fallback_body", eventFallbackBody);
        data.put("recorded_at_ms", recordedAtMs);
        data.put("not_before_ms", notBeforeMs);
        return MAPPER.writeValueAsString(data);
    }

    /**
     * Parses a record from its on-disk JSON form, validating the invariants the
     * retry engine relies on. Invalid records throw so callers can quarantine them.
     *
     * @param raw the JSON string
     * @return the parsed record
     * @throws JsonProcessingException  if the JSON is malformed
     * @throws IllegalArgumentException if the record is semantically invalid
     */
    @SuppressWarnings("unchecked")
    public static PendingCommitRecord fromJson(String raw) throws JsonProcessingException {
        Map<String, Object> data = MAPPER.readValue(raw, MAP_TYPE);

        Object rawReservationId = data.get("reservation_id");
        if (!(rawReservationId instanceof String reservationId) || reservationId.isEmpty()) {
            throw new IllegalArgumentException("journal record missing reservation_id");
        }
        Object rawMode = data.getOrDefault("mode", MODE_COMMIT);
        if (!MODE_COMMIT.equals(rawMode) && !MODE_EVENT.equals(rawMode)) {
            throw new IllegalArgumentException("journal record has unknown mode: " + rawMode);
        }
        String mode = (String) rawMode;
        if (MODE_COMMIT.equals(mode) && !(data.get("commit_body") instanceof Map)) {
            throw new IllegalArgumentException("commit-mode journal record missing commit_body");
        }
        if (MODE_EVENT.equals(mode) && !(data.get("event_fallback_body") instanceof Map)) {
            throw new IllegalArgumentException("event-mode journal record missing event_fallback_body");
        }

        String baseUrl = data.get("base_url") instanceof String s ? s : "";
        Map<String, Object> commitBody = data.get("commit_body") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : null;
        Map<String, Object> eventFallbackBody = data.get("event_fallback_body") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : null;
        long recordedAtMs = data.get("recorded_at_ms") instanceof Number n ? n.longValue() : 0L;
        Long notBeforeMs = data.get("not_before_ms") instanceof Number n ? n.longValue() : null;

        return new PendingCommitRecord(reservationId, baseUrl, mode, commitBody,
                eventFallbackBody, recordedAtMs, notBeforeMs);
    }
}
