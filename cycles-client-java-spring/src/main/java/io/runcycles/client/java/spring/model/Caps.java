package io.runcycles.client.java.spring.model;

import java.util.List;
import java.util.Map;

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

    public Integer getMaxTokens() { return maxTokens; }
    public Integer getMaxStepsRemaining() { return maxStepsRemaining; }
    public List<String> getToolAllowlist() { return toolAllowlist; }
    public List<String> getToolDenylist() { return toolDenylist; }
    public Integer getCooldownMs() { return cooldownMs; }

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
