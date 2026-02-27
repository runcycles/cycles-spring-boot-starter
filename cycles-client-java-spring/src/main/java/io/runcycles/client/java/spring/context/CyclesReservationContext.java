package io.runcycles.client.java.spring.context;

public class CyclesReservationContext {

    private final String reservationId;
    private final long estimate;

    public CyclesReservationContext(String reservationId, long estimate) {
        this.reservationId = reservationId;
        this.estimate = estimate;
    }

    public String getReservationId() { return reservationId; }
    public long getEstimate() { return estimate; }
}
