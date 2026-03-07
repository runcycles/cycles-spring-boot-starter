package io.runcycles.client.java.spring.model;

import java.util.List;

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
    private final Amount reserved;
    private final List<Balance> balances;
    private final String reasonCode;
    private final Integer retryAfterMs;

    public DryRunResult(Decision decision, Caps caps,
                        List<String> affectedScopes, String scopePath,
                        Amount reserved,
                        List<Balance> balances,
                        String reasonCode,
                        Integer retryAfterMs) {
        this.decision = decision;
        this.caps = caps;
        this.affectedScopes = affectedScopes;
        this.scopePath = scopePath;
        this.reserved = reserved;
        this.balances = balances;
        this.reasonCode = reasonCode;
        this.retryAfterMs = retryAfterMs;
    }

    public Decision getDecision() { return decision; }
    public Caps getCaps() { return caps; }
    public List<String> getAffectedScopes() { return affectedScopes; }
    public String getScopePath() { return scopePath; }
    public Amount getReserved() { return reserved; }
    public List<Balance> getBalances() { return balances; }
    public String getReasonCode() { return reasonCode; }
    public Integer getRetryAfterMs() { return retryAfterMs; }

    public boolean isAllowed() { return decision == Decision.ALLOW || decision == Decision.ALLOW_WITH_CAPS; }
    public boolean hasCaps() { return caps != null; }

    @Override
    public String toString() {
        return "DryRunResult{decision=" + decision +
                ", caps=" + caps +
                ", affectedScopes=" + affectedScopes +
                ", scopePath='" + scopePath + '\'' +
                ", reasonCode='" + reasonCode + '\'' +
                ", balances=" + (balances != null ? balances.size() + " entries" : "null") +
                ", retryAfterMs=" + retryAfterMs + '}';
    }
}
