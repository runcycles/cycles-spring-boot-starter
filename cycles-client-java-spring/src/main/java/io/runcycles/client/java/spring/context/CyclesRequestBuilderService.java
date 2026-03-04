package io.runcycles.client.java.spring.context;

import io.runcycles.client.java.spring.annotation.Cycles;
import io.runcycles.client.java.spring.evaluation.CyclesValueResolutionService;
import io.runcycles.client.java.spring.model.CyclesProtocolException;
import io.runcycles.client.java.spring.util.Constants;
import io.runcycles.client.java.spring.util.ValidationUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class CyclesRequestBuilderService {

    private final CyclesValueResolutionService cyclesValueResolutionService;

    public CyclesRequestBuilderService (CyclesValueResolutionService cyclesValueResolutionService){
        this.cyclesValueResolutionService = cyclesValueResolutionService;
    }

    public Map<String, Object> buildReservation(Cycles cycles, long estimatedAmount) {
        Objects.requireNonNull(cycles, "Cycles annotation must not be null");

        validateReservationMandatory(cycles, estimatedAmount);

        Map<String, Object> subject = buildSubject(cycles);
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

        return body;
    }
    public Map<String, Object> buildRelease() {
        return Map.of(Constants.IDEMPOTENCY_KEY, UUID.randomUUID().toString()) ;
    }

    public Map<String, Object> buildCommit(Cycles cycles, long actualAmount) {
        Objects.requireNonNull(cycles, "Cycles annotation must not be null");

        Map<String, Object> body = new HashMap<>();
        body.put(Constants.IDEMPOTENCY_KEY, UUID.randomUUID().toString());

        Map<String, Object> actual = buildActual(cycles, actualAmount);
        body.put("actual", actual);

        ValidationUtils.putIfNotBlank(body, "overage_policy", cycles.overagePolicy());
        return body;
    }

    // -------------------------
    // Subject
    // -------------------------
    private Map<String, Object> buildSubject(Cycles c) {
        Map<String, Object> subject = new HashMap<>();

        ValidationUtils.putIfNotBlank(subject, Constants.TENANT, cyclesValueResolutionService.resolve(Constants.TENANT,c.tenant()));
        ValidationUtils.putIfNotBlank(subject, Constants.WORKSPACE, cyclesValueResolutionService.resolve(Constants.WORKSPACE,c.workspace()));
        ValidationUtils.putIfNotBlank(subject, Constants.APP, cyclesValueResolutionService.resolve(Constants.APP,c.app()));
        ValidationUtils.putIfNotBlank(subject, Constants.WORKFLOW, cyclesValueResolutionService.resolve(Constants.WORKFLOW,c.workflow()));
        ValidationUtils.putIfNotBlank(subject, Constants.AGENT, cyclesValueResolutionService.resolve(Constants.AGENT,c.agent()));
        ValidationUtils.putIfNotBlank(subject, Constants.TOOLSET, cyclesValueResolutionService.resolve(Constants.TOOLSET, c.toolset()));

        return subject;
    }

    // -------------------------
    // Action
    // -------------------------
    private Map<String, Object> buildAction(Cycles c) {
        Map<String, Object> action = new HashMap<>();

        ValidationUtils.putIfNotBlank(action, "kind", c.actionKind());
        ValidationUtils.putIfNotBlank(action, "name", c.actionName());

        return action;
    }

    // -------------------------
    // Estimate
    // -------------------------
    private static Map<String, Object> buildEstimate(Cycles c, long estimatedAmount) {
        Map<String, Object> estimate = new HashMap<>();

        if (estimatedAmount < 0) {
            throw new CyclesProtocolException("Estimated amount must be >= 0");
        }

        estimate.put(Constants.UNIT, c.unit());
        estimate.put(Constants.AMOUNT, estimatedAmount);

        return estimate;
    }
    // -------------------------
    // Actual
    // -------------------------
    private Map<String, Object> buildActual(Cycles c, long actualAmount) {
        Map<String, Object> actual = new HashMap<>();
        if (actualAmount < 0) {
            throw new CyclesProtocolException("Actual amount must be >= 0");
        }
        ValidationUtils.requireNotBlank(c.unit(), "unit is mandatory");
        actual.put(Constants.UNIT, c.unit());
        actual.put(Constants.AMOUNT, actualAmount);
        return actual;
    }
    // -------------------------
    // Validation
    // -------------------------
    private void validateReservationMandatory(Cycles c, long estimatedAmount) {
        String tenant = cyclesValueResolutionService.resolve(Constants.TENANT,c.tenant()) ;
        ValidationUtils.requireNotBlank(tenant, "tenant is mandatory");
        ValidationUtils.requireNotBlank(c.actionKind(), "actionKind is mandatory");
        ValidationUtils.requireNotBlank(c.actionName(), "actionName is mandatory");

        if (estimatedAmount < 0) {
            throw new IllegalArgumentException("Estimated amount must be >= 0");
        }
        ValidationUtils.requireNotBlank(c.unit(), "unit is mandatory");
    }



}
