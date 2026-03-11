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

    /**
     * Deserializes a {@code DecisionResult} from a raw API response map.
     *
     * @param map the response body map, or {@code null}
     * @return the parsed result, or {@code null} if the input is {@code null}
     */
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

    /** Returns the server's decision. */
    public Decision getDecision() { return decision; }
    /** Returns the capability constraints, or {@code null} if none. */
    public Caps getCaps() { return caps; }
    /** Returns the reason code for the decision, or {@code null}. */
    public String getReasonCode() { return reasonCode; }
    /** Returns the suggested retry delay in milliseconds, or {@code null}. */
    public Integer getRetryAfterMs() { return retryAfterMs; }
    /** Returns the list of affected budget scopes. */
    public List<String> getAffectedScopes() { return affectedScopes; }

    /** Returns {@code true} if the decision is {@code ALLOW} or {@code ALLOW_WITH_CAPS}. */
    public boolean isAllowed() { return decision == Decision.ALLOW || decision == Decision.ALLOW_WITH_CAPS; }
    /** Returns {@code true} if the decision is {@code DENY}. */
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
