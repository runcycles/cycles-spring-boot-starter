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

    /** Creates a new, empty metrics instance. */
    public CyclesMetrics() {}

    /**
     * Returns the input token count.
     *
     * @return the input tokens, or {@code null}
     */
    public Integer getTokensInput() { return tokensInput; }

    /**
     * Sets the input token count.
     *
     * @param tokensInput the input tokens
     */
    public void setTokensInput(Integer tokensInput) { this.tokensInput = tokensInput; }

    /**
     * Returns the output token count.
     *
     * @return the output tokens, or {@code null}
     */
    public Integer getTokensOutput() { return tokensOutput; }

    /**
     * Sets the output token count.
     *
     * @param tokensOutput the output tokens
     */
    public void setTokensOutput(Integer tokensOutput) { this.tokensOutput = tokensOutput; }

    /**
     * Returns the latency in milliseconds.
     *
     * @return the latency, or {@code null}
     */
    public Integer getLatencyMs() { return latencyMs; }

    /**
     * Sets the latency in milliseconds.
     *
     * @param latencyMs the latency
     */
    public void setLatencyMs(Integer latencyMs) { this.latencyMs = latencyMs; }

    /**
     * Returns the model version string.
     *
     * @return the model version, or {@code null}
     */
    public String getModelVersion() { return modelVersion; }

    /**
     * Sets the model version string.
     *
     * @param modelVersion the model version
     */
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }

    /**
     * Returns the custom metrics map.
     *
     * @return the custom metrics, or {@code null}
     */
    public Map<String, Object> getCustom() { return custom; }

    /**
     * Sets the custom metrics map.
     *
     * @param custom the custom metrics
     */
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
