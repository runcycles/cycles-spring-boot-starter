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
     * Creates a new lifecycle service with the given dependencies.
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
        this(client, retryEngine, requestBuilderService, evaluator,
                Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r);
                    t.setDaemon(true);
                    t.setName("cycles-heartbeat");
                    return t;
                }));
    }

    // Visible for testing
    CyclesLifecycleService(CyclesClient client,
                           CommitRetryEngine retryEngine,
                           CyclesRequestBuilderService requestBuilderService,
                           CyclesExpressionEvaluator evaluator,
                           ScheduledExecutorService heartbeatExecutor) {
        this(client, retryEngine, requestBuilderService, evaluator, heartbeatExecutor, System::nanoTime);
    }

    // Visible for testing — injectable monotonic clock for deterministic heartbeat tests
    CyclesLifecycleService(CyclesClient client,
                           CommitRetryEngine retryEngine,
                           CyclesRequestBuilderService requestBuilderService,
                           CyclesExpressionEvaluator evaluator,
                           ScheduledExecutorService heartbeatExecutor,
                           LongSupplier nanoClock) {
        this.client = client;
        this.retryEngine = retryEngine;
        this.requestBuilderService = requestBuilderService;
        this.evaluator = evaluator;
        this.heartbeatExecutor = heartbeatExecutor;
        this.nanoClock = nanoClock;
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
        CyclesResponse<Map<String, Object>> reservationResponse = client.createReservation(createBody);

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

        ScheduledFuture<?> heartbeatFuture = scheduleHeartbeat(
                reservationId, cycles.ttlMs(), expiresAtMs, reservationResponse.getDateMs(), ctx);

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
            cancelHeartbeat(heartbeatFuture);
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
    private ScheduledFuture<?> scheduleHeartbeat(String reservationId, long requestedTtlMs,
                                                  Long expiresAtMs, Long serverDateMs,
                                                  CyclesReservationContext ctx) {
        if (expiresAtMs == null || requestedTtlMs <= 0) {
            return null;
        }
        // Tenant policy max_reservation_ttl_ms silently CAPS the granted TTL at reserve
        // time (governance default 1 hour) and the create response has no effective-TTL
        // field. Seeding the beat from the requested ttl would schedule the first tick far
        // too late (a 24h request capped to 1h would first beat at 12h — long after
        // expiry). Recover the effective TTL from the reserve response itself:
        // expires_at_ms − Date header, BOTH server-frame timestamps, so the difference is
        // clock-skew-free. Clamp to [1000, requestedTtl] to defuse a mangled Date header.
        final long ttlMs = serverDateMs != null
                ? Math.max(1000L, Math.min(expiresAtMs - serverDateMs, requestedTtlMs))
                : requestedTtlMs;
        // No floor: spec ttl_ms minimum is 1000, so the interval is at least 500ms. A floor
        // above ttl/2 would guarantee a lapse for small TTLs (tick after expiry).
        long intervalMs = ttlMs / 2;
        LOG.debug("Scheduling heartbeat: reservationId={}, effectiveTtlMs={}, intervalMs={}",
                reservationId, ttlMs, intervalMs);
        // Lead-estimate extension: extend_by_ms is relative to the CURRENT expires_at_ms,
        // so extending ttlMs on every ttl/2 tick would drift expiry ahead by +ttl/2 per beat
        // (a zombie budget lockup if the process dies) and burn the server's capped
        // extension_count twice as fast as needed. Each tick estimates how far expiry leads
        // "now" and only extends when the lead has dropped below 1.5*ttl:
        //
        //   leadMs = (knownExpiry - initialExpiry) + ttlMs - elapsedMs
        //
        // This is clock-skew-free: (knownExpiry - initialExpiry) is a difference of two
        // SERVER-frame timestamps and elapsedMs is a difference of two CLIENT-monotonic
        // readings — the client's wall clock is never compared with the server's wall clock.
        // scheduleAtFixedRate catch-up ticks self-correct: a zero-gap queued tick sees the
        // lead unchanged-high and skips, so a slow extend can never double-extend.
        //
        // Failure handling: a failed extend keeps its idempotency key and retries it on the
        // next tick, so a lost response can never double-extend. Permanent failures
        // (410/RESERVATION_EXPIRED, RESERVATION_FINALIZED, MAX_EXTENSIONS_EXCEEDED,
        // TENANT_CLOSED, NOT_FOUND) stop the heartbeat for good — all irreversible; no
        // amount of retrying can revive those.
        final long initialExpiry = expiresAtMs;
        final long anchorNanos = nanoClock.getAsLong();
        AtomicLong knownExpiry = new AtomicLong(initialExpiry);
        AtomicReference<String> pendingKey = new AtomicReference<>();
        AtomicReference<ScheduledFuture<?>> selfRef = new AtomicReference<>();
        AtomicBoolean stopped = new AtomicBoolean(false);
        ScheduledFuture<?> future = heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (stopped.get()) {
                return;
            }
            long elapsedMs = (nanoClock.getAsLong() - anchorNanos) / 1_000_000L;
            long leadMs = (knownExpiry.get() - initialExpiry) + ttlMs - elapsedMs;
            if (leadMs >= ttlMs + ttlMs / 2) {
                LOG.debug("Skipping heartbeat tick, expiry lead still ample: reservationId={}, leadMs={}",
                        reservationId, leadMs);
                return;
            }
            try {
                // Reuse the pending idempotency key after a failure so a lost response
                // replays instead of double-extending; regenerate only after a 2xx.
                String key = pendingKey.get();
                if (key == null) {
                    key = UUID.randomUUID().toString();
                    pendingKey.set(key);
                }
                LOG.debug("Sending heartbeat extend: reservationId={}, leadMs={}", reservationId, leadMs);
                // Copy so the reused key lands in the body; the client mirrors the body's
                // idempotency_key into the X-Idempotency-Key header, keeping them consistent.
                Map<String, Object> extendBody = new LinkedHashMap<>(requestBuilderService.buildExtend(ttlMs, null));
                extendBody.put(Constants.IDEMPOTENCY_KEY, key);
                CyclesResponse<Map<String, Object>> extendResponse = client.extendReservation(reservationId, extendBody);
                if (extendResponse.is2xx()) {
                    pendingKey.set(null);
                    ExtendResult extResult = ExtendResult.fromMap(extendResponse.getBody());
                    if (extResult == null || extResult.getStatus() != ExtendStatus.ACTIVE) {
                        LOG.warn("Heartbeat extend returned 2xx with unexpected status, treating as applied: "
                                + "reservationId={}, status={}", reservationId,
                                extResult != null ? extResult.getStatus() : null);
                    }
                    Long newExpiresAtMs = extResult != null ? extResult.getExpiresAtMs() : null;
                    long resolvedExpiry = newExpiresAtMs != null ? newExpiresAtMs : knownExpiry.get() + ttlMs;
                    knownExpiry.set(resolvedExpiry);
                    ctx.updateExpiresAtMs(resolvedExpiry);
                    LOG.debug("Heartbeat extend successful: reservationId={}, newExpiresAtMs={}",
                            reservationId, resolvedExpiry);
                } else {
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
                    } else {
                        LOG.warn("Heartbeat extend failed, will retry next tick with the same idempotency key: "
                                + "reservationId={}, status={}, error={}",
                                reservationId, extendResponse.getStatus(), extendResponse.getErrorMessage());
                    }
                }
            } catch (Exception e) {
                LOG.warn("Heartbeat extend error, will retry next tick with the same idempotency key: "
                        + "reservationId={}", reservationId, e);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        selfRef.set(future);
        return future;
    }

    private void cancelHeartbeat(ScheduledFuture<?> heartbeatFuture) {
        if (heartbeatFuture != null) {
            heartbeatFuture.cancel(false);
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
