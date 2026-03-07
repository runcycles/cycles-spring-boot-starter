package io.runcycles.client.java.spring.context;

import io.runcycles.client.java.spring.model.Caps;
import io.runcycles.client.java.spring.model.CyclesMetrics;
import io.runcycles.client.java.spring.model.Decision;

import java.util.List;
import java.util.Map;

public class CyclesReservationContext {

    private final String reservationId;
    private final long estimate;
    private final Decision decision;
    private final Caps caps;
    private final Long expiresAtMs;
    private final List<String> affectedScopes;
    private final String scopePath;
    private final List<Map<String, Object>> balances;

    // Mutable fields: users can set these during guarded method execution
    // and the aspect will pick them up at commit time.
    private CyclesMetrics metrics;
    private Map<String, Object> commitMetadata;

    public CyclesReservationContext(String reservationId, long estimate,
                                   Decision decision, Caps caps, Long expiresAtMs,
                                   List<String> affectedScopes, String scopePath,
                                   List<Map<String, Object>> balances) {
        this.reservationId = reservationId;
        this.estimate = estimate;
        this.decision = decision;
        this.caps = caps;
        this.expiresAtMs = expiresAtMs;
        this.affectedScopes = affectedScopes;
        this.scopePath = scopePath;
        this.balances = balances;
    }

    public String getReservationId() { return reservationId; }
    public long getEstimate() { return estimate; }
    public Decision getDecision() { return decision; }
    public Caps getCaps() { return caps; }
    public Long getExpiresAtMs() { return expiresAtMs; }
    public List<String> getAffectedScopes() { return affectedScopes; }
    public String getScopePath() { return scopePath; }
    public List<Map<String, Object>> getBalances() { return balances; }

    public boolean hasCaps() { return caps != null; }
    public boolean isExpiringSoon(long thresholdMs) {
        if (expiresAtMs == null) return false;
        return (expiresAtMs - System.currentTimeMillis()) < thresholdMs;
    }

    /**
     * Set standard metrics to include in the commit request.
     * Call this inside your {@code @Cycles}-guarded method to report
     * tokens_input, tokens_output, latency_ms, model_version, or custom metrics.
     */
    public void setMetrics(CyclesMetrics metrics) { this.metrics = metrics; }
    public CyclesMetrics getMetrics() { return metrics; }

    /**
     * Set metadata to include in the commit request.
     * Call this inside your {@code @Cycles}-guarded method to attach
     * arbitrary key-value audit/debugging data to the commit.
     */
    public void setCommitMetadata(Map<String, Object> commitMetadata) { this.commitMetadata = commitMetadata; }
    public Map<String, Object> getCommitMetadata() { return commitMetadata; }
}
