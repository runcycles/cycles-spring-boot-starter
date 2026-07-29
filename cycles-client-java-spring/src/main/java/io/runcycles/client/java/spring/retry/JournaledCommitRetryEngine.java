package io.runcycles.client.java.spring.retry;

import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.config.CyclesProperties;
import io.runcycles.client.java.spring.journal.CommitJournal;
import io.runcycles.client.java.spring.journal.PendingCommitRecord;
import io.runcycles.client.java.spring.model.CyclesResponse;
import io.runcycles.client.java.spring.model.ErrorCode;
import io.runcycles.client.java.spring.model.ErrorResponse;
import io.runcycles.client.java.spring.model.SettlementResponseValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Durable {@link CommitRetryEngine}: exponential-backoff retries on a single-thread
 * daemon {@link ScheduledExecutorService}, backed by an on-disk {@link CommitJournal}.
 *
 * <p>Durability model: every scheduled retry is journaled to disk first and the
 * entry is removed only on a terminal outcome, so pending commits survive JVM
 * restarts and are replayed the next time an engine is created for the same journal
 * identity. When a retried commit lands after the reservation's grace period (the
 * server answers {@code RESERVATION_EXPIRED} and has already returned the reserved
 * budget to the pool), the engine falls back to {@code POST /v1/events} — the
 * spec's post-hoc direct-debit endpoint — so the spend is still recorded.
 *
 * <p>Outcome classification per attempt:
 * <ul>
 *   <li>2xx — success; journal entry discarded.</li>
 *   <li>429 / {@code LIMIT_EXCEEDED} — transient; the server's {@code Retry-After}
 *       becomes the floor for the next delay and is persisted as
 *       {@code not_before_ms} so a restart mid-wait honors it.</li>
 *   <li>401/403 — terminal for this run but the journal entry is retained: fix
 *       credentials and restart to replay.</li>
 *   <li>{@code RESERVATION_EXPIRED} (or a bare 410, even bodyless) — flips to event
 *       mode and delivers the fallback immediately; without a fallback the entry is
 *       retained.</li>
 *   <li>other 4xx with a recognized {@link ErrorCode} — genuine rejection; journal
 *       entry discarded.</li>
 *   <li>4xx without a recognized code (codeless or {@code UNKNOWN}) — terminal for
 *       this run but the journal entry is retained: this client version cannot
 *       prove it is a genuine rejection, so the spend record must survive.</li>
 *   <li>transport/5xx — keep retrying; on exhaustion the entry is retained for
 *       replay on the next run.</li>
 * </ul>
 *
 * <p>On shutdown (Spring {@link DisposableBean}) the engine flushes in-flight
 * retries bounded by {@code cycles.retry.flush-timeout}; unfinished work stays
 * journaled.
 */
public class JournaledCommitRetryEngine implements CommitRetryEngine, DisposableBean {

    private static final Logger LOG = LoggerFactory.getLogger(JournaledCommitRetryEngine.class);

    // Upper bound on any server-requested Retry-After delay (1 hour). Caps hostile
    // or mangled headers and persisted floors so a retry can never be parked for
    // days by a single bad value.
    static final long MAX_RETRY_AFTER_MS = 3_600_000L;

    // Journal replay must happen at most once per identity directory per JVM: the
    // first engine created for a (server, credential) identity claims its
    // subdirectory and replays surviving entries; later engines (and the claimer's
    // own in-flight work) are excluded. Because the claim is scoped to the identity
    // subdirectory — not the shared parent — an engine for one server/key can never
    // block another identity's entries from replaying.
    private static final Set<Path> REPLAYED_DIRS = ConcurrentHashMap.newKeySet();

    /** Clears the once-per-JVM replay claims. Package-private hook for tests. */
    static void resetReplayClaimsForTesting() {
        REPLAYED_DIRS.clear();
    }

    private final CyclesClient client;
    private final CyclesProperties.Retry props;
    private final String baseUrl;
    private final CommitJournal journal;
    private final ScheduledExecutorService executor;

