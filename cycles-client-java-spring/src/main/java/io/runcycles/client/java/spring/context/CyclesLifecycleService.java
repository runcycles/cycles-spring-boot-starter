package io.runcycles.client.java.spring.context;

import io.runcycles.client.java.spring.annotation.Cycles;
import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.evaluation.CyclesExpressionEvaluator;
import io.runcycles.client.java.spring.model.*;
import io.runcycles.client.java.spring.retry.CommitRetryEngine;
import io.runcycles.client.java.spring.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;

/**
 * Orchestrates the Cycles reserve/execute/commit lifecycle.
 * Extracted from CyclesAspect to enable unit testing and programmatic usage.
 */
public class CyclesLifecycleService {

    private static final Logger LOG = LoggerFactory.getLogger(CyclesLifecycleService.class);

    private final CyclesClient client;
    private final CommitRetryEngine retryEngine;
    private final CyclesExpressionEvaluator evaluator;
    private final CyclesRequestBuilderService requestBuilderService;
    private final ScheduledExecutorService heartbeatExecutor;
    private final LongSupplier nanoClock;
    /**
     * The client's ENFORCED finite upper bound for one complete extend attempt
     * (connect, write, read, failure detection) in ms, or {@code null} when
     * unknown/unbounded. Feeds attempt_budget in the remaining_ttl_ms heartbeat
     * schedule (HEARTBEAT GUIDANCE, cycles-protocol-v0.yaml); unknown makes
     * attempt_budget infinite, which forces next_delay to 0 per spec.
     */
    private final Long requestTimeoutBudgetMs;

    /**
     * Creates a new lifecycle service with the given dependencies and an
     * unknown per-attempt HTTP timeout (heartbeat field-mode scheduling then
     * conservatively treats the attempt budget as unbounded).
     *
     * @param client                the Cycles API client
     * @param retryEngine           the commit retry engine
     * @param requestBuilderService the request builder service
     * @param evaluator             the SpEL expression evaluator
     */
    public CyclesLifecycleService(CyclesClient client,
                                  CommitRetryEngine retryEngine,
                                  CyclesRequestBuilderService requestBuilderService,
                                  CyclesExpressionEvaluator evaluator) {
        this(client, retryEngine, requestBuilderService, evaluator, (Duration) null);
    }

