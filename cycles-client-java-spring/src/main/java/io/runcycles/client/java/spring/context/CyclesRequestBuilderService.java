package io.runcycles.client.java.spring.context;

import io.runcycles.client.java.spring.annotation.Cycles;
import io.runcycles.client.java.spring.evaluation.CyclesValueResolutionService;
import io.runcycles.client.java.spring.model.CyclesMetrics;
import io.runcycles.client.java.spring.model.CyclesProtocolException;
import io.runcycles.client.java.spring.util.Constants;
import io.runcycles.client.java.spring.util.ValidationUtils;

import java.util.*;

public class CyclesRequestBuilderService {

    private final CyclesValueResolutionService cyclesValueResolutionService;

    public CyclesRequestBuilderService(CyclesValueResolutionService cyclesValueResolutionService) {
        this.cyclesValueResolutionService = cyclesValueResolutionService;
    }

    public Map<String, Object> buildReservation(Cycles cycles, long estimatedAmount,
                                                 String actionKind, String actionName,
                                                 Map<String, Object> metadata) {
        Objects.requireNonNull(cycles, "Cycles annotation must not be null");

        Map<String, String> resolvedFields = resolveSubjectFields(cycles);
        validateMandatory(resolvedFields, actionKind, actionName, estimatedAmount, cycles.unit());

        Map<String, Object> subject = buildSubject(resolvedFields, cycles.dimensions());
        Map<String, Object> action = buildAction(actionKind, actionName, cycles.actionTags());
        Map<String, Object> estimate = buildEstimate(cycles, estimatedAmount);

        Map<String, Object> body = new HashMap<>();
        body.put(Constants.IDEMPOTENCY_KEY, UUID.randomUUID().toString());
        body.put("subject", subject);
        body.put("action", action);
        body.put("estimate", estimate);

        if (cycles.ttlMs() > 0) {
            if (cycles.ttlMs() < 1000 || cycles.ttlMs() > 86400000) {
                throw new CyclesProtocolException(
                        "ttl_ms must be between 1000 and 86400000, got: " + cycles.ttlMs());
            }
            body.put("ttl_ms", cycles.ttlMs());
        }
        if (cycles.gracePeriodMs() >= 0) {
            if (cycles.gracePeriodMs() > 60000) {
                throw new CyclesProtocolException(
                        "grace_period_ms must be between 0 and 60000, got: " + cycles.gracePeriodMs());
            }
            body.put("grace_period_ms", cycles.gracePeriodMs());
        }

        if (!"REJECT".equals(cycles.overagePolicy())) {
            ValidationUtils.putIfNotBlank(body, "overage_policy", cycles.overagePolicy());
        }

        if (cycles.dryRun()) {
            body.put(Constants.DRY_RUN, true);
        }
        if (metadata != null && !metadata.isEmpty()) {
            body.put(Constants.METADATA, metadata);
        }

        return body;
    }

    public Map<String, Object> buildExtend(long extendByMs, Map<String, Object> metadata) {
        if (extendByMs < 1 || extendByMs > 86400000) {
            throw new CyclesProtocolException("extend_by_ms must be between 1 and 86400000, got: " + extendByMs);
        }
        Map<String, Object> body = new HashMap<>();
        body.put(Constants.IDEMPOTENCY_KEY, UUID.randomUUID().toString());
        body.put("extend_by_ms", extendByMs);
        if (metadata != null && !metadata.isEmpty()) {
            body.put(Constants.METADATA, metadata);
        }
        return body;
    }

    public Map<String, Object> buildRelease(String reason) {
        Map<String, Object> body = new HashMap<>();
        body.put(Constants.IDEMPOTENCY_KEY, UUID.randomUUID().toString());
        ValidationUtils.putIfNotBlank(body, "reason", reason);
        return body;
    }

    public Map<String, Object> buildCommit(Cycles cycles, long actualAmount,
                                            CyclesMetrics metrics, Map<String, Object> metadata) {
        Objects.requireNonNull(cycles, "Cycles annotation must not be null");

        Map<String, Object> body = new HashMap<>();
        body.put(Constants.IDEMPOTENCY_KEY, UUID.randomUUID().toString());
        body.put("actual", buildActual(cycles, actualAmount));

        if (metrics != null && !metrics.isEmpty()) {
            body.put(Constants.METRICS, metrics.toMap());
        }
        if (metadata != null && !metadata.isEmpty()) {
            body.put(Constants.METADATA, metadata);
        }

        return body;
    }

    public Map<String, Object> buildDecision(Cycles cycles, long estimatedAmount,
                                              String actionKind, String actionName,
                                              Map<String, Object> metadata) {
        Objects.requireNonNull(cycles, "Cycles annotation must not be null");

        Map<String, String> resolvedFields = resolveSubjectFields(cycles);
        validateMandatory(resolvedFields, actionKind, actionName, estimatedAmount, cycles.unit());

        Map<String, Object> subject = buildSubject(resolvedFields, cycles.dimensions());
        Map<String, Object> action = buildAction(actionKind, actionName, cycles.actionTags());
        Map<String, Object> estimate = buildEstimate(cycles, estimatedAmount);

        Map<String, Object> body = new HashMap<>();
        body.put(Constants.IDEMPOTENCY_KEY, UUID.randomUUID().toString());
        body.put("subject", subject);
        body.put("action", action);
        body.put("estimate", estimate);

        if (metadata != null && !metadata.isEmpty()) {
            body.put(Constants.METADATA, metadata);
        }

        return body;
    }