    /**
     * Creates a new durable retry engine and replays any journaled pending commits
     * for this engine's identity (first engine per identity per JVM only).
     *
     * @param client     the Cycles API client for retrying commits and delivering events
     * @param properties the Cycles configuration properties (retry, journal, and the
     *                   base-url/api-key/tenant identity for journal scoping)
     */
    public JournaledCommitRetryEngine(CyclesClient client, CyclesProperties properties) {
        this(client, properties, true);
    }

    /**
     * Creates a new retry engine, optionally forcing the journal off regardless of
     * configuration (used by the deprecated in-memory subclass).
     *
     * @param client         the Cycles API client
     * @param properties     the Cycles configuration properties
     * @param journalAllowed {@code false} to force in-memory-only operation
     */
    protected JournaledCommitRetryEngine(CyclesClient client, CyclesProperties properties,
                                         boolean journalAllowed) {
        this.client = client;
        this.props = properties.getRetry();
        this.baseUrl = properties.getBaseUrl();
        this.journal = journalAllowed ? buildJournal(properties) : null;
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("cycles-commit-retry");
            return t;
        });
        replayJournal();
    }

    private static CommitJournal buildJournal(CyclesProperties properties) {
        CyclesProperties.Journal journalProps = properties.getJournal();
        if (!journalProps.isEnabled()) {
            return null;
        }
        String baseUrl = properties.getBaseUrl();
        String apiKey = properties.getApiKey();
        String tenant = properties.getTenant();
        boolean hasPrincipal = (apiKey != null && !apiKey.isBlank()) || (tenant != null && !tenant.isBlank());
        if (baseUrl == null || baseUrl.isBlank() || !hasPrincipal) {
            LOG.warn("Commit journal disabled: cycles.base-url and cycles.api-key (or cycles.tenant) "
                    + "must be configured to scope journal records to an identity");
            return null;
        }
        Path base = journalProps.getDir() != null && !journalProps.getDir().isBlank()
                ? Path.of(journalProps.getDir())
                : CommitJournal.defaultJournalDir();
        // Per-identity subdirectory: clients sharing a journal directory but using
        // different servers or API keys must never replay each other's records (a
        // foreign record would 401/403 — permanent spend loss risk).
        return new CommitJournal(base.resolve(CommitJournal.authFingerprint(baseUrl, apiKey, tenant)));
    }

    @Override
    public void schedule(String reservationId, Map<String, Object> commitBody,
                         Map<String, Object> eventFallbackBody, Integer retryAfterMs) {
        Pending pending = new Pending(reservationId, commitBody, eventFallbackBody,
                PendingCommitRecord.MODE_COMMIT);
        if (retryAfterMs != null) {
            // A rate-limited first attempt passes its Retry-After along so the first
            // background retry honors the server's delay (clamped to the 1h cap).
            pending.retryAfterMs = Math.min(retryAfterMs.longValue(), MAX_RETRY_AFTER_MS);
        }
        submit(pending);
    }

    @Override
    public void scheduleEvent(String reservationId, Map<String, Object> eventBody) {
        submit(new Pending(reservationId, null, eventBody, PendingCommitRecord.MODE_EVENT));
    }

    @Override
    public void persistPending(String reservationId, Map<String, Object> commitBody,
                               Map<String, Object> eventFallbackBody) {
        journalRecord(new Pending(reservationId, commitBody, eventFallbackBody,
                PendingCommitRecord.MODE_COMMIT));
    }

    @Override
    public void discardPending(String reservationId) {
        journalDiscard(reservationId);
    }

    /**
     * Waits (bounded by one overall deadline) for in-flight retries to finish, then
     * stops accepting new work. Called automatically on Spring context shutdown.
     * Anything still pending when the timeout elapses stays journaled and replays
     * on the next run.
     *
     * @param timeout the maximum time to wait, or {@code null} for
     *                {@code cycles.retry.flush-timeout}
     */
    @Override
    public void flush(Duration timeout) {
        executor.shutdown();
        long waitMs = Math.max(0, (timeout != null ? timeout : props.getFlushTimeout()).toMillis());
        try {
            if (!executor.awaitTermination(waitMs, TimeUnit.MILLISECONDS)) {
                if (journal != null) {
                    LOG.warn("Commit retry flush timed out after {}ms; unfinished work remains journaled "
                            + "for replay on next run", waitMs);
                } else {
                    LOG.error("Commit retry flush timed out after {}ms; in-flight commit retries "
                            + "dropped at shutdown — journal disabled", waitMs);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void destroy() {
        flush(props.getFlushTimeout());
        if (journal != null) {
            // Release this engine's replay claim so a same-JVM context restart (a new
            // engine for the same identity) can resume replaying retained entries.
            REPLAYED_DIRS.remove(journal.getDirectory().toAbsolutePath().normalize());
        }
    }

    // -------------------------
    // Scheduling
    // -------------------------

    private void submit(Pending pending) {
        // Journal FIRST, on every schedule — even when retry is disabled the record
        // must survive for replay on the next run.
        journalRecord(pending);
        if (!props.isEnabled()) {
            logDisabledDrop(pending);
            return;
        }
        scheduleNext(pending);
    }

    private void scheduleNext(Pending pending) {
        long delayMs = delayFor(pending);
        pending.attempt++;
        LOG.debug("Scheduling {} retry: reservationId={}, attempt={}/{}, delayMs={}",
                pending.mode, pending.reservationId, pending.attempt, props.getMaxAttempts(), delayMs);
        try {
            executor.schedule(() -> run(pending), delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            LOG.warn("Retry engine shut down; pending {} remains journaled for replay on next run: "
                    + "reservationId={}", pending.mode, pending.reservationId);
        }
    }

    private void run(Pending pending) {
        try {
            if (attemptOnce(pending)) {
                return;
            }
        } catch (Exception e) {
            LOG.error("{} retry error: reservationId={}, attempt={}",
                    pending.mode, pending.reservationId, pending.attempt, e);
        }
        if (pending.attempt < props.getMaxAttempts()) {
            scheduleNext(pending);
        } else {
            logExhausted(pending);
        }
    }

    /**
     * Computes the next delay: exponential backoff capped at max-delay, floored by a
     * pending server-requested Retry-After (consumed once).
     */
    private long delayFor(Pending pending) {
        double backoff = props.getInitialDelay().toMillis()
                * Math.pow(props.getMultiplier(), pending.attempt);
        long delayMs = (long) Math.min(backoff, props.getMaxDelay().toMillis());
        if (pending.retryAfterMs != null) {
            // A 429 asked us to wait: honor the server's Retry-After when it exceeds
            // our own backoff, then clear it — it applies once.
            delayMs = Math.max(delayMs, pending.retryAfterMs);
            pending.retryAfterMs = null;
        }
        return delayMs;
    }

    // -------------------------
    // Attempts and classification
    // -------------------------

    private boolean attemptOnce(Pending pending) {
        if (PendingCommitRecord.MODE_COMMIT.equals(pending.mode)) {
            CyclesResponse<Map<String, Object>> response =
                    client.commitReservation(pending.reservationId, pending.commitBody);
            boolean terminal = classifyCommitResponse(pending, response);
            if (!terminal && PendingCommitRecord.MODE_EVENT.equals(pending.mode)) {
                // Expired -> deliver the event fallback immediately, no extra backoff.
                return attemptOnce(pending);
            }
            return terminal;
        }
        CyclesResponse<Map<String, Object>> response = client.createEvent(pending.eventFallbackBody);
        return classifyEventResponse(pending, response);
    }

    /**
     * Handles a commit attempt's response. Returns {@code true} when terminal. May
     * flip {@code pending} into event mode; the caller then delivers the event
     * fallback immediately (no extra backoff).
     */
    private boolean classifyCommitResponse(Pending pending, CyclesResponse<Map<String, Object>> response) {
        if (SettlementResponseValidator.isCommitSuccess(response)) {
            LOG.info("Commit retry succeeded: reservationId={}, attempt={}",
                    pending.reservationId, pending.attempt);
            journalDiscard(pending.reservationId);
            return true;
        }
        if (isRateLimited(pending, response)) {
            return false;
        }
        if (response.getStatus() == 401 || response.getStatus() == 403) {
            LOG.error("Commit retry got authentication failure (status={}); journal entry retained — "
                    + "fix credentials and restart to replay: reservationId={}",
                    response.getStatus(), pending.reservationId);
            return true;
        }
        if (response.is4xx()) {
            ErrorCode code = extractErrorCode(response);
            // A bare 410 counts as expired even when the body is missing or mangled:
            // it must flip to the event fallback, never discard the spend record.
            if (code == ErrorCode.RESERVATION_EXPIRED || response.getStatus() == 410) {
                if (pending.eventFallbackBody != null && !pending.eventFallbackBody.isEmpty()) {
                    LOG.warn("Reservation expired before commit landed; falling back to POST /v1/events: "
                            + "reservationId={}", pending.reservationId);
                    pending.mode = PendingCommitRecord.MODE_EVENT;
                    pending.attempt = 0;
                    journalRecord(pending);
                    return false;
                }
                LOG.error("Reservation expired with no event fallback; spend is unrecorded "
                        + "(journal entry retained): reservationId={}", pending.reservationId);
                return true;
            }
            if (code == null || code.isRetryable()) {
                // Codeless or unrecognized 4xx: this client version cannot prove it is
                // a genuine rejection, so keep the spend record for a later replay.
                LOG.error("Commit retry got 4xx without a recognized error code (status={}); journal "
                        + "entry retained for replay on next run: reservationId={}",
                        response.getStatus(), pending.reservationId);
                return true;
            }
            LOG.warn("Commit retry got non-retryable error: reservationId={}, status={}, error={}",
                    pending.reservationId, response.getStatus(), code);
            journalDiscard(pending.reservationId);
            return true;
        }
        LOG.warn("Commit retry failed: reservationId={}, attempt={}, status={}",
                pending.reservationId, pending.attempt, response.getStatus());
        return false;
    }

    /** Handles an event-fallback attempt's response. Returns {@code true} when terminal. */
    private boolean classifyEventResponse(Pending pending, CyclesResponse<Map<String, Object>> response) {
        if (SettlementResponseValidator.isEventSuccess(response)) {
            LOG.info("Recovered expired-commit spend via /v1/events: reservationId={}, eventId={}",
                    pending.reservationId, response.getBodyAttributeAsString("event_id"));
            journalDiscard(pending.reservationId);
            return true;
        }
        if (isRateLimited(pending, response)) {
            return false;
        }
        if (response.getStatus() == 401 || response.getStatus() == 403) {
            LOG.error("Event fallback got authentication failure (status={}); journal entry retained — "
                    + "fix credentials and restart to replay: reservationId={}",
                    response.getStatus(), pending.reservationId);
            return true;
        }
        if (response.is4xx()) {
            ErrorCode code = extractErrorCode(response);
            if (code == null || code.isRetryable()) {
                // Codeless or unrecognized 4xx: cannot prove a genuine rejection, so
                // keep the spend record for a later replay.
                LOG.error("Event fallback got 4xx without a recognized error code (status={}); journal "
                        + "entry retained for replay on next run: reservationId={}",
                        response.getStatus(), pending.reservationId);
                return true;
            }
            LOG.error("Event fallback rejected ({}); spend recovery failed: reservationId={}, status={}",
                    code, pending.reservationId, response.getStatus());
            journalDiscard(pending.reservationId);
            return true;
        }
        LOG.warn("Event fallback failed: reservationId={}, attempt={}, status={}",
                pending.reservationId, pending.attempt, response.getStatus());
        return false;
    }

    /** Detects 429 / LIMIT_EXCEEDED and stashes its Retry-After. Transient, never terminal. */
    private boolean isRateLimited(Pending pending, CyclesResponse<Map<String, Object>> response) {
        if (response.getStatus() != 429 && extractErrorCode(response) != ErrorCode.LIMIT_EXCEEDED) {
            return false;
        }
        Integer retryAfterMs = response.getRetryAfterMs();
        if (retryAfterMs != null) {
            pending.retryAfterMs = Math.min(retryAfterMs.longValue(), MAX_RETRY_AFTER_MS);
            // Persist the floor: a restart during a long Retry-After wait must not
            // replay into the window the server told us to avoid.
            journalRecord(pending);
        }
        LOG.warn("{} retry rate-limited: reservationId={}, attempt={}, retryAfterMs={}",
                pending.mode, pending.reservationId, pending.attempt, retryAfterMs);
        return true;
    }

    private void logExhausted(Pending pending) {
        LOG.error("{} retry exhausted: reservationId={}, attempts={}{}",
                pending.mode, pending.reservationId, props.getMaxAttempts(),
                journal != null ? " (journal entry retained for replay on next run)" : "");
    }

    private void logDisabledDrop(Pending pending) {
        if (journal != null) {
            LOG.warn("Retry disabled; pending {} journaled for replay on next run: reservationId={}",
                    pending.mode, pending.reservationId);
        } else {
            LOG.warn("Retry and journal disabled, dropping failed {}: reservationId={}",
                    pending.mode, pending.reservationId);
        }
    }

    private ErrorCode extractErrorCode(CyclesResponse<Map<String, Object>> response) {
        ErrorResponse errorResponse = response.getErrorResponse();
        if (errorResponse != null && errorResponse.getErrorCode() != null) {
            return errorResponse.getErrorCode();
        }
        return ErrorCode.fromString(response.getBodyAttributeAsString("error"));
    }

    // -------------------------
    // Journal plumbing
    // -------------------------

    private void journalRecord(Pending pending) {
        if (journal == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long notBeforeMs = pending.retryAfterMs != null ? now + pending.retryAfterMs : null;
        journal.record(new PendingCommitRecord(pending.reservationId, baseUrl, pending.mode,
                pending.commitBody, pending.eventFallbackBody, now, notBeforeMs));
    }

    private void journalDiscard(String reservationId) {
        if (journal != null) {
            journal.discard(reservationId);
        }
    }

    private void replayJournal() {
        if (journal == null || !props.isEnabled()) {
            return;
        }
        Path identityDir = journal.getDirectory().toAbsolutePath().normalize();
        if (!REPLAYED_DIRS.add(identityDir)) {
            return;
        }
        List<PendingCommitRecord> entries = journal.loadPending(baseUrl);
        if (!entries.isEmpty()) {
            LOG.info("Replaying {} journaled pending commit(s) from {}", entries.size(), identityDir);
        }
        long now = System.currentTimeMillis();
        for (PendingCommitRecord entry : entries) {
            Pending pending = new Pending(entry.getReservationId(), entry.getCommitBody(),
                    entry.getEventFallbackBody(), entry.getMode());
            // Restore a persisted Retry-After floor as a relative delay (clamped to
            // the 1h cap — records written by other tools may carry arbitrary
            // floors); a floor already in the past falls back to normal backoff.
            if (entry.getNotBeforeMs() != null && entry.getNotBeforeMs() > now) {
                pending.retryAfterMs = Math.min(entry.getNotBeforeMs() - now, MAX_RETRY_AFTER_MS);
            }
            scheduleNext(pending);
        }
    }

    /** Mutable per-pending-commit retry state. */
    private static final class Pending {
        final String reservationId;
        final Map<String, Object> commitBody;
        final Map<String, Object> eventFallbackBody;
        String mode;
        int attempt;
        // Server-requested minimum delay (ms) before the next attempt, set from a
        // 429's Retry-After and consumed by delayFor().
        Long retryAfterMs;

        Pending(String reservationId, Map<String, Object> commitBody,
                Map<String, Object> eventFallbackBody, String mode) {
            this.reservationId = reservationId;
            this.commitBody = commitBody;
            this.eventFallbackBody = eventFallbackBody;
            this.mode = mode;
        }
    }
}
