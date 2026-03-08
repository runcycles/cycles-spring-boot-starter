package io.runcycles.client.java.spring.model;

import java.util.List;
import java.util.Map;

/**
 * Typed result for {@code POST /v1/decide}.
 * Matches server's DecisionResponse: decision, caps, reason_code, retry_after_ms, affected_scopes.
 */
public class DecisionResult {
    private final Decision decision;
    private final Caps caps;
    private final String reasonCode;
    private final Integer retryAfterMs;
    private final List<String> affectedScopes;

    private DecisionResult(Decision decision, Caps caps, String reasonCode,
                           Integer retryAfterMs, List<String> affectedScopes) {
        this.decision = decision;
        this.caps = caps;
        this.reasonCode = reasonCode;
        this.retryAfterMs = retryAfterMs;
        this.affectedScopes = affectedScopes;
    }

    @SuppressWarnings("unchecked")
    public static DecisionResult fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new DecisionResult(
                Decision.fromString(map.get("decision") instanceof String s ? s : null),
                Caps.fromMap(map.get("caps") instanceof Map<?, ?> m ? (Map<String, Object>) m : null),
                map.get("reason_code") instanceof String s ? s : null,
                map.get("retry_after_ms") instanceof Number n ? n.intValue() : null,
                map.get("affected_scopes") instanceof List<?> l ? (List<String>) l : List.of()
        );
    }

    public Decision getDecision() { return decision; }
    public Caps getCaps() { return caps; }
    public String getReasonCode() { return reasonCode; }
    public Integer getRetryAfterMs() { return retryAfterMs; }
    public List<String> getAffectedScopes() { return affectedScopes; }

    public boolean isAllowed() { return decision == Decision.ALLOW || decision == Decision.ALLOW_WITH_CAPS; }
    public boolean isDenied() { return decision == Decision.DENY; }

    @Override
    public String toString() {
        return "DecisionResult{decision=" + decision +
                ", caps=" + caps +
                ", reasonCode='" + reasonCode + '\'' +
                ", retryAfterMs=" + retryAfterMs +
                ", affectedScopes=" + affectedScopes + '}';
    }
}
