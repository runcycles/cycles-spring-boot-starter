package io.runcycles.client.java.spring.context;

import io.runcycles.client.java.spring.annotation.Cycles;
import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.evaluation.CyclesExpressionEvaluator;
import io.runcycles.client.java.spring.model.*;
import io.runcycles.client.java.spring.retry.CommitRetryEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

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

    public CyclesLifecycleService(CyclesClient client,
                                  CommitRetryEngine retryEngine,
                                  CyclesRequestBuilderService requestBuilderService,
                                  CyclesExpressionEvaluator evaluator) {
        this.client = client;
        this.retryEngine = retryEngine;
        this.requestBuilderService = requestBuilderService;
        this.evaluator = evaluator;
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("cycles-heartbeat");
            return t;
        });
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
                cycles, estimate, actionKind, actionName, null);
        LOG.debug("Creating reservation: createBody={}", createBody);

        long resT1 = System.currentTimeMillis();
        CyclesResponse<Map<String, Object>> reservationResponse = client.createReservation(createBody);

        if (!reservationResponse.is2xx()) {
            LOG.error("Reservation failed, aborting: reservationResponse={}", reservationResponse);
            throw buildProtocolException("Failed to create reservation", reservationResponse);
        }

        // Parse reservation response
        Map<String, Object> resBody = reservationResponse.getBody();
        String reservationId = reservationResponse.getBodyAttributeAsString("reservation_id");
        Decision decision = Decision.fromString(reservationResponse.getBodyAttributeAsString("decision"));
        String reasonCode = reservationResponse.getBodyAttributeAsString("reason_code");
        Long expiresAtMs = resBody.get("expires_at_ms") instanceof Number n ? n.longValue() : null;

        @SuppressWarnings("unchecked")
        Map<String, Object> capsMap = resBody.get("caps") instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
        Caps caps = Caps.fromMap(capsMap);

        @SuppressWarnings("unchecked")
        List<String> affectedScopes = resBody.get("affected_scopes") instanceof List<?> l
                ? (List<String>) l : List.of();
        String scopePath = resBody.get("scope_path") instanceof String s ? s : null;
        @SuppressWarnings("unchecked")
        Map<String, Object> reserved = resBody.get("reserved") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : null;
        Long retryAfterMs = resBody.get("retry_after_ms") instanceof Number n ? n.longValue() : null;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> balances = resBody.get("balances") instanceof List<?> l
                ? (List<Map<String, Object>>) l : null;

        long resT2 = System.currentTimeMillis();

        // Validate decision field
        if (decision == null) {
            String rawDecision = reservationResponse.getBodyAttributeAsString("decision");
            LOG.error("Unrecognized decision value from server: decision={}, response={}", rawDecision, resBody);
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
            return new DryRunResult(decision, caps, affectedScopes, scopePath, reserved, balances, retryAfterMs);
        }

        // Handle DENY
        if (decision == Decision.DENY) {
            LOG.error("Reservation denied: decision=DENY, reasonCode={}, retryAfterMs={}, response={}",
                    reasonCode, retryAfterMs, resBody);
            throw new CyclesProtocolException(
                    "Reservation denied: " + (reasonCode != null ? reasonCode : "BUDGET_EXCEEDED"),
                    ErrorCode.fromString(reasonCode != null ? reasonCode : "BUDGET_EXCEEDED"),
                    reasonCode,
                    reservationResponse.getStatus(),
                    retryAfterMs
            );
        }

        if (decision == Decision.ALLOW_WITH_CAPS) {
            LOG.warn("Reservation allowed with caps: caps={}, response={}", caps, resBody);
        }

        if (reservationId == null) {
            LOG.error("Reservation successful but reservation id missing: responseBody={}", resBody);
            throw new CyclesProtocolException("Failed to create reservation because of missing reservation identifier");
        }

        LOG.info("Reservation created: elapsedTime={}ms, reservationId={}, decision={}, expiresAtMs={}",
                (resT2 - resT1), reservationId, decision, expiresAtMs);

        // Set context and start heartbeat
        CyclesReservationContext ctx = new CyclesReservationContext(
                reservationId, estimate, decision, caps, expiresAtMs,
                affectedScopes, scopePath, reserved, balances);
        CyclesContextHolder.set(ctx);

        ScheduledFuture<?> heartbeatFuture = scheduleHeartbeat(reservationId, cycles.ttlMs(), expiresAtMs, ctx);

        try {
            // Execute guarded action
            Object result = action.get();
            long methodElapsed = System.currentTimeMillis() - resT2;
            LOG.debug("Guarded action finished: reservationId={}, methodElapsedMs={}", reservationId, methodElapsed);

            // Resolve actual amount
            long actualAmount = resolveActualAmount(cycles, estimate, method, args, result, target);

            // Build and send commit
            CyclesMetrics metrics = ctx.getMetrics();
            if (metrics == null) {
                metrics = new CyclesMetrics();
            }
            if (metrics.getLatencyMs() == null) {
                metrics.setLatencyMs((int) methodElapsed);
            }

            Map<String, Object> commitBody = requestBuilderService.buildCommit(
                    cycles, actualAmount, metrics, ctx.getCommitMetadata());

            handleCommit(reservationId, commitBody);

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
    private void handleCommit(String reservationId, Map<String, Object> commitBody) {
        try {
            LOG.debug("Committing reservation: reservationId={}, commitBody={}", reservationId, commitBody);
            long comT1 = System.currentTimeMillis();
            CyclesResponse<Map<String, Object>> commitResponse = client.commitReservation(reservationId, commitBody);
            long comT2 = System.currentTimeMillis();
            LOG.debug("Commit done: elapsedTime={}ms, response={}", (comT2 - comT1), commitResponse);

            if (commitResponse.is2xx()) {
                Map<String, Object> comBody = commitResponse.getBody();
                LOG.info("Commit successful: reservationId={}, status={}, charged={}, released={}",
                        reservationId,
                        comBody != null ? comBody.get("status") : null,
                        comBody != null ? comBody.get("charged") : null,
                        comBody != null ? comBody.get("released") : null);
            } else {
                LOG.error("Commit failed: reservationId={}, reason={}, responseBody={}",
                        reservationId, commitResponse.getErrorMessage(), commitResponse.getBody());
                ErrorCode commitErrorCode = extractErrorCode(commitResponse);
                if (commitResponse.isTransportError() || commitResponse.is5xx()
                        || (commitErrorCode != null && commitErrorCode.isRetryable())) {
                    retryEngine.schedule(reservationId, commitBody);
                } else if (commitErrorCode == ErrorCode.RESERVATION_FINALIZED
                        || commitErrorCode == ErrorCode.RESERVATION_EXPIRED) {
                    LOG.warn("Reservation already finalized/expired, skipping release: reservationId={}, errorCode={}",
                            reservationId, commitErrorCode);
                } else if (commitResponse.is4xx()) {
                    handleRelease(reservationId, "commit_rejected_" + commitErrorCode);
                } else {
                    LOG.warn("Unrecognized response: response={}", commitResponse);
                }
            }

        } catch (Exception e) {
            LOG.error("Failed to commit reservation: reservationId={}", reservationId, e);
            retryEngine.schedule(reservationId, commitBody);
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
    private ScheduledFuture<?> scheduleHeartbeat(String reservationId, long ttlMs,
                                                  Long expiresAtMs, CyclesReservationContext ctx) {
        if (expiresAtMs == null || ttlMs <= 0) {
            return null;
        }
        long intervalMs = Math.max(ttlMs / 2, 1000);
        LOG.debug("Scheduling heartbeat: reservationId={}, intervalMs={}", reservationId, intervalMs);
        return heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                LOG.debug("Sending heartbeat extend: reservationId={}", reservationId);
                Map<String, Object> extendBody = requestBuilderService.buildExtend(ttlMs, null);
                CyclesResponse<Map<String, Object>> extendResponse = client.extendReservation(reservationId, extendBody);
                if (extendResponse.is2xx()) {
                    Map<String, Object> extBody = extendResponse.getBody();
                    Long newExpiresAtMs = extBody != null && extBody.get("expires_at_ms") instanceof Number n
                            ? n.longValue() : null;
                    if (newExpiresAtMs != null) {
                        ctx.updateExpiresAtMs(newExpiresAtMs);
                    }
                    LOG.debug("Heartbeat extend successful: reservationId={}, newExpiresAtMs={}",
                            reservationId, newExpiresAtMs);
                } else {
                    LOG.warn("Heartbeat extend failed: reservationId={}, status={}, error={}",
                            reservationId, extendResponse.getStatus(), extendResponse.getErrorMessage());
                }
            } catch (Exception e) {
                LOG.warn("Heartbeat extend error: reservationId={}", reservationId, e);
            }
        }, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
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
            return estimate;
        } else {
            LOG.error("Actual usage amount is missing that is required");
            throw new IllegalStateException("Actual expression required");
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
        ErrorCode errorCode = extractErrorCode(response);
        String errorField = response.getBodyAttributeAsString("error");
        String requestId = response.getBodyAttributeAsString("request_id");
        String message = prefix + ": " + (response.getErrorMessage() != null ? response.getErrorMessage() : "unknown error");
        if (requestId != null) {
            LOG.error("Server error response: requestId={}, errorCode={}, status={}", requestId, errorField, response.getStatus());
        }
        return new CyclesProtocolException(message, errorCode, errorField, response.getStatus());
    }

    private ErrorCode extractErrorCode(CyclesResponse<Map<String, Object>> response) {
        String errorCodeStr = response.getBodyAttributeAsString("error");
        if (errorCodeStr == null) {
            errorCodeStr = response.getBodyAttributeAsString("reason_code");
        }
        return ErrorCode.fromString(errorCodeStr);
    }

    @FunctionalInterface
    public interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }
}
