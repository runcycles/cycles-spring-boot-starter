package io.runcycles.client.java.spring.client;

public interface CyclesClient {

    String createReservation(Object body);
    void commitReservation(String reservationId, Object body);
    void releaseReservation(String reservationId, Object body);
}