    public Map<String, Object> buildEvent(Cycles cycles, long actualAmount,
                                           String actionKind, String actionName,
                                           CyclesMetrics metrics, Long clientTimeMs,
                                           Map<String, Object> metadata) {
        Objects.requireNonNull(cycles, "Cycles annotation must not be null");

        Map<String, String> resolvedFields = resolveSubjectFields(cycles);
        validateMandatory(resolvedFields, actionKind, actionName, actualAmount, cycles.unit());

        Map<String, Object> subject = buildSubject(resolvedFields, cycles.dimensions());
        Map<String, Object> action = buildAction(actionKind, actionName, cycles.actionTags());

        Map<String, Object> body = new HashMap<>();
        body.put(Constants.IDEMPOTENCY_KEY, UUID.randomUUID().toString());
        body.put("subject", subject);
        body.put("action", action);
        body.put("actual", buildActual(cycles, actualAmount));

        if (!"REJECT".equals(cycles.overagePolicy())) {
            ValidationUtils.putIfNotBlank(body, "overage_policy", cycles.overagePolicy());
        }

        if (metrics != null && !metrics.isEmpty()) {
            body.put(Constants.METRICS, metrics.toMap());
        }
        if (clientTimeMs != null) {
            body.put("client_time_ms", clientTimeMs);
        }
        if (metadata != null && !metadata.isEmpty()) {
            body.put(Constants.METADATA, metadata);
        }

        return body;
    }

    // -------------------------
    // Subject
    // -------------------------
    private Map<String, String> resolveSubjectFields(Cycles c) {
        Map<String, String> resolved = new HashMap<>();
        resolved.put(Constants.TENANT, cyclesValueResolutionService.resolve(Constants.TENANT, c.tenant()));
        resolved.put(Constants.WORKSPACE, cyclesValueResolutionService.resolve(Constants.WORKSPACE, c.workspace()));
        resolved.put(Constants.APP, cyclesValueResolutionService.resolve(Constants.APP, c.app()));
        resolved.put(Constants.WORKFLOW, cyclesValueResolutionService.resolve(Constants.WORKFLOW, c.workflow()));
        resolved.put(Constants.AGENT, cyclesValueResolutionService.resolve(Constants.AGENT, c.agent()));
        resolved.put(Constants.TOOLSET, cyclesValueResolutionService.resolve(Constants.TOOLSET, c.toolset()));
        return resolved;
    }

    private Map<String, Object> buildSubject(Map<String, String> resolvedFields, String[] dimensions) {
        Map<String, Object> subject = new HashMap<>();
        for (Map.Entry<String, String> entry : resolvedFields.entrySet()) {
            ValidationUtils.putIfNotBlank(subject, entry.getKey(), entry.getValue());
        }
        Map<String, String> dimMap = parseDimensions(dimensions);
        if (!dimMap.isEmpty()) {
            subject.put(Constants.DIMENSIONS, dimMap);
        }
        return subject;
    }

    // -------------------------
    // Action
    // -------------------------
    private Map<String, Object> buildAction(String actionKind, String actionName, String[] actionTags) {
        Map<String, Object> action = new HashMap<>();
        ValidationUtils.putIfNotBlank(action, "kind", actionKind);
        ValidationUtils.putIfNotBlank(action, "name", actionName);
        if (actionTags.length > 0) {
            action.put("tags", List.of(actionTags));
        }
        return action;
    }

    // -------------------------
    // Estimate / Actual
    // -------------------------
    private static Map<String, Object> buildEstimate(Cycles c, long estimatedAmount) {
        if (estimatedAmount < 0) {
            throw new CyclesProtocolException("Estimated amount must be >= 0");
        }
        Map<String, Object> estimate = new HashMap<>();
        estimate.put(Constants.UNIT, c.unit());
        estimate.put(Constants.AMOUNT, estimatedAmount);
        return estimate;
    }

    private Map<String, Object> buildActual(Cycles c, long actualAmount) {
        if (actualAmount < 0) {
            throw new CyclesProtocolException("Actual amount must be >= 0");
        }
        ValidationUtils.requireNotBlank(c.unit(), "unit is mandatory");
        Map<String, Object> actual = new HashMap<>();
        actual.put(Constants.UNIT, c.unit());
        actual.put(Constants.AMOUNT, actualAmount);
        return actual;
    }

    // -------------------------
    // Dimensions
    // -------------------------
    static Map<String, String> parseDimensions(String[] dimensions) {
        if (dimensions == null || dimensions.length == 0) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (String dim : dimensions) {
            int eq = dim.indexOf('=');
            if (eq <= 0 || eq == dim.length() - 1) {
                throw new CyclesProtocolException("Invalid dimension format (expected 'key=value'): " + dim);
            }
            result.put(dim.substring(0, eq), dim.substring(eq + 1));
        }
        return result;
    }

    // -------------------------
    // Validation
    // -------------------------
    private void validateMandatory(Map<String, String> resolvedFields,
                                   String actionKind, String actionName,
                                   long amount, String unit) {
        boolean hasAnySubjectField = resolvedFields.entrySet().stream()
                .anyMatch(e -> e.getValue() != null && !e.getValue().isBlank());
        if (!hasAnySubjectField) {
            throw new CyclesProtocolException(
                    "At least one Subject field (tenant, workspace, app, workflow, agent, or toolset) is required");
        }
        ValidationUtils.requireNotBlank(actionKind, "actionKind is mandatory");
        ValidationUtils.requireNotBlank(actionName, "actionName is mandatory");
        if (amount < 0) {
            throw new CyclesProtocolException("Amount must be >= 0");
        }
        ValidationUtils.requireNotBlank(unit, "unit is mandatory");
    }
}
