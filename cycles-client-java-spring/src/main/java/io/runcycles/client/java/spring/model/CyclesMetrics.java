package io.runcycles.client.java.spring.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Standard metrics for commit and event requests.
 * Maps to the StandardMetrics schema in the Cycles Protocol v0 spec.
 */
public class CyclesMetrics {
    private Integer tokensInput;
    private Integer tokensOutput;
    private Integer latencyMs;
    private String modelVersion;
    private Map<String, Object> custom;

    public CyclesMetrics() {}

    public Integer getTokensInput() { return tokensInput; }
    public void setTokensInput(Integer tokensInput) { this.tokensInput = tokensInput; }

    public Integer getTokensOutput() { return tokensOutput; }
    public void setTokensOutput(Integer tokensOutput) { this.tokensOutput = tokensOutput; }

    public Integer getLatencyMs() { return latencyMs; }
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }

    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    public Map<String, Object> getCustom() { return custom; }
    public void setCustom(Map<String, Object> custom) { this.custom = custom; }

    /**
     * Adds a custom metric entry, initializing the custom map if needed.
     *
     * @param key   the metric key
     * @param value the metric value
     */
    public void putCustom(String key, Object value) {
        if (custom == null) { custom = new HashMap<>(); }
        custom.put(key, value);
    }

    /**
     * Returns {@code true} if no standard or custom metrics have been set.
     *
     * @return whether all fields are {@code null} or empty
     */
    public boolean isEmpty() {
        return tokensInput == null && tokensOutput == null
                && latencyMs == null && modelVersion == null
                && (custom == null || custom.isEmpty());
    }

    /**
     * Serializes these metrics to a map suitable for the API request body.
     *
     * @return a mutable map of non-null fields
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        if (tokensInput != null) map.put("tokens_input", tokensInput);
        if (tokensOutput != null) map.put("tokens_output", tokensOutput);
        if (latencyMs != null) map.put("latency_ms", latencyMs);
        if (modelVersion != null) map.put("model_version", modelVersion);
        if (custom != null && !custom.isEmpty()) map.put("custom", custom);
        return map;
    }

    @Override
    public String toString() {
        return "CyclesMetrics{tokensInput=" + tokensInput +
                ", tokensOutput=" + tokensOutput +
                ", latencyMs=" + latencyMs +
                ", modelVersion=" + modelVersion +
                ", custom=" + custom + '}';
    }
}
