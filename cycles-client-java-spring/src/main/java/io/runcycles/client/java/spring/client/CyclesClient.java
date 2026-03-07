package io.runcycles.client.java.spring.client;

import io.runcycles.client.java.spring.model.CyclesResponse;

import java.util.Map;

public interface CyclesClient {

    // Core reservation lifecycle
    CyclesResponse<Map<String, Object>> createReservation(Object body);
    CyclesResponse<Map<String, Object>> commitReservation(String reservationId, Object body);
    CyclesResponse<Map<String, Object>> releaseReservation(String reservationId, Object body);
    CyclesResponse<Map<String, Object>> extendReservation(String reservationId, Object body);

    // Optional: preflight decision (no reservation created)
    CyclesResponse<Map<String, Object>> decide(Object body);

    // Optional: list reservations with query filters
    CyclesResponse<Map<String, Object>> listReservations(Map<String, String> queryParams);

    // Optional: get reservation detail by id
    CyclesResponse<Map<String, Object>> getReservation(String reservationId);

    // Optional: query balances
    CyclesResponse<Map<String, Object>> getBalances(Map<String, String> queryParams);

    // Optional: post-only accounting event (no reservation)
    CyclesResponse<Map<String, Object>> createEvent(Object body);
}
