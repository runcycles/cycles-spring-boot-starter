package io.runcycles.client.java.spring.retry;

import java.time.Duration;
import java.util.Map;

/**
 * Strategy interface for retrying failed commit operations.
 *
 * <p>When a commit request fails with a retryable error (transport error, 5xx, 429
 * rate limiting, or a retryable {@link io.runcycles.client.java.spring.model.ErrorCode}),
 * the {@link io.runcycles.client.java.spring.context.CyclesLifecycleService} delegates
 * to this engine to schedule background retries. A commit records spend that has
 * already happened, so engines must never drop it silently: when the reservation
 * expires before the commit lands, the {@code eventFallbackBody} is delivered via
 * {@code POST /v1/events} instead.
 *
 * @see JournaledCommitRetryEngine
 */
public interface CommitRetryEngine {

    /**
     * Schedules a background retry of the commit operation.
     *
     * @param reservationId     the reservation to commit
     * @param commitBody        the commit request body
     * @param eventFallbackBody the {@code POST /v1/events} body used to record the
     *                          spend if the reservation expires before the commit
     *                          lands, or {@code null} when no fallback is available
     * @param retryAfterMs      the server-requested minimum delay before the next
     *                          attempt (from a 429's {@code Retry-After}), or {@code null}
     */
    void schedule(String reservationId, Map<String, Object> commitBody,
                  Map<String, Object> eventFallbackBody, Integer retryAfterMs);

    /**
     * Delivers spend via {@code POST /v1/events} for a reservation that already
     * expired (the server has returned the reserved budget to the pool, but the
     * spend still has to be recorded).
     *
     * @param reservationId the reservation whose spend is being recovered
     * @param eventBody     the event request body
     */
    void scheduleEvent(String reservationId, Map<String, Object> eventBody);

    /**
     * Waits (bounded by one overall deadline) for in-flight retries to finish.
     * Anything still pending when the timeout elapses stays journaled and replays
     * on the next run.
     *
     * @param timeout the maximum time to wait, or {@code null} for the configured default
     */
    void flush(Duration timeout);

    /**
     * Schedules a background retry of the commit operation without an event
     * fallback. Retained for backward compatibility; prefer
     * {@link #schedule(String, Map, Map, Integer)}.
     *
     * @param reservationId the reservation to commit
     * @param body          the commit request body
     */
    @SuppressWarnings("unchecked")
    default void schedule(String reservationId, Object body) {
        schedule(reservationId, body instanceof Map ? (Map<String, Object>) body : null, null, null);
    }
}
