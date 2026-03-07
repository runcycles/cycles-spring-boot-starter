package io.runcycles.client.java.spring.context;

import io.runcycles.client.java.spring.model.Caps;
import io.runcycles.client.java.spring.model.Decision;

public class CyclesReservationContext {

    private final String reservationId;
    private final long estimate;
    private final Decision decision;
    private final Caps caps;
    private final Long expiresAtMs;

    public CyclesReservationContext(String reservationId, long estimate,
                                   Decision decision, Caps caps, Long expiresAtMs) {
        this.reservationId = reservationId;
        this.estimate = estimate;
        this.decision = decision;
        this.caps = caps;
        this.expiresAtMs = expiresAtMs;
    }

    public String getReservationId() { return reservationId; }
    public long getEstimate() { return estimate; }
    public Decision getDecision() { return decision; }
    public Caps getCaps() { return caps; }
    public Long getExpiresAtMs() { return expiresAtMs; }

    public boolean hasCaps() { return caps != null; }
    public boolean isExpiringSoon(long thresholdMs) {
        if (expiresAtMs == null) return false;
        return (expiresAtMs - System.currentTimeMillis()) < thresholdMs;
    }
}
