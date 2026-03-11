package io.runcycles.client.java.spring.client;

import io.runcycles.client.java.spring.model.*;

import java.util.Map;

/**
 * Client interface for the Cycles budget-management API.
 *
 * <p>Provides the full reservation lifecycle (create, commit, release, extend) as well as
 * optional endpoints for preflight decisions, balance queries, reservation listing, and
 * standalone accounting events.
 *
 * <p>Each method returns a {@link CyclesResponse} wrapping the raw response map. Typed
 * request DTO overloads are provided as {@code default} methods that delegate to the
 * raw-body variants.
 *
 * @see DefaultCyclesClient
 */
public interface CyclesClient {

    /**
     * Creates a new budget reservation.
     *
     * @param body the reservation request payload
     * @return the API response containing the reservation result
     */
    CyclesResponse<Map<String, Object>> createReservation(Object body);

    /**
     * Commits a previously created reservation, finalizing the actual usage amount.
     *
     * @param reservationId the reservation identifier
     * @param body          the commit request payload
     * @return the API response containing the commit result
     */
    CyclesResponse<Map<String, Object>> commitReservation(String reservationId, Object body);

    /**
     * Releases a reservation, returning any held budget back to the pool.
     *
     * @param reservationId the reservation identifier
     * @param body          the release request payload
     * @return the API response containing the release result
     */
    CyclesResponse<Map<String, Object>> releaseReservation(String reservationId, Object body);

    /**
     * Extends the TTL of an active reservation.
     *
     * @param reservationId the reservation identifier
     * @param body          the extend request payload
     * @return the API response containing the extend result
     */
    CyclesResponse<Map<String, Object>> extendReservation(String reservationId, Object body);

    /**
     * Performs a preflight budget decision without creating a reservation.
     *
     * @param body the decision request payload
     * @return the API response containing the decision result
     */
    CyclesResponse<Map<String, Object>> decide(Object body);

    /**
     * Lists reservations matching the given query filters.
     *
     * @param queryParams filter parameters (e.g. status, tenant, cursor)
     * @return the API response containing a paginated reservation list
     */
    CyclesResponse<Map<String, Object>> listReservations(Map<String, String> queryParams);

    /**
     * Retrieves the details of a single reservation.
     *
     * @param reservationId the reservation identifier
     * @return the API response containing the reservation detail
     */
    CyclesResponse<Map<String, Object>> getReservation(String reservationId);

    /**
     * Queries current balance information for the given scope.
     *
     * @param queryParams scope filters (e.g. tenant, workspace, app)
     * @return the API response containing balance data
     */
    CyclesResponse<Map<String, Object>> getBalances(Map<String, String> queryParams);

    /**
     * Creates a standalone accounting event (no reservation required).
     *
     * @param body the event creation request payload
     * @return the API response containing the event result
     */
    CyclesResponse<Map<String, Object>> createEvent(Object body);

    // ---- Typed request DTO overloads ----

    /** Typed overload for {@link #createReservation(Object)}. */
    default CyclesResponse<Map<String, Object>> createReservation(ReservationCreateRequest request) {
        return createReservation((Object) request.toMap());
    }

    /** Typed overload for {@link #commitReservation(String, Object)}. */
    default CyclesResponse<Map<String, Object>> commitReservation(String reservationId, CommitRequest request) {
        return commitReservation(reservationId, (Object) request.toMap());
    }

    /** Typed overload for {@link #releaseReservation(String, Object)}. */
    default CyclesResponse<Map<String, Object>> releaseReservation(String reservationId, ReleaseRequest request) {
        return releaseReservation(reservationId, (Object) request.toMap());
    }

    /** Typed overload for {@link #extendReservation(String, Object)}. */
    default CyclesResponse<Map<String, Object>> extendReservation(String reservationId, ReservationExtendRequest request) {
        return extendReservation(reservationId, (Object) request.toMap());
    }

    /** Typed overload for {@link #decide(Object)}. */
    default CyclesResponse<Map<String, Object>> decide(DecisionRequest request) {
        return decide((Object) request.toMap());
    }

    /** Typed overload for {@link #createEvent(Object)}. */
    default CyclesResponse<Map<String, Object>> createEvent(EventCreateRequest request) {
        return createEvent((Object) request.toMap());
    }
}
