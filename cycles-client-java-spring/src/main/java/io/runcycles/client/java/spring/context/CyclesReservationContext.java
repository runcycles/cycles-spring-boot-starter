package io.runcycles.client.java.spring.context;

import io.runcycles.client.java.spring.model.Amount;
import io.runcycles.client.java.spring.model.Balance;
import io.runcycles.client.java.spring.model.Caps;
import io.runcycles.client.java.spring.model.CyclesMetrics;
import io.runcycles.client.java.spring.model.Decision;

import java.util.List;
import java.util.Map;

/**
 * Holds the state of an active Cycles reservation during the execution of a
 * {@code @Cycles}-annotated method.
 *
 * <p>Accessible via {@link CyclesContextHolder#get()} from within guarded methods.
 * Application code can:
 * <ul>
 *   <li>Inspect the {@linkplain #getDecision() decision} and {@linkplain #getCaps() caps}</li>
 *   <li>Attach {@linkplain #setMetrics(CyclesMetrics) metrics} for the commit</li>
 *   <li>Attach {@linkplain #setCommitMetadata(Map) metadata} for the commit</li>
 *   <li>Check if the reservation {@linkplain #isExpiringSoon(long) is about to expire}</li>
 * </ul>
 *
 * @see CyclesContextHolder
 */
public class CyclesReservationContext {

    private final String reservationId;
    private final long estimate;
    private final Decision decision;
    private final Caps caps;
    private volatile Long expiresAtMs;
    private final List<String> affectedScopes;
    private final String scopePath;
    private final Amount reserved;
    private final List<Balance> balances;

    // Mutable fields: users can set these during guarded method execution
    // and the aspect will pick them up at commit time.
    private CyclesMetrics metrics;
    private Map<String, Object> commitMetadata;

    /**
     * Creates a new reservation context with the given reservation state.
     *
     * @param reservationId  the server-assigned reservation identifier
     * @param estimate       the estimated amount that was reserved
     * @param decision       the budget decision returned by the server
     * @param caps           the applicable budget caps, or {@code null}
     * @param expiresAtMs    the reservation expiration timestamp in epoch milliseconds, or {@code null}
     * @param affectedScopes the budget scopes affected by this reservation
     * @param scopePath      the scope path for this reservation
     * @param reserved       the reserved amount
     * @param balances       the balance snapshots after reservation
     */
    public CyclesReservationContext(String reservationId, long estimate,
                                   Decision decision, Caps caps, Long expiresAtMs,
                                   List<String> affectedScopes, String scopePath,
                                   Amount reserved,
                                   List<Balance> balances) {
        this.reservationId = reservationId;
        this.estimate = estimate;
        this.decision = decision;
        this.caps = caps;
        this.expiresAtMs = expiresAtMs;
        this.affectedScopes = affectedScopes;
        this.scopePath = scopePath;
        this.reserved = reserved;
        this.balances = balances;
    }

    /** Returns the server-assigned reservation identifier. */
    public String getReservationId() { return reservationId; }
    /** Returns the estimated amount that was reserved. */
    public long getEstimate() { return estimate; }
    /** Returns the budget decision returned by the server. */
    public Decision getDecision() { return decision; }
    /** Returns the applicable budget caps, or {@code null}. */
    public Caps getCaps() { return caps; }
    /** Returns the reservation expiration timestamp in epoch milliseconds, or {@code null}. */
    public Long getExpiresAtMs() { return expiresAtMs; }
    /** Returns the budget scopes affected by this reservation. */
    public List<String> getAffectedScopes() { return affectedScopes; }
    /** Returns the scope path for this reservation. */
    public String getScopePath() { return scopePath; }
    /** Returns the reserved amount. */
    public Amount getReserved() { return reserved; }
    /** Returns the balance snapshots after reservation. */
    public List<Balance> getBalances() { return balances; }

    /**
     * Updates the expiration timestamp after a successful heartbeat extend.
     *
     * @param expiresAtMs the new expiration timestamp in epoch milliseconds
     */
    void updateExpiresAtMs(Long expiresAtMs) { this.expiresAtMs = expiresAtMs; }

    /**
     * Returns {@code true} if budget caps are present.
     *
     * @return whether caps are non-null
     */
    public boolean hasCaps() { return caps != null; }

    /**
     * Returns {@code true} if the reservation will expire within the given threshold.
     *
     * @param thresholdMs the threshold in milliseconds
     * @return whether the remaining TTL is less than {@code thresholdMs}
     */
    public boolean isExpiringSoon(long thresholdMs) {
        if (expiresAtMs == null) return false;
        return (expiresAtMs - System.currentTimeMillis()) < thresholdMs;
    }

    /**
     * Sets standard metrics to include in the commit request.
     * Call this inside your {@code @Cycles}-guarded method to report
     * tokens_input, tokens_output, latency_ms, model_version, or custom metrics.
     *
     * @param metrics the metrics to attach to the commit
     */
    public void setMetrics(CyclesMetrics metrics) { this.metrics = metrics; }
    /** Returns the metrics to include in the commit, or {@code null}. */
    public CyclesMetrics getMetrics() { return metrics; }

    /**
     * Sets metadata to include in the commit request.
     * Call this inside your {@code @Cycles}-guarded method to attach
     * arbitrary key-value audit/debugging data to the commit.
     *
     * @param commitMetadata the metadata map to attach to the commit
     */
    public void setCommitMetadata(Map<String, Object> commitMetadata) { this.commitMetadata = commitMetadata; }
    /** Returns the commit metadata, or {@code null}. */
    public Map<String, Object> getCommitMetadata() { return commitMetadata; }
}
