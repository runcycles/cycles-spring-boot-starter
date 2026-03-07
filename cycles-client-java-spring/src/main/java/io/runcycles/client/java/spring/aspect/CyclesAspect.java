package io.runcycles.client.java.spring.aspect;

import io.runcycles.client.java.spring.annotation.Cycles;
import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.config.CyclesProperties;
import io.runcycles.client.java.spring.context.CyclesRequestBuilderService;
import io.runcycles.client.java.spring.evaluation.CyclesExpressionEvaluator;
import io.runcycles.client.java.spring.model.*;
import io.runcycles.client.java.spring.retry.CommitRetryEngine;

import io.runcycles.client.java.spring.context.CyclesContextHolder;
import io.runcycles.client.java.spring.context.CyclesReservationContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Aspect
public class CyclesAspect {
    private static final Logger LOG = LoggerFactory.getLogger(CyclesAspect.class);

    private final CyclesClient client;
    private final CommitRetryEngine retryEngine;
    private final CyclesExpressionEvaluator evaluator;
    private final CyclesRequestBuilderService cyclesRequestBuilderService;
    private final CyclesProperties cyclesConfiguration;
    private final ScheduledExecutorService heartbeatExecutor;

    public CyclesAspect(CyclesClient client,
                        CommitRetryEngine retryEngine,
                        CyclesRequestBuilderService cyclesRequestBuilderService,
                        CyclesExpressionEvaluator evaluator,
                        CyclesProperties cyclesConfiguration) {
        this.client = client;
        this.retryEngine = retryEngine;
        this.cyclesRequestBuilderService = cyclesRequestBuilderService;
        this.evaluator = evaluator;
        this.cyclesConfiguration = cyclesConfiguration;
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("cycles-heartbeat");
            return t;
        });
    }

    @Around("@annotation(cycles)")
    public Object around(ProceedingJoinPoint pjp, Cycles cycles) throws Throwable {
        LOG.info("Cycles aspect flow start: cycles={}", cycles);
        long t1 = System.currentTimeMillis();
        if (CyclesContextHolder.get() != null) {
            LOG.error("Nested annotation usage not supported");
            throw new IllegalStateException("Nested @Cycles not supported");
        }

        Method method = ((MethodSignature) pjp.getSignature()).getMethod();

        // Resolve actionKind/actionName: annotation value or derive from class/method
        String actionKind = cycles.actionKind().isBlank()
                ? pjp.getTarget().getClass().getSimpleName()
                : cycles.actionKind();
        String actionName = cycles.actionName().isBlank()
                ? method.getName()
                : cycles.actionName();

        // Resolve estimate expression: value() and estimate() are synonyms
        String estimateExpr = resolveEstimateExpression(cycles);

        long estimate = evaluator.evaluate(
                estimateExpr,
                method,
                pjp.getArgs(),
                null,
                pjp.getTarget()
        );
        LOG.info("Estimated usage: estimate={}", estimate);

        Map<String, Object> createBody = cyclesRequestBuilderService.buildReservation(
                cycles, estimate, actionKind, actionName, null);

        LOG.info("Creating reservation: createBody={}", createBody);
        long resT1 = System.currentTimeMillis();
        CyclesResponse<Map<String, Object>> reservationResponse = client.createReservation(createBody);

        if (!reservationResponse.is2xx()) {
            LOG.error("Reservation failed, aborting: reservationResponse={}", reservationResponse);
            throw buildProtocolException("Failed to create reservation", reservationResponse);
        }

        // Parse full reservation response
        Map<String, Object> resBody = reservationResponse.getBody();
        String reservationId = extractReservationId(reservationResponse);
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
        List<Map<String, Object>> balances = resBody.get("balances") instanceof List<?> l
                ? (List<Map<String, Object>>) l : null;

        long resT2 = System.currentTimeMillis();

        // dry_run: server evaluates but does not persist — no reservation_id, no commit/release.
        // Method does NOT execute per spec; no commit/release needed.
        // Per spec, decision MAY be DENY on dry_run 200 responses, so this must be checked first.
        if (cycles.dryRun()) {
            LOG.info("Dry-run reservation evaluated: elapsedTime={}ms, decision={}, caps={}, affectedScopes={}",
                    (resT2 - resT1), decision, caps, affectedScopes);
            if (decision == Decision.DENY) {
                throw new CyclesProtocolException(
                        "Dry-run denied: " + (reasonCode != null ? reasonCode : "BUDGET_EXCEEDED"),
                        ErrorCode.fromString(reasonCode != null ? reasonCode : "BUDGET_EXCEEDED"),
                        reasonCode,
                        reservationResponse.getStatus()
                );
            }
            return null;
        }

        if (decision == Decision.DENY) {
            LOG.error("Reservation denied: decision=DENY, reasonCode={}, response={}", reasonCode, resBody);
            throw new CyclesProtocolException(
                    "Reservation denied: " + (reasonCode != null ? reasonCode : "BUDGET_EXCEEDED"),
                    ErrorCode.fromString(reasonCode != null ? reasonCode : "BUDGET_EXCEEDED"),
                    reasonCode,
                    reservationResponse.getStatus()
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

        CyclesReservationContext ctx = new CyclesReservationContext(
                reservationId, estimate, decision, caps, expiresAtMs,
                affectedScopes, scopePath, balances);
        CyclesContextHolder.set(ctx);

        // Start heartbeat if we have an expiry
        ScheduledFuture<?> heartbeatFuture = scheduleHeartbeat(reservationId, cycles.ttlMs(), expiresAtMs);

        try {
            Object result = pjp.proceed();
            long methodElapsed = System.currentTimeMillis() - resT2;
            LOG.info("Annotated method finished: reservationId={}, methodElapsedMs={}", reservationId, methodElapsed);

            long actualAmount;
            if (!cycles.actual().isBlank()) {
                actualAmount = evaluator.evaluate(
                        cycles.actual(),
                        method,
                        pjp.getArgs(),
                        result,
                        pjp.getTarget()
                );
            } else if (cycles.useEstimateIfActualNotProvided()) {
                actualAmount = estimate;
            } else {
                LOG.error("Actual usage amount is missing that is required");
                throw new IllegalStateException("Actual expression required");
            }

            // Pick up metrics and metadata from context (user may have set them during execution)
            CyclesMetrics metrics = ctx.getMetrics();
            if (metrics == null) {
                metrics = new CyclesMetrics();
            }
            // Auto-populate latency if not already set
            if (metrics.getLatencyMs() == null) {
                metrics.setLatencyMs((int) methodElapsed);
            }
            Map<String, Object> commitMetadata = ctx.getCommitMetadata();

            Map<String, Object> commitBody = cyclesRequestBuilderService.buildCommit(
                    cycles, actualAmount, metrics, commitMetadata);

            try {
                LOG.info("Committing reservation: reservationId={}, commitBody={}", reservationId, commitBody);
                long comT1 = System.currentTimeMillis();
                CyclesResponse<Map<String, Object>> commitResponse = client.commitReservation(reservationId, commitBody);
                long comT2 = System.currentTimeMillis();
                LOG.info("Commit done: elapsedTime={}ms, response={}", (comT2 - comT1), commitResponse);

                if (commitResponse.is2xx()) {
                    LOG.info("Commit successful: reservationId={}", reservationId);
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
                        handleReleaseReservation(reservationId, "commit_rejected_" + commitErrorCode);
                    } else {
                        LOG.warn("Unrecognized response: response={}", commitResponse);
                    }
                }

            } catch (Exception e) {
                LOG.error("Failed to commit reservation: reservationId={}", reservationId, e);
                retryEngine.schedule(reservationId, commitBody);
            }

            long t2 = System.currentTimeMillis();
            LOG.info("Cycles aspect flow finished: elapsedTime={}ms, cycles={}", (t2 - t1), cycles);
            return result;

        } catch (Throwable ex) {
            LOG.error("Guarded method execution failed, releasing reservation: reservationId={}, cycles={}",
                    reservationId, cycles, ex);
            handleReleaseReservation(reservationId, "guarded_method_failed");
            throw ex;
        } finally {
            cancelHeartbeat(heartbeatFuture);
            CyclesContextHolder.clear();
        }
    }

    // -------------------------
    // Heartbeat
    // -------------------------
    private ScheduledFuture<?> scheduleHeartbeat(String reservationId, long ttlMs, Long expiresAtMs) {
        if (expiresAtMs == null || ttlMs <= 0) {
            return null;
        }
        long intervalMs = Math.max(ttlMs / 2, 1000);
        LOG.info("Scheduling heartbeat: reservationId={}, intervalMs={}", reservationId, intervalMs);
        return heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                LOG.debug("Sending heartbeat extend: reservationId={}", reservationId);
                Map<String, Object> extendBody = cyclesRequestBuilderService.buildExtend(ttlMs, null);
                CyclesResponse<Map<String, Object>> extendResponse = client.extendReservation(reservationId, extendBody);
                if (extendResponse.is2xx()) {
                    LOG.debug("Heartbeat extend successful: reservationId={}", reservationId);
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
    // Error handling
    // -------------------------
    private CyclesProtocolException buildProtocolException(String prefix, CyclesResponse<Map<String, Object>> response) {
        ErrorCode errorCode = extractErrorCode(response);
        // ErrorResponse uses "error" field (the ErrorCode), not "reason_code" (which is only in 2xx responses)
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

    // -------------------------
    // Helpers
    // -------------------------
    private String resolveEstimateExpression(Cycles cycles) {
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

    private String extractReservationId(CyclesResponse<Map<String, Object>> response) {
        return response.getBodyAttributeAsString("reservation_id");
    }

    private void handleReleaseReservation(String reservationId, String reason) {
        try {
            LOG.info("Releasing reservation: reservationId={}, reason={}", reservationId, reason);
            CyclesResponse<Map<String, Object>> releaseResponse = client.releaseReservation(reservationId,
                    cyclesRequestBuilderService.buildRelease(reason));
            if (releaseResponse.is2xx()) {
                LOG.info("Reservation released successfully: reservationId={}", reservationId);
            } else {
                LOG.warn("Reservation release failed: reservationId={}, errorMessage={}, responseBody={}",
                        reservationId, releaseResponse.getErrorMessage(), releaseResponse.getBody());
            }
        } catch (Exception ignored) {
            LOG.error("Failed to release reservation: reservationId={}", reservationId, ignored);
        }
    }
}
