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
                                                 Map<String, Object> metadata) {
        Objects.requireNonNull(cycles, "Cycles annotation must not be null");

        Map<String, String> resolvedFields = resolveSubjectFields(cycles);
        validateReservationMandatory(cycles, resolvedFields, estimatedAmount);

        Map<String, Object> subject = buildSubject(resolvedFields, cycles.dimensions());
        Map<String, Object> action = buildAction(cycles);
        Map<String, Object> estimate = buildEstimate(cycles, estimatedAmount);

        Map<String, Object> body = new HashMap<>();
        body.put(Constants.IDEMPOTENCY_KEY, UUID.randomUUID().toString());
        body.put("subject", subject);
        body.put("action", action);
        body.put("estimate", estimate);

        if (cycles.ttlMs() > 0) {
            body.put("ttl_ms", cycles.ttlMs());
        }
        if (cycles.gracePeriodMs() >= 0) {
            body.put("grace_period_ms", cycles.gracePeriodMs());
        }

        ValidationUtils.putIfNotBlank(body, "overage_policy", cycles.overagePolicy());

        if (cycles.dryRun()) {
            body.put(Constants.DRY_RUN, true);
        }
        if (metadata != null && !metadata.isEmpty()) {
            body.put(Constants.METADATA, metadata);
        }

        return body;
    }

    public Map<String, Object> buildExtend(long extendByMs, Map<String, Object> metadata) {
        if (extendByMs <= 0) {
            throw new CyclesProtocolException("extend_by_ms must be > 0");
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
                                              Map<String, Object> metadata) {
        Objects.requireNonNull(cycles, "Cycles annotation must not be null");

        Map<String, String> resolvedFields = resolveSubjectFields(cycles);
        validateReservationMandatory(cycles, resolvedFields, estimatedAmount);

        Map<String, Object> subject = buildSubject(resolvedFields, cycles.dimensions());
        Map<String, Object> action = buildAction(cycles);
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
                                           CyclesMetrics metrics, Map<String, Object> metadata) {
        Objects.requireNonNull(cycles, "Cycles annotation must not be null");

        Map<String, String> resolvedFields = resolveSubjectFields(cycles);
        validateReservationMandatory(cycles, resolvedFields, actualAmount);

        Map<String, Object> subject = buildSubject(resolvedFields, cycles.dimensions());
        Map<String, Object> action = buildAction(cycles);

        Map<String, Object> body = new HashMap<>();
        body.put(Constants.IDEMPOTENCY_KEY, UUID.randomUUID().toString());
        body.put("subject", subject);
        body.put("action", action);
        body.put("actual", buildActual(cycles, actualAmount));

        ValidationUtils.putIfNotBlank(body, "overage_policy", cycles.overagePolicy());

        if (metrics != null && !metrics.isEmpty()) {
            body.put(Constants.METRICS, metrics.toMap());
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
    private Map<String, Object> buildAction(Cycles c) {
        Map<String, Object> action = new HashMap<>();
        ValidationUtils.putIfNotBlank(action, "kind", c.actionKind());
        ValidationUtils.putIfNotBlank(action, "name", c.actionName());
        if (c.actionTags().length > 0) {
            action.put("tags", List.of(c.actionTags()));
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
    private void validateReservationMandatory(Cycles c, Map<String, String> resolvedFields, long amount) {
        ValidationUtils.requireNotBlank(resolvedFields.get(Constants.TENANT), "tenant is mandatory");
        ValidationUtils.requireNotBlank(c.actionKind(), "actionKind is mandatory");
        ValidationUtils.requireNotBlank(c.actionName(), "actionName is mandatory");
        if (amount < 0) {
            throw new CyclesProtocolException("Amount must be >= 0");
        }
        ValidationUtils.requireNotBlank(c.unit(), "unit is mandatory");
    }
}
