package io.runcycles.client.java.spring.context;

import io.runcycles.client.java.spring.annotation.Cycles;
import io.runcycles.client.java.spring.config.CyclesProperties;
import io.runcycles.client.java.spring.model.CyclesProtocolException;
import io.runcycles.client.java.spring.util.Constants;
import org.springframework.lang.NonNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class CyclesRequestBuilder {

    public static Map<String, Object> buildReservation(@NonNull CyclesProperties cfg, Cycles cycles, long estimatedAmount) {
        Objects.requireNonNull(cycles, "Cycles annotation must not be null");

        validateReservationMandatory(cfg, cycles, estimatedAmount);

        Map<String, Object> subject = buildSubject(cfg,cycles);
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
    public static Map<String, Object> buildRelease() {
        return Map.of(Constants.IDEMPOTENCY_KEY, UUID.randomUUID().toString()) ;
    }

    public static Map<String, Object> buildCommit(Cycles cycles, long actualAmount) {
        Objects.requireNonNull(cycles, "Cycles annotation must not be null");

        Map<String, Object> body = new HashMap<>();
        body.put(Constants.IDEMPOTENCY_KEY, UUID.randomUUID().toString());

        Map<String, Object> actual = buildActual(cycles, actualAmount);
        body.put("actual", actual);

        putIfNotBlank(body, "overage_policy", cycles.overagePolicy());
        return body;
    }

    // -------------------------
    // Subject
    // -------------------------
    private static Map<String, Object> buildSubject(CyclesProperties cfg, Cycles c) {
        Map<String, Object> subject = new HashMap<>();

        putIfNotBlank(subject, "tenant", resolve(c.tenant(), cfg.getTenant()));
        putIfNotBlank(subject, "workspace", resolve(c.workspace(), cfg.getWorkspace()));
        putIfNotBlank(subject, "app", resolve(c.app(), cfg.getApp()));
        putIfNotBlank(subject, "workflow", resolve(c.workflow(), cfg.getWorkflow()));
        putIfNotBlank(subject, "agent", resolve(c.agent(), cfg.getAgent()));
        putIfNotBlank(subject, "toolset", resolve(c.toolset(), cfg.getToolset()));

        return subject;
    }

    // -------------------------
    // Action
    // -------------------------
    private static Map<String, Object> buildAction(Cycles c) {
        Map<String, Object> action = new HashMap<>();

        putIfNotBlank(action, "kind", c.actionKind());
        putIfNotBlank(action, "name", c.actionName());

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
    private static Map<String, Object> buildActual(Cycles c, long actualAmount) {
        Map<String, Object> actual = new HashMap<>();
        if (actualAmount < 0) {
            throw new CyclesProtocolException("Actual amount must be >= 0");
        }
        requireNotBlank(c.unit(), "unit is mandatory");
        actual.put(Constants.UNIT, c.unit());
        actual.put(Constants.AMOUNT, actualAmount);
        return actual;
    }
    // -------------------------
    // Validation
    // -------------------------
    private static void validateReservationMandatory(CyclesProperties cfg,Cycles c, long estimatedAmount) {
        String tenant = resolve(c.tenant(),cfg.getTenant()) ;
        requireNotBlank(tenant, "tenant is mandatory");
        requireNotBlank(c.actionKind(), "actionKind is mandatory");
        requireNotBlank(c.actionName(), "actionName is mandatory");

        if (estimatedAmount < 0) {
            throw new IllegalArgumentException("Estimated amount must be >= 0");
        }

        requireNotBlank(c.unit(), "unit is mandatory");
    }

    // -------------------------
    // Helpers
    // -------------------------
    private static void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    private static void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CyclesProtocolException(message);
        }
    }
    private static String resolve(String annotationValue, String configValue) {
        if (annotationValue != null && !annotationValue.isBlank()) {
            return annotationValue;
        }

        if (configValue != null && !configValue.isBlank()) {
            return configValue;
        }
        return null;
    }

}
