package io.runcycles.client.java.spring.model;

import java.util.List;
import java.util.Map;

/**
 * Typed result returned by {@code @Cycles(dryRun=true)} annotated methods
 * when the server evaluates ALLOW or ALLOW_WITH_CAPS.
 * <p>
 * Contains the full evaluation data from the server: decision, caps,
 * affected scopes, balances, and scope path.
 */
public class DryRunResult {
    private final Decision decision;
    private final Caps caps;
    private final List<String> affectedScopes;
    private final String scopePath;
    private final Map<String, Object> reserved;
    private final List<Map<String, Object>> balances;
    private final Long retryAfterMs;

    public DryRunResult(Decision decision, Caps caps,
                        List<String> affectedScopes, String scopePath,
                        Map<String, Object> reserved,
                        List<Map<String, Object>> balances,
                        Long retryAfterMs) {
        this.decision = decision;
        this.caps = caps;
        this.affectedScopes = affectedScopes;
        this.scopePath = scopePath;
        this.reserved = reserved;
        this.balances = balances;
        this.retryAfterMs = retryAfterMs;
    }

    public Decision getDecision() { return decision; }
    public Caps getCaps() { return caps; }
    public List<String> getAffectedScopes() { return affectedScopes; }
    public String getScopePath() { return scopePath; }
    public Map<String, Object> getReserved() { return reserved; }
    public List<Map<String, Object>> getBalances() { return balances; }
    public Long getRetryAfterMs() { return retryAfterMs; }

    public boolean isAllowed() { return decision == Decision.ALLOW || decision == Decision.ALLOW_WITH_CAPS; }
    public boolean hasCaps() { return caps != null; }

    @Override
    public String toString() {
        return "DryRunResult{decision=" + decision +
                ", caps=" + caps +
                ", affectedScopes=" + affectedScopes +
                ", scopePath='" + scopePath + '\'' +
                ", balances=" + (balances != null ? balances.size() + " entries" : "null") +
                ", retryAfterMs=" + retryAfterMs + '}';
    }
}
