package io.runcycles.client.java.spring.client;

import io.runcycles.client.java.spring.model.*;

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

    // ---- Typed request DTO overloads ----

    default CyclesResponse<Map<String, Object>> createReservation(ReservationCreateRequest request) {
        return createReservation((Object) request.toMap());
    }

    default CyclesResponse<Map<String, Object>> commitReservation(String reservationId, CommitRequest request) {
        return commitReservation(reservationId, (Object) request.toMap());
    }

    default CyclesResponse<Map<String, Object>> releaseReservation(String reservationId, ReleaseRequest request) {
        return releaseReservation(reservationId, (Object) request.toMap());
    }

    default CyclesResponse<Map<String, Object>> extendReservation(String reservationId, ReservationExtendRequest request) {
        return extendReservation(reservationId, (Object) request.toMap());
    }

    default CyclesResponse<Map<String, Object>> decide(DecisionRequest request) {
        return decide((Object) request.toMap());
    }

    default CyclesResponse<Map<String, Object>> createEvent(EventCreateRequest request) {
        return createEvent((Object) request.toMap());
    }
}