    /**
     * Creates a new lifecycle service with the given dependencies and the
     * client's enforced per-attempt HTTP timeout.
     *
     * @param client                the Cycles API client
     * @param retryEngine           the commit retry engine
     * @param requestBuilderService the request builder service
     * @param evaluator             the SpEL expression evaluator
     * @param requestTimeoutBudget  the enforced upper bound for one complete
     *                              HTTP attempt (connect + read), or {@code null}
     *                              when unknown/unbounded
     */
    public CyclesLifecycleService(CyclesClient client,
                                  CommitRetryEngine retryEngine,
                                  CyclesRequestBuilderService requestBuilderService,
                                  CyclesExpressionEvaluator evaluator,
                                  Duration requestTimeoutBudget) {
        this(client, retryEngine, requestBuilderService, evaluator,
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("cycles-heartbeat");
                    return t;
                }), System::nanoTime,
                requestTimeoutBudget != null ? requestTimeoutBudget.toMillis() : null);
    }

    // Visible for testing
    CyclesLifecycleService(CyclesClient client,
                           CommitRetryEngine retryEngine,
                           CyclesRequestBuilderService requestBuilderService,
                           CyclesExpressionEvaluator evaluator,
                           ScheduledExecutorService heartbeatExecutor) {
        this(client, retryEngine, requestBuilderService, evaluator, heartbeatExecutor, System::nanoTime, null);
    }

    // Visible for testing — injectable monotonic clock for deterministic heartbeat tests
    CyclesLifecycleService(CyclesClient client,
                           CommitRetryEngine retryEngine,
                           CyclesRequestBuilderService requestBuilderService,
                           CyclesExpressionEvaluator evaluator,
                           ScheduledExecutorService heartbeatExecutor,
                           LongSupplier nanoClock) {
        this(client, retryEngine, requestBuilderService, evaluator, heartbeatExecutor, nanoClock, null);
    }

    // Visible for testing — injectable clock and enforced per-attempt timeout (ms)
    CyclesLifecycleService(CyclesClient client,
                           CommitRetryEngine retryEngine,
                           CyclesRequestBuilderService requestBuilderService,
                           CyclesExpressionEvaluator evaluator,
                           ScheduledExecutorService heartbeatExecutor,
                           LongSupplier nanoClock,
                           Long requestTimeoutBudgetMs) {
        this.client = client;
        this.retryEngine = retryEngine;
        this.requestBuilderService = requestBuilderService;
        this.evaluator = evaluator;
        this.heartbeatExecutor = heartbeatExecutor;
        this.nanoClock = nanoClock;
        this.requestTimeoutBudgetMs = requestTimeoutBudgetMs;
    }

    /**
     * Executes the full reserve/execute/commit lifecycle.
     *
     * @param action     the guarded work to execute between reserve and commit
     * @param cycles     the annotation configuration
     * @param method     the annotated method (for SpEL evaluation)
     * @param args       the method arguments
     * @param target     the target object
     * @param actionKind resolved action kind
     * @param actionName resolved action name
     * @return the result of the guarded action
     * @throws Throwable if the guarded method or lifecycle operations fail
     */
    public Object executeWithReservation(ThrowingSupplier<Object> action,
                                         Cycles cycles,
                                         Method method,
                                         Object[] args,
                                         Object target,
                                         String actionKind,
                                         String actionName) throws Throwable {
        // Evaluate estimate
        String estimateExpr = resolveEstimateExpression(cycles);
        long estimate = evaluator.evaluate(estimateExpr, method, args, null, target);
        LOG.debug("Estimated usage: estimate={}", estimate);

        // Create reservation
        Map<String, Object> createBody = requestBuilderService.buildReservation(
                cycles, estimate, actionKind, actionName, null, method, args, target);
        LOG.debug("Creating reservation: createBody={}", createBody);

        long resT1 = System.currentTimeMillis();
        // Round-trip of the create call on the injected monotonic clock — feeds the
        // remaining_ttl_ms lead-floor computation when the server emits the field.
        // Rounded UP so rounding cannot consume the reserved margin.
        long createSentNanos = nanoClock.getAsLong();
        CyclesResponse<Map<String, Object>> reservationResponse = client.createReservation(createBody);
        long createRttMs = ceilToMs(nanoClock.getAsLong() - createSentNanos);

        if (!reservationResponse.is2xx()) {
            LOG.error("Reservation failed, aborting: reservationResponse={}", reservationResponse);
            throw buildProtocolException("Failed to create reservation", reservationResponse);
        }

        // Parse reservation response into typed DTO
        ReservationResult resResult = ReservationResult.fromMap(reservationResponse.getBody());
        long resT2 = System.currentTimeMillis();

        if (resResult == null) {
            LOG.error("Could not parse reservation response: responseBody={}", reservationResponse.getBody());
            throw new CyclesProtocolException(
                    "Failed to parse reservation response",
                    ErrorCode.INTERNAL_ERROR, null, reservationResponse.getStatus());
        }

        Decision decision = resResult.getDecision();
        String reservationId = resResult.getReservationId();
        String reasonCode = resResult.getReasonCode();
        Long expiresAtMs = resResult.getExpiresAtMs();
        Caps caps = resResult.getCaps();
        List<String> affectedScopes = resResult.getAffectedScopes();
        String scopePath = resResult.getScopePath();
        Amount reserved = resResult.getReserved();
        Integer retryAfterMs = resResult.getRetryAfterMs();
        List<Balance> balances = resResult.getBalances();

        // Validate decision field
        if (decision == null) {
            String rawDecision = reservationResponse.getBodyAttributeAsString("decision");
            LOG.error("Unrecognized decision value from server: decision={}, response={}", rawDecision, reservationResponse.getBody());
            throw new CyclesProtocolException(
                    "Unrecognized decision value: " + rawDecision,
                    ErrorCode.INTERNAL_ERROR, null, reservationResponse.getStatus());
        }

        // Handle dry-run: return typed DryRunResult with full evaluation data
        if (cycles.dryRun()) {
            long elapsedMs = resT2 - resT1;
            if (decision == Decision.DENY) {
                LOG.info("Dry-run denied: elapsedTime={}ms, reasonCode={}", elapsedMs, reasonCode);
                throw new CyclesProtocolException(
                        "Dry-run denied: " + (reasonCode != null ? reasonCode : "BUDGET_EXCEEDED"),
                        ErrorCode.fromString(reasonCode != null ? reasonCode : "BUDGET_EXCEEDED"),
                        reasonCode,
                        reservationResponse.getStatus(),
                        retryAfterMs
                );
            }
            LOG.info("Dry-run evaluated: elapsedTime={}ms, decision={}, caps={}, affectedScopes={}",
                    elapsedMs, decision, caps, affectedScopes);
            return new DryRunResult(decision, caps, affectedScopes, scopePath, reserved, balances, reasonCode, retryAfterMs);
        }

        // Handle DENY
        if (decision == Decision.DENY) {
            LOG.error("Reservation denied: decision=DENY, reasonCode={}, retryAfterMs={}, response={}",
                    reasonCode, retryAfterMs, reservationResponse.getBody());
            throw new CyclesProtocolException(
                    "Reservation denied: " + (reasonCode != null ? reasonCode : "BUDGET_EXCEEDED"),
                    ErrorCode.fromString(reasonCode != null ? reasonCode : "BUDGET_EXCEEDED"),
                    reasonCode,
                    reservationResponse.getStatus(),
                    retryAfterMs
            );
        }

        if (decision == Decision.ALLOW_WITH_CAPS) {
            LOG.warn("Reservation allowed with caps: caps={}, response={}", caps, reservationResponse.getBody());
        }

        if (reservationId == null) {
            LOG.error("Reservation successful but reservation id missing: responseBody={}", reservationResponse.getBody());
            throw new CyclesProtocolException("Failed to create reservation because of missing reservation identifier");
        }

        LOG.info("Reservation created: elapsedTime={}ms, reservationId={}, decision={}, expiresAtMs={}",
                (resT2 - resT1), reservationId, decision, expiresAtMs);

        // Set context and start heartbeat
        CyclesReservationContext ctx = new CyclesReservationContext(
                reservationId, estimate, decision, caps, expiresAtMs,
                affectedScopes, scopePath, reserved, balances);
        CyclesContextHolder.set(ctx);

        Runnable heartbeatCanceller = scheduleHeartbeat(
                reservationId, cycles.ttlMs(), expiresAtMs,
                resResult.getRemainingTtlMs(), createRttMs, ctx);

        try {
            // Execute guarded action
            Object result = action.get();
            long methodElapsed = System.currentTimeMillis() - resT2;
            LOG.debug("Guarded action finished: reservationId={}, methodElapsedMs={}", reservationId, methodElapsed);

            // Resolve actual amount
            boolean actualFromEstimate = cycles.actual().isBlank() && cycles.useEstimateIfActualNotProvided();
            long actualAmount = resolveActualAmount(cycles, estimate, method, args, result, target);

            // Build and send commit
            CyclesMetrics metrics = ctx.getMetrics();
            if (metrics == null) {
                metrics = new CyclesMetrics();
            }
            if (metrics.getLatencyMs() == null) {
                metrics.setLatencyMs((int) methodElapsed);
            }

            Map<String, Object> commitMetadata = resolveCommitMetadata(
                    cycles, method, args, result, target, ctx.getCommitMetadata());
            if (actualFromEstimate) {
                // The commit records the estimate as measured spend — mark it so the
                // server-side record is distinguishable from a genuinely measured actual.
                Map<String, Object> marked = new LinkedHashMap<>();
                if (commitMetadata != null) {
                    marked.putAll(commitMetadata);
                }
                marked.put("actual_source", "estimate");
                commitMetadata = marked;
            }
            Map<String, Object> commitBody = requestBuilderService.buildCommit(
                    cycles, actualAmount, metrics, commitMetadata);
            Map<String, Object> eventFallbackBody = buildEventFallbackBody(
                    reservationId, createBody, commitBody);

            handleCommit(reservationId, commitBody, eventFallbackBody);

            return result;

        } catch (Throwable ex) {
            LOG.error("Guarded action failed, releasing reservation: reservationId={}", reservationId, ex);
            handleRelease(reservationId, "guarded_method_failed");
            throw ex;
        } finally {
            cancelHeartbeat(heartbeatCanceller);
            CyclesContextHolder.clear();
        }
    }

    // -------------------------
    // Commit
    // -------------------------

    /**
     * Builds a {@code POST /v1/events} body that records the spend of a commit whose
     * reservation expired before the commit landed (the server has already returned
     * the reserved budget to the pool at that point).
     *
     * <p>Reuses the commit's idempotency key — the event idempotency namespace is
     * separate, so replays across JVM restarts stay exactly-once. Omits
     * {@code overage_policy}: the spec default ALLOW_IF_AVAILABLE never rejects,
     * which is the right bias when the spend has already happened.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> buildEventFallbackBody(String reservationId,
                                                              Map<String, Object> createBody,
                                                              Map<String, Object> commitBody) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (commitBody.get("metadata") instanceof Map<?, ?> existing) {
            metadata.putAll((Map<String, Object>) existing);
        }
        metadata.put("recovered_reservation_id", reservationId);
        metadata.put("recovery_reason", "commit_after_reservation_expired");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("idempotency_key", commitBody.get("idempotency_key"));
        body.put("subject", createBody.get("subject"));
        body.put("action", createBody.get("action"));
        body.put("actual", commitBody.get("actual"));
        body.put("metadata", metadata);
        if (commitBody.containsKey("metrics")) {
            body.put("metrics", commitBody.get("metrics"));
        }
        return body;
    }

    private void handleCommit(String reservationId, Map<String, Object> commitBody,
                              Map<String, Object> eventFallbackBody) {
        try {
            LOG.debug("Committing reservation: reservationId={}, commitBody={}", reservationId, commitBody);
            long comT1 = System.currentTimeMillis();
            CyclesResponse<Map<String, Object>> commitResponse = client.commitReservation(reservationId, commitBody);
            long comT2 = System.currentTimeMillis();
            LOG.debug("Commit done: elapsedTime={}ms, response={}", (comT2 - comT1), commitResponse);

            if (commitResponse.is2xx()) {
                CommitResult commitResult = CommitResult.fromMap(commitResponse.getBody());
                LOG.info("Commit successful: reservationId={}, status={}, charged={}, released={}",
                        reservationId,
                        commitResult != null ? commitResult.getStatus() : null,
                        commitResult != null ? commitResult.getCharged() : null,
                        commitResult != null ? commitResult.getReleased() : null);
            } else {
                LOG.error("Commit failed: reservationId={}, reason={}, responseBody={}",
                        reservationId, commitResponse.getErrorMessage(), commitResponse.getBody());
                ErrorCode commitErrorCode = extractErrorCode(commitResponse);
                if (commitResponse.isTransportError() || commitResponse.is5xx()
                        || commitResponse.getStatus() == 429
                        || (commitErrorCode != null && commitErrorCode.isRetryable())) {
                    // Transient (transport, 5xx, or 429/LIMIT_EXCEEDED rate limiting —
                    // including bodyless 429s): not a rejection, so releasing would
                    // return budget for spend that already happened. Retry instead,
                    // honoring the server's Retry-After.
                    Integer retryAfterMs = commitResponse.getStatus() == 429
                            ? commitResponse.getRetryAfterMs() : null;
                    retryEngine.schedule(reservationId, commitBody, eventFallbackBody, retryAfterMs);
                } else if (commitResponse.getStatus() == 401 || commitResponse.getStatus() == 403) {
                    // Credentials failed after the spend happened: journal the commit
                    // for replay once they're fixed. Never release — that would return
                    // budget for real spend.
                    LOG.error("Commit got authentication failure (status={}); scheduling for replay: "
                            + "reservationId={}", commitResponse.getStatus(), reservationId);
                    retryEngine.schedule(reservationId, commitBody, eventFallbackBody, null);
                } else if (commitErrorCode == ErrorCode.RESERVATION_EXPIRED
                        || commitResponse.getStatus() == 410) {
                    // A bare 410 counts as expired even when the body is missing or
                    // mangled — recover the spend, never release or discard it.
                    LOG.warn("Reservation expired before commit; recovering spend via POST /v1/events: "
                            + "reservationId={}", reservationId);
                    retryEngine.scheduleEvent(reservationId, eventFallbackBody);
                } else if (commitErrorCode == ErrorCode.RESERVATION_FINALIZED) {
                    LOG.warn("Reservation already finalized, skipping release: reservationId={}", reservationId);
                } else if (commitErrorCode == ErrorCode.IDEMPOTENCY_MISMATCH) {
                    LOG.warn("Commit idempotency mismatch (not releasing): reservationId={}", reservationId);
                } else if (commitResponse.is4xx()) {
                    handleRelease(reservationId, "commit_rejected_" + commitErrorCode);
                } else {
                    LOG.warn("Unrecognized response: response={}", commitResponse);
                }
            }

        } catch (Exception e) {
            LOG.error("Failed to commit reservation: reservationId={}", reservationId, e);
            retryEngine.schedule(reservationId, commitBody, eventFallbackBody, null);
        }
    }

    // -------------------------
    // Release
    // -------------------------
    private void handleRelease(String reservationId, String reason) {
        try {
            LOG.info("Releasing reservation: reservationId={}, reason={}", reservationId, reason);
            CyclesResponse<Map<String, Object>> releaseResponse = client.releaseReservation(reservationId,
                    requestBuilderService.buildRelease(reason));
            if (releaseResponse.is2xx()) {
                LOG.info("Reservation released successfully: reservationId={}", reservationId);
            } else {
                LOG.warn("Reservation release failed: reservationId={}, errorMessage={}, responseBody={}",
                        reservationId, releaseResponse.getErrorMessage(), releaseResponse.getBody());
            }
        } catch (Exception e) {
            LOG.error("Failed to release reservation: reservationId={}", reservationId, e);
        }
    }

    // -------------------------
    // Heartbeat
    // -------------------------
    /**
     * Schedules the keepalive heartbeat for a reservation and returns a canceller
     * (run it to stop the heartbeat), or {@code null} when no heartbeat is needed.
     */
    private Runnable scheduleHeartbeat(String reservationId, long requestedTtlMs,
                                       Long expiresAtMs, Long createRemainingTtlMs,
                                       long createRttMs, CyclesReservationContext ctx) {
        if (expiresAtMs == null || requestedTtlMs <= 0) {
            return null;
        }
        // NORMATIVE SCHEDULING (remaining_ttl_ms — HEARTBEAT GUIDANCE in
        // cycles-protocol-v0.yaml, spec PR #148 head dd60c27). Only a SCHEMA-VALID
        // HTTP 200 ReservationExtendResponse (status=ACTIVE + expires_at_ms), or the
        // initial ReservationCreateResponse, counts as an observed success in this
        // mode; any other or malformed 2xx is AMBIGUOUS and is treated as a transient
        // failure with same-key recovery — never as applied. On every such success
        // carrying remaining_ttl_ms, the schedule is recomputed from that response
        // alone (never from accumulated expiry differences):
        //
        //   rtt           = per-attempt monotonic round-trip (rounded UP; max tracked)
        //   leadFloor     = max(0, remaining_ttl_ms - rtt)               (rounded DOWN)
        //   attemptBudget = max(requestTimeoutBudget, 1000, 2 x maxObservedRtt)
        //                   (unknown/unbounded timeout -> positive infinity)
        //   safetyMargin  = max(1000, 2 x maxObservedRtt)
        //   retryReserve  = 2 x attemptBudget + safetyMargin
        //   nextDelay     = max(0, leadFloor - retryReserve)
        //
        // scheduled from response receipt, overflow-safe ms arithmetic throughout
        // (positive overflow saturates, never wraps). The leadMin skip check is
        // BYPASSED — scheduling is exact, so a heuristic skip could overshoot the
        // real lease. ZERO-DELAY GUARD: a schema-valid success producing nextDelay 0
        // permits ONE immediate fresh attempt (new idempotency key); if that success
        // also yields 0, the heartbeat stops and surfaces that the lease is shorter
        // than the retry-safety budget. Unknown timing/timeout MUST NOT be treated as
        // zero elapsed and MUST NOT downgrade the client to the fieldless fallback.
        //
        // RECOVERY (timeout/connection/5xx/429/ambiguous-2xx): from the SAME last
        // schema-valid response, currentLeadEstimate = max(0, lastLeadFloor -
        // elapsedSinceThatResponse) and retryWindow = currentLeadEstimate -
        // attemptBudget - safetyMargin, UNclamped. window < 0 -> no complete retry
        // plus margin is provably safe: stop and surface. Otherwise non-429 retries
        // the SAME key after min(30s, currentLeadEstimate/4, retryWindow); 429 uses
        // Retry-After (delta-seconds x 1000, overflow-safe) only when it fits the
        // window, else stops. Recovery may repeat: lead/window are recomputed after
        // EVERY failed attempt; window == 0 permits one immediate retry then stops,
        // and a progress guard stops the loop when neither elapsed nor window moves
        // between consecutive failures. Any other 4xx stops and surfaces without
        // key rotation. The grants/leadMin bookkeeping keeps running underneath so
        // the fallback heuristic takes over the moment a response lacks the field
        // (older server, or a proxy stripping it).
        //
        // Everything below is the best-effort FALLBACK for servers that do not emit
        // remaining_ttl_ms. Regime detection from (grant, elapsed) alone is formally
        // undecidable — sticky window: a per-extend grant in
        // [0.75 x min(ttl/2, 30s), 0.9 x ttl) is indistinguishable from a lead-clamp
        // echo at the cadence it itself induces — which is why the field exists.
        //
        // FIRST BEAT (fallback). Immediate (~0ms). Tenant policy max_reservation_ttl_ms
        // silently CAPS the granted TTL at reserve time (governance default 1 hour),
        // so without remaining_ttl_ms ANY bounded first-beat delay can outlive a small
        // capped lease (a 30s delay lapses a 10s cap before the first extend). An
        // immediate first extend costs one cheap request and primes the grant
        // observation below. When the CREATE response carries remaining_ttl_ms the
        // first beat is scheduled from the formula above instead — no wasted primed
        // extension under max-lead clamping. (The Date-derived first-beat hint from
        // v2.2 is gone: cross-clock arithmetic on a best-effort header.)
        //
        // LEAD LOWER BOUND. extend_by_ms is relative to the CURRENT expires_at_ms, so
        // blindly extending on every beat would drift expiry ahead of the client (a
        // zombie budget lockup if the process dies) and burn the server's capped
        // extension_count faster than needed. Each beat computes a conservative lower
        // bound on how far expiry leads "now":
        //
        //   leadMinMs = grantsSum - elapsedMs
        //
        // where grantsSum is the sum of observed grants (differences of successive
        // returned expires_at_ms values — same server frame, so skew-free) and elapsedMs
        // is a difference of two client-monotonic readings. It starts at 0 (nothing
        // proven yet), so the first beat always extends; the bound only ever
        // understates the true lead, so skipping on it can never cause a lapse. A beat
        // skips only when leadMinMs >= 1.5 x the last observed grant; otherwise it
        // extends by the REQUESTED ttl (the server clamps to what policy allows, and
        // the returned expires_at_ms reveals the actual grant).
        //
        // SCHEDULING. Beats are one-shot and self-rescheduling (schedule, not
        // scheduleAtFixedRate) so the cadence can adapt to the observed grant:
        // after a success the next delay is clamp(grant/2, 500, requestedTtl/2).
        // One-shot scheduling also removes the fixed-rate catch-up hazard by
        // construction — there is no queue of missed ticks to fire back-to-back,
        // because the next beat is only scheduled when the current one finishes.
        //
        // LEAD-CLAMP REGIME. Grant-derived cadence is only valid when the server
        // grants real per-extend lease. Under a maximum-LEAD clamp (a depleting
        // budget holds expiry at ≈ now+L), successive expires_at_ms differences
        // measure ELAPSED time, not lease — feeding them into grant/2 would collapse
        // the cadence to the floor and burn the capped max_extensions in seconds.
        // Each success therefore compares the observed grant against the elapsed
        // time since the previous success (first: since heartbeat start): a
        // non-positive grant, or one both well below the requested ttl (< 0.9×)
        // and INSIDE the elapsed band (0.75×elapsed ≤ grant ≤ 1.25×elapsed), marks
        // the lead-clamp regime — the cadence HOLDS at min(requestedTtl/2, 30s),
        // never tightens, and a single WARN flags the likely budget depletion.
        // Real per-extend grants (e.g. a policy-capped but genuine lease) still
        // tighten normally. The band needs BOTH bounds: after a leadMin skip the
        // next grant arrives across a doubled gap, so a genuine clamped grant can
        // equal elapsed exactly (the cadence is grant/2) — an upper-bound-only
        // test would stick that server in the hold, where a clamped lease banks
        // less than elapsed per cycle and decays to a lapse. With the band, a
        // post-skip real grant lands in the hold at most once; at the held cadence
        // its grant/elapsed ratio falls to ~0.5, exits the band, and the cadence
        // re-tightens. A genuine maximum-lead clamp tracks ANY gap (ratio ≈ 1),
        // so it stays held.
        //
        // FAILURE HANDLING. A failed extend keeps its idempotency key and retries it on
        // the next beat (at the current delay), so a lost response can never
        // double-extend. Permanent failures (410/RESERVATION_EXPIRED,
        // RESERVATION_FINALIZED, MAX_EXTENSIONS_EXCEEDED, TENANT_CLOSED, NOT_FOUND)
        // stop the heartbeat for good — all irreversible; no amount of retrying can
        // revive those.
        final long anchorNanos = nanoClock.getAsLong();
        // Held cadence for the lead-clamp regime, and the fallback retry delay while
        // no grant has been observed yet (the fallback first beat fires at 0 —
        // retrying a failed first extend at delay 0 would busy-spin).
        final long heldCadenceMs = Math.min(requestedTtlMs / 2, 30_000L);
        AtomicLong prevExpiry = new AtomicLong(expiresAtMs);
        AtomicLong grantsSum = new AtomicLong(0);
        AtomicReference<Long> lastGrant = new AtomicReference<>();
        AtomicLong lastSuccessNanos = new AtomicLong(anchorNanos);
        AtomicLong delayMs = new AtomicLong(heldCadenceMs);
        AtomicBoolean leadClampWarned = new AtomicBoolean(false);
        AtomicReference<String> pendingKey = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> selfRef = new AtomicReference<>();
        AtomicBoolean stopped = new AtomicBoolean(false);
        // Server-authoritative lead floor (field mode): >= 0 while the LATEST
        // schema-valid success carried remaining_ttl_ms, -1 in heuristic mode.
        AtomicLong lastLeadFloorMs = new AtomicLong(-1L);
        AtomicLong leadFloorAtNanos = new AtomicLong(anchorNanos);
        AtomicLong maxRttMs = new AtomicLong(Math.max(createRttMs, 0L));
        // Zero-delay guard: true while the latest schema-valid success (or the
        // create) produced next_delay 0 — a second consecutive zero stops the beat.
        AtomicBoolean zeroDelayStreak = new AtomicBoolean(false);
        // Recovery progress state, reset on every schema-valid success.
        AtomicBoolean zeroWindowRetried = new AtomicBoolean(false);
        AtomicLong lastRecoveryElapsed = new AtomicLong(Long.MIN_VALUE);
        AtomicLong lastRecoveryWindow = new AtomicLong(Long.MIN_VALUE);
        long firstDelayMs = 0L;
        if (createRemainingTtlMs != null) {
            long leadFloor = Math.max(0L, createRemainingTtlMs - Math.max(createRttMs, 0L));
            lastLeadFloorMs.set(leadFloor);
            firstDelayMs = nextFieldDelayMs(leadFloor, maxRttMs.get());
            // A zero first delay is itself the one permitted immediate attempt.
            zeroDelayStreak.set(firstDelayMs == 0L);
        }
        LOG.debug("Scheduling heartbeat: reservationId={}, requestedTtlMs={}, "
                + "createRemainingTtlMs={}, firstDelayMs={}",
                reservationId, requestedTtlMs, createRemainingTtlMs, firstDelayMs);
        Runnable beat = new Runnable() {

            /** Stops the heartbeat for good and surfaces the reason at WARN. */
            private void stopAndSurface(String reason) {
                stopped.set(true);
                LOG.warn("Heartbeat stopped ({}): reservationId={}", reason, reservationId);
                ScheduledFuture<?> self = selfRef.get();
                if (self != null) {
                    self.cancel(false);
                }
            }

            /**
             * Applies the field-mode schedule from a fresh schema-valid lead floor,
             * including the zero-delay guard, and resets recovery progress state.
             */
            private void applyFieldSchedule(long leadFloorMs, long receivedNanos) {
                lastLeadFloorMs.set(leadFloorMs);
                leadFloorAtNanos.set(receivedNanos);
                zeroWindowRetried.set(false);
                lastRecoveryElapsed.set(Long.MIN_VALUE);
                lastRecoveryWindow.set(Long.MIN_VALUE);
                long nextDelay = nextFieldDelayMs(leadFloorMs, maxRttMs.get());
                if (nextDelay == 0L) {
                    if (zeroDelayStreak.getAndSet(true)) {
                        // Two consecutive zero-delay schedules: an unbounded
                        // zero-delay loop is forbidden.
                        stopAndSurface("lease is shorter than the retry-safety budget "
                                + "(consecutive zero-delay schedules)");
                    } else {
                        // ONE immediate fresh attempt with a NEW idempotency key
                        // (pendingKey was cleared by the success that got us here).
                        delayMs.set(0L);
                    }
                } else {
                    zeroDelayStreak.set(false);
                    delayMs.set(nextDelay);
                }
            }

            /** Fallback (fieldless) cadence from the observed grant — v2.3+band. */
            private void applyFallbackDelay(long appliedGrant, long elapsedSinceSuccessMs) {
                // Lead-clamp regime detection (LEAD-CLAMP REGIME above):
                // grant-derived cadence is only valid for real per-extend
                // grants, never for elapsed-time echoes of a clamped lead.
                // Both band bounds matter — a post-skip real grant can equal
                // elapsed once, but only a clamped lead TRACKS elapsed.
                boolean leadClamped = appliedGrant <= 0
                        || (appliedGrant < requestedTtlMs * 9 / 10
                            && appliedGrant >= elapsedSinceSuccessMs * 3 / 4
                            && appliedGrant <= elapsedSinceSuccessMs + elapsedSinceSuccessMs / 4);
                if (leadClamped) {
                    delayMs.set(heldCadenceMs);
                    if (leadClampWarned.compareAndSet(false, true)) {
                        LOG.warn("Heartbeat extends are not gaining lease (grantMs={} vs "
                                + "elapsedMs={} — expiry appears lead-clamped, likely budget "
                                + "depletion); holding cadence at {}ms: reservationId={}",
                                appliedGrant, elapsedSinceSuccessMs, heldCadenceMs, reservationId);
                    }
                } else {
                    delayMs.set(Math.max(500L, Math.min(appliedGrant / 2, requestedTtlMs / 2)));
                }
            }

            /**
             * Field-mode recovery after a timeout, connection error, 5xx, 429, or
             * ambiguous 2xx. Recomputes the lead estimate and retry window from the
             * last schema-valid response on EVERY failure; retries the SAME key only
             * while a complete attempt plus margin provably fits.
             */
            private void fieldRecovery(Integer retryAfterMs, boolean rateLimited) {
                long elapsedSinceLeadMs = ceilToMs(nanoClock.getAsLong() - leadFloorAtNanos.get());
                long currentLeadMs = Math.max(0L, lastLeadFloorMs.get() - elapsedSinceLeadMs);
                long retryWindow = retryWindowMs(currentLeadMs, maxRttMs.get());
                long prevElapsed = lastRecoveryElapsed.getAndSet(elapsedSinceLeadMs);
                long prevWindow = lastRecoveryWindow.getAndSet(retryWindow);
                if (prevElapsed != Long.MIN_VALUE
                        && elapsedSinceLeadMs <= prevElapsed && retryWindow >= prevWindow) {
                    // Progress guard: a zero-time recovery loop is forbidden.
                    stopAndSurface("no progress between consecutive extend recovery attempts");
                } else if (retryWindow < 0L) {
                    stopAndSurface("no complete extend retry plus safety margin fits the remaining "
                            + "lease (retryWindowMs=" + retryWindow + ")");
                } else if (rateLimited) {
                    if (retryAfterMs == null || retryAfterMs > retryWindow) {
                        // Never invent an earlier retry that violates throttling.
                        stopAndSurface("429 Retry-After is missing, invalid, or exceeds the safe "
                                + "retry window (retryAfterMs=" + retryAfterMs
                                + ", retryWindowMs=" + retryWindow + ")");
                    } else {
                        delayMs.set(retryAfterMs.longValue());
                    }
                } else if (retryWindow == 0L) {
                    if (zeroWindowRetried.getAndSet(true)) {
                        stopAndSurface("retry window still zero after an immediate recovery retry");
                    } else {
                        delayMs.set(0L);
                    }
                } else {
                    delayMs.set(Math.min(30_000L, Math.min(currentLeadMs / 4, retryWindow)));
                }
            }

            /** Permanent, irreversible failures stop the heartbeat in both modes. */
            private boolean handledPermanently(CyclesResponse<Map<String, Object>> extendResponse) {
                ErrorCode errorCode = extractErrorCode(extendResponse);
                if (extendResponse.getStatus() == 410
                        || errorCode == ErrorCode.RESERVATION_EXPIRED
                        || errorCode == ErrorCode.RESERVATION_FINALIZED
                        || errorCode == ErrorCode.MAX_EXTENSIONS_EXCEEDED
                        || errorCode == ErrorCode.TENANT_CLOSED
                        || errorCode == ErrorCode.NOT_FOUND) {
                    stopped.set(true);
                    LOG.warn("Heartbeat extend failed permanently, stopping heartbeat: "
                            + "reservationId={}, status={}, errorCode={}",
                            reservationId, extendResponse.getStatus(), errorCode);
                    ScheduledFuture<?> self = selfRef.get();
                    if (self != null) {
                        self.cancel(false);
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void run() {
                if (stopped.get()) {
                    return;
                }
                long elapsedMs = ceilToMs(nanoClock.getAsLong() - anchorNanos);
                long leadMinMs = grantsSum.get() - elapsedMs;
                Long grant = lastGrant.get();
                // Field mode bypasses the heuristic skip: scheduling from
                // remaining_ttl_ms is exact, so a skip could overshoot the real lease.
                boolean fieldMode = lastLeadFloorMs.get() >= 0L;
                if (!fieldMode && grant != null && leadMinMs >= grant + grant / 2) {
                    LOG.debug("Skipping heartbeat beat, expiry lead still ample: reservationId={}, leadMinMs={}",
                            reservationId, leadMinMs);
                } else {
                    try {
                        // Reuse the pending idempotency key after a failure so a lost response
                        // replays instead of double-extending; regenerate only after a success.
                        String key = pendingKey.get();
                        if (key == null) {
                            key = UUID.randomUUID().toString();
                            pendingKey.set(key);
                        }
                        LOG.debug("Sending heartbeat extend: reservationId={}, leadMinMs={}",
                                reservationId, leadMinMs);
                        // Copy so the reused key lands in the body; the client mirrors the body's
                        // idempotency_key into the X-Idempotency-Key header, keeping them consistent.
                        Map<String, Object> extendBody =
                                new LinkedHashMap<>(requestBuilderService.buildExtend(requestedTtlMs, null));
                        extendBody.put(Constants.IDEMPOTENCY_KEY, key);
                        long sentNanos = nanoClock.getAsLong();
                        CyclesResponse<Map<String, Object>> extendResponse =
                                client.extendReservation(reservationId, extendBody);
                        long receivedNanos = nanoClock.getAsLong();
                        // Per-attempt rtt, rounded UP so rounding cannot consume margin.
                        long rttMs = ceilToMs(receivedNanos - sentNanos);
                        maxRttMs.accumulateAndGet(rttMs, Math::max);
                        ExtendResult extResult = extendResponse.is2xx()
                                ? ExtendResult.fromMap(extendResponse.getBody()) : null;
                        // SUCCESS PREDICATE (field mode): only a schema-valid HTTP 200
                        // ReservationExtendResponse counts — status ACTIVE + expires_at_ms.
                        boolean schemaValid = extendResponse.getStatus() == 200
                                && extResult != null
                                && extResult.getStatus() == ExtendStatus.ACTIVE
                                && extResult.getExpiresAtMs() != null;
                        if (fieldMode) {
                            if (schemaValid) {
                                pendingKey.set(null);
                                long newExpiresAtMs = extResult.getExpiresAtMs();
                                long appliedGrant = newExpiresAtMs - prevExpiry.getAndSet(newExpiresAtMs);
                                long nowNanos = nanoClock.getAsLong();
                                long elapsedSinceSuccessMs =
                                        ceilToMs(nowNanos - lastSuccessNanos.getAndSet(nowNanos));
                                // Grants/leadMin bookkeeping runs in BOTH modes so the
                                // fallback can take over if the field disappears.
                                long creditedGrant = Math.max(appliedGrant, 0L);
                                grantsSum.addAndGet(creditedGrant);
                                lastGrant.set(creditedGrant);
                                ctx.updateExpiresAtMs(newExpiresAtMs);
                                Long remainingTtlMs = extResult.getRemainingTtlMs();
                                if (remainingTtlMs != null) {
                                    applyFieldSchedule(Math.max(0L, remainingTtlMs - rttMs), receivedNanos);
                                } else {
                                    // Field disappeared -> the fallback heuristic takes over.
                                    lastLeadFloorMs.set(-1L);
                                    zeroDelayStreak.set(false);
                                    applyFallbackDelay(appliedGrant, elapsedSinceSuccessMs);
                                }
                                LOG.debug("Heartbeat extend successful (field mode): reservationId={}, "
                                        + "newExpiresAtMs={}, remainingTtlMs={}, nextDelayMs={}",
                                        reservationId, newExpiresAtMs, remainingTtlMs, delayMs.get());
                            } else if (!extendResponse.is2xx() && handledPermanently(extendResponse)) {
                                // stopped inside handledPermanently
                            } else if (!extendResponse.is2xx() && extendResponse.is4xx()
                                    && extendResponse.getStatus() != 429) {
                                // Any other 4xx: request/authorization failure — never
                                // rotate the key and retry an unchanged request.
                                stopAndSurface("extend rejected with status "
                                        + extendResponse.getStatus() + "; not retrying an unchanged request");
                            } else {
                                // Transient: timeout/connection/5xx/429/ambiguous 2xx.
                                boolean rateLimited = extendResponse.getStatus() == 429;
                                if (extendResponse.is2xx()) {
                                    LOG.warn("Heartbeat extend returned an ambiguous 2xx (not a schema-valid "
                                            + "200 ReservationExtendResponse); treating as a transient failure: "
                                            + "reservationId={}, status={}",
                                            reservationId, extendResponse.getStatus());
                                }
                                fieldRecovery(rateLimited ? extendResponse.getRetryAfterMs() : null, rateLimited);
                            }
                        } else if (extendResponse.is2xx()) {
                            // Fallback path keeps its 2xx-as-applied behavior.
                            pendingKey.set(null);
                            if (extResult == null || extResult.getStatus() != ExtendStatus.ACTIVE) {
                                LOG.warn("Heartbeat extend returned 2xx with unexpected status, treating as applied: "
                                        + "reservationId={}, status={}", reservationId,
                                        extResult != null ? extResult.getStatus() : null);
                            }
                            Long newExpiresAtMs = extResult != null ? extResult.getExpiresAtMs() : null;
                            long prev = prevExpiry.get();
                            // The observed grant is the difference of two successive
                            // server-frame expires_at_ms values; when the response omits
                            // expires_at_ms assume the conservative requested-ttl grant.
                            long appliedGrant = newExpiresAtMs != null
                                    ? newExpiresAtMs - prev : requestedTtlMs;
                            long resolvedExpiry = newExpiresAtMs != null
                                    ? newExpiresAtMs : prev + requestedTtlMs;
                            prevExpiry.set(resolvedExpiry);
                            long nowNanos = nanoClock.getAsLong();
                            long elapsedSinceSuccessMs =
                                    ceilToMs(nowNanos - lastSuccessNanos.getAndSet(nowNanos));
                            Long remainingTtlMs = extResult != null ? extResult.getRemainingTtlMs() : null;
                            if (schemaValid && remainingTtlMs != null) {
                                // A schema-valid 200 carrying the field (re)enters field mode.
                                applyFieldSchedule(Math.max(0L, remainingTtlMs - rttMs), receivedNanos);
                            } else {
                                applyFallbackDelay(appliedGrant, elapsedSinceSuccessMs);
                            }
                            long creditedGrant = Math.max(appliedGrant, 0L);
                            grantsSum.addAndGet(creditedGrant);
                            lastGrant.set(creditedGrant);
                            ctx.updateExpiresAtMs(resolvedExpiry);
                            LOG.debug("Heartbeat extend successful: reservationId={}, newExpiresAtMs={}, "
                                    + "grantMs={}, remainingTtlMs={}, nextDelayMs={}",
                                    reservationId, resolvedExpiry, appliedGrant, remainingTtlMs, delayMs.get());
                        } else if (!handledPermanently(extendResponse)) {
                            LOG.warn("Heartbeat extend failed, will retry next beat with the same "
                                    + "idempotency key: reservationId={}, status={}, error={}, nextDelayMs={}",
                                    reservationId, extendResponse.getStatus(),
                                    extendResponse.getErrorMessage(), delayMs.get());
                        }
                    } catch (Exception e) {
                        if (fieldMode) {
                            LOG.warn("Heartbeat extend error in field mode, applying recovery: reservationId={}",
                                    reservationId, e);
                            fieldRecovery(null, false);
                        } else {
                            LOG.warn("Heartbeat extend error, will retry next beat with the same idempotency key: "
                                    + "reservationId={}", reservationId, e);
                        }
                    }
                }
                if (!stopped.get()) {
                    selfRef.set(heartbeatExecutor.schedule(this, delayMs.get(), TimeUnit.MILLISECONDS));
                }
            }
        };
        selfRef.set(heartbeatExecutor.schedule(beat, firstDelayMs, TimeUnit.MILLISECONDS));
        return () -> {
            stopped.set(true);
            ScheduledFuture<?> f = selfRef.get();
            if (f != null) {
                f.cancel(false);
            }
        };
    }

    // -------------------------
    // remaining_ttl_ms schedule arithmetic (overflow-safe milliseconds; positive
    // overflow saturates to +infinity, never wraps — per HEARTBEAT GUIDANCE)
    // -------------------------

    /** Nanos to ms rounded UP (rtts and elapsed feeding budgets/lead bounds round up). */
    private static long ceilToMs(long nanos) {
        return nanos <= 0L ? 0L : (nanos - 1L) / 1_000_000L + 1L;
    }

    private static long satAdd(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static long satMul(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * {@code attempt_budget = max(request_timeout_budget, 1s, 2 x maxObservedRtt)}.
     * An unknown/unbounded per-attempt timeout makes the budget positive infinity,
     * which forces every field-mode delay to 0 (the spec's conservative posture:
     * unknown timing is never treated as zero elapsed time).
     */
    private long attemptBudgetMs(long maxRttMs) {
        long timeout = requestTimeoutBudgetMs != null ? requestTimeoutBudgetMs : Long.MAX_VALUE;
        return Math.max(Math.max(timeout, 1000L), satMul(2L, maxRttMs));
    }

    /** {@code safety_margin = max(1s, 2 x maxObservedRtt)}. */
    private static long safetyMarginMs(long maxRttMs) {
        return Math.max(1000L, satMul(2L, maxRttMs));
    }

    /**
     * {@code next_delay = max(0, lead_floor - (2 x attempt_budget + safety_margin))} —
     * the reserve covers one failed attempt, one same-key retry, and margin.
     */
    private long nextFieldDelayMs(long leadFloorMs, long maxRttMs) {
        long retryReserve = satAdd(satMul(2L, attemptBudgetMs(maxRttMs)), safetyMarginMs(maxRttMs));
        return leadFloorMs > retryReserve ? leadFloorMs - retryReserve : 0L;
    }

    /**
     * {@code retry_window = current_lead_estimate - attempt_budget - safety_margin},
     * deliberately UNclamped — a negative window proves no complete retry plus
     * margin fits and the caller must stop.
     */
    private long retryWindowMs(long currentLeadMs, long maxRttMs) {
        try {
            return Math.subtractExact(Math.subtractExact(currentLeadMs, attemptBudgetMs(maxRttMs)),
                    safetyMarginMs(maxRttMs));
        } catch (ArithmeticException e) {
            return Long.MIN_VALUE;
        }
    }

    private void cancelHeartbeat(Runnable heartbeatCanceller) {
        if (heartbeatCanceller != null) {
            heartbeatCanceller.run();
        }
    }

    // -------------------------
    // Helpers
    // -------------------------
    private long resolveActualAmount(Cycles cycles, long estimate,
                                     Method method, Object[] args,
                                     Object result, Object target) {
        if (!cycles.actual().isBlank()) {
            return evaluator.evaluate(cycles.actual(), method, args, result, target);
        } else if (cycles.useEstimateIfActualNotProvided()) {
            LOG.debug("No actual expression provided; committing the estimate as actual "
                    + "(marked with metadata actual_source=estimate): estimate={}", estimate);
            return estimate;
        } else {
            LOG.error("Actual usage amount is missing that is required");
            throw new IllegalStateException("Actual expression required");
        }
    }

    private Map<String, Object> resolveCommitMetadata(Cycles cycles,
                                                       Method method,
                                                       Object[] args,
                                                       Object result,
                                                       Object target,
                                                       Map<String, Object> contextMetadata) {
        Map<String, Object> annotationMetadata = evaluateAnnotationCommitMetadata(
                cycles, method, args, result, target);

        boolean hasAnnotationMetadata = annotationMetadata != null && !annotationMetadata.isEmpty();
        boolean hasContextMetadata = contextMetadata != null && !contextMetadata.isEmpty();

        if (!hasAnnotationMetadata) {
            return contextMetadata;
        }
        if (!hasContextMetadata) {
            return annotationMetadata;
        }

        Map<String, Object> merged = new LinkedHashMap<>(annotationMetadata);
        merged.putAll(contextMetadata);
        return merged;
    }

    private Map<String, Object> evaluateAnnotationCommitMetadata(Cycles cycles,
                                                                  Method method,
                                                                  Object[] args,
                                                                  Object result,
                                                                  Object target) {
        String metadataExpression = cycles.metadata();
        if (metadataExpression == null || metadataExpression.isBlank()) {
            return null;
        }
        try {
            return evaluator.evaluateMap(metadataExpression, method, args, result, target);
        } catch (Exception e) {
            LOG.warn("Skipping @Cycles commit metadata after SpEL evaluation failure: method={}, expression={}",
                    method, metadataExpression, e);
            return null;
        }
    }

    private static String resolveEstimateExpression(Cycles cycles) {
        boolean hasValue = !cycles.value().isBlank();
        boolean hasEstimate = !cycles.estimate().isBlank();
        if (hasValue && hasEstimate) {
            throw new IllegalStateException("@Cycles: set value or estimate, not both");
        }
        if (!hasValue && !hasEstimate) {
            throw new IllegalStateException("@Cycles: value or estimate is required");
        }
        return hasValue ? cycles.value() : cycles.estimate();
    }

    private CyclesProtocolException buildProtocolException(String prefix, CyclesResponse<Map<String, Object>> response) {
        ErrorResponse errorResponse = response.getErrorResponse();
        ErrorCode errorCode;
        String reasonCode = null;
        String message;

        if (errorResponse != null) {
            errorCode = errorResponse.getErrorCode();
            reasonCode = errorResponse.getErrorCode() != null ? errorResponse.getErrorCode().name() : null;
            String serverMessage = errorResponse.getMessage();
            message = prefix + ": " + (serverMessage != null ? serverMessage : "unknown error");
            String requestId = errorResponse.getRequestId();
            if (requestId != null) {
                LOG.error("Server error response: requestId={}, errorCode={}, status={}", requestId, errorCode, response.getStatus());
            }
        } else {
            errorCode = ErrorCode.fromString(response.getBodyAttributeAsString("error"));
            message = prefix + ": " + (response.getErrorMessage() != null ? response.getErrorMessage() : "unknown error");
        }

        return new CyclesProtocolException(message, errorCode, reasonCode, response.getStatus());
    }

    private ErrorCode extractErrorCode(CyclesResponse<Map<String, Object>> response) {
        ErrorResponse errorResponse = response.getErrorResponse();
        if (errorResponse != null && errorResponse.getErrorCode() != null) {
            return errorResponse.getErrorCode();
        }
        String errorCodeStr = response.getBodyAttributeAsString("error");
        return ErrorCode.fromString(errorCodeStr);
    }

    /**
     * A supplier that may throw checked exceptions.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        /**
         * Gets the result.
         *
         * @return the result
         * @throws Throwable if unable to compute a result
         */
        T get() throws Throwable;
    }
}
