package io.runcycles.client.java.spring.model;

import java.util.List;
import java.util.Map;

/**
 * Capability constraints returned by the Cycles API when a reservation decision is
 * {@code ALLOW_WITH_CAPS}.
 *
 * <p>Caps may limit token usage, restrict available tools, impose a cooldown, or
 * cap the remaining steps. Application code can inspect these via
 * {@link io.runcycles.client.java.spring.context.CyclesReservationContext#getCaps()
 * CyclesReservationContext.getCaps()}.
 */
public class Caps {
    private final Integer maxTokens;
    private final Integer maxStepsRemaining;
    private final List<String> toolAllowlist;
    private final List<String> toolDenylist;
    private final Integer cooldownMs;

    private Caps(Integer maxTokens, Integer maxStepsRemaining,
                 List<String> toolAllowlist, List<String> toolDenylist,
                 Integer cooldownMs) {
        this.maxTokens = maxTokens;
        this.maxStepsRemaining = maxStepsRemaining;
        this.toolAllowlist = toolAllowlist;
        this.toolDenylist = toolDenylist;
        this.cooldownMs = cooldownMs;
    }

    /**
     * Parses a {@code Caps} instance from a raw API response map.
     *
     * @param map the caps section of the response, or {@code null}
     * @return the parsed {@code Caps}, or {@code null} if the input is {@code null}
     */
    @SuppressWarnings("unchecked")
    public static Caps fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new Caps(
                map.get("max_tokens") instanceof Number n ? n.intValue() : null,
                map.get("max_steps_remaining") instanceof Number n ? n.intValue() : null,
                map.get("tool_allowlist") instanceof List<?> l ? (List<String>) l : null,
                map.get("tool_denylist") instanceof List<?> l ? (List<String>) l : null,
                map.get("cooldown_ms") instanceof Number n ? n.intValue() : null
        );
    }

    /** Returns the maximum token budget, or {@code null} if unconstrained. */
    public Integer getMaxTokens() { return maxTokens; }
    /** Returns the maximum remaining steps, or {@code null} if unconstrained. */
    public Integer getMaxStepsRemaining() { return maxStepsRemaining; }
    /** Returns the tool allowlist, or {@code null} if no allowlist is set. */
    public List<String> getToolAllowlist() { return toolAllowlist; }
    /** Returns the tool denylist, or {@code null} if no denylist is set. */
    public List<String> getToolDenylist() { return toolDenylist; }
    /** Returns the cooldown period in milliseconds, or {@code null} if no cooldown. */
    public Integer getCooldownMs() { return cooldownMs; }

    /**
     * Checks whether the given tool is permitted under these caps.
     *
     * <p>If an allowlist is present, only listed tools are permitted. If a denylist
     * is present instead, all tools except those listed are permitted. If neither
     * list is set, all tools are allowed.
     *
     * @param toolName the tool name to check
     * @return {@code true} if the tool is allowed
     */
    public boolean isToolAllowed(String toolName) {
        if (toolAllowlist != null && !toolAllowlist.isEmpty()) {
            return toolAllowlist.contains(toolName);
        }
        if (toolDenylist != null && !toolDenylist.isEmpty()) {
            return !toolDenylist.contains(toolName);
        }
        return true;
    }

    @Override
    public String toString() {
        return "Caps{maxTokens=" + maxTokens +
                ", maxStepsRemaining=" + maxStepsRemaining +
                ", toolAllowlist=" + toolAllowlist +
                ", toolDenylist=" + toolDenylist +
                ", cooldownMs=" + cooldownMs + '}';
    }
}
