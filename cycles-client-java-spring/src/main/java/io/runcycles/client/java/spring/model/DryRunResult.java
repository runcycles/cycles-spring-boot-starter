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

    /** Returns the server's decision. */
    public Decision getDecision() { return decision; }
    /** Returns the capability constraints, or {@code null} if none. */
    public Caps getCaps() { return caps; }
    /** Returns the list of affected budget scopes. */
    public List<String> getAffectedScopes() { return affectedScopes; }
    /** Returns the fully-qualified scope path. */
    public String getScopePath() { return scopePath; }
    /** Returns the would-be reserved amount. */
    public Amount getReserved() { return reserved; }
    /** Returns the current balances. */
    public List<Balance> getBalances() { return balances; }
    /** Returns the reason code for the decision, or {@code null}. */
    public String getReasonCode() { return reasonCode; }
    /** Returns the suggested retry delay in milliseconds, or {@code null}. */
    public Integer getRetryAfterMs() { return retryAfterMs; }

    /** Returns {@code true} if the decision is {@code ALLOW} or {@code ALLOW_WITH_CAPS}. */
    public boolean isAllowed() { return decision == Decision.ALLOW || decision == Decision.ALLOW_WITH_CAPS; }
    /** Returns {@code true} if capability constraints were returned by the server. */
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
