package io.runcycles.client.java.spring.retry;

/**
 * Strategy interface for retrying failed commit operations.
 *
 * <p>When a commit request fails with a retryable error (transport error, 5xx, or
 * retryable {@link io.runcycles.client.java.spring.model.ErrorCode}), the
 * {@link io.runcycles.client.java.spring.context.CyclesLifecycleService} delegates
 * to this engine to schedule background retries.
 *
 * @see InMemoryCommitRetryEngine
 */
public interface CommitRetryEngine {

    /**
     * Schedules a background retry of the commit operation.
     *
     * @param reservationId the reservation to commit
     * @param body          the commit request body
     */
    void schedule(String reservationId, Object body);
}
