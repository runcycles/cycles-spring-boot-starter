package io.runcycles.client.java.spring.client;

import io.runcycles.client.java.spring.model.CyclesResponse;

import java.util.Map;

public interface CyclesClient {

    CyclesResponse<Map<String,Object>> createReservation(Object body);
    CyclesResponse<Map<String,Object>> commitReservation(String reservationId, Object body);
    CyclesResponse<Map<String,Object>> releaseReservation(String reservationId, Object body);
    CyclesResponse<Map<String,Object>> extendReservation(String reservationId, Object body);
}
