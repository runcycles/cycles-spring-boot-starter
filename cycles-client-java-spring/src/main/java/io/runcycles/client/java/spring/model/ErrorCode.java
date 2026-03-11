package io.runcycles.client.java.spring.model;

/**
 * Enumeration of structured error codes returned by the Cycles API.
 *
 * <p>Maps directly to the {@code error} field in API error responses. Unknown or
 * unrecognized values are mapped to {@link #UNKNOWN}.
 */
public enum ErrorCode {
    /** The request payload is malformed or missing required fields. */
    INVALID_REQUEST,
    /** The API key is missing or invalid. */
    UNAUTHORIZED,
    /** The API key does not have permission for the requested operation. */
    FORBIDDEN,
    /** The requested resource (e.g. reservation) was not found. */
    NOT_FOUND,
    /** The budget has been fully consumed; no further reservations can be made. */
    BUDGET_EXCEEDED,
    /** The reservation has expired and can no longer be committed or extended. */
    RESERVATION_EXPIRED,
    /** The reservation has already been committed or released. */
    RESERVATION_FINALIZED,
    /** An idempotency key was reused with different request parameters. */
    IDEMPOTENCY_MISMATCH,
    /** The unit in the request does not match the reservation's unit. */
    UNIT_MISMATCH,
    /** The overdraft limit has been exceeded. */
    OVERDRAFT_LIMIT_EXCEEDED,
    /** Outstanding debt must be settled before new reservations are allowed. */
    DEBT_OUTSTANDING,
    /** A transient server-side error occurred (retryable). */
    INTERNAL_ERROR,
    /** An error code not recognized by this client version. */
    UNKNOWN;

    /**
     * Parses an error code from its string representation.
     *
     * @param value the error code string, or {@code null}
     * @return the matching {@code ErrorCode}, {@link #UNKNOWN} for unrecognized values,
     *         or {@code null} if the input is {@code null}
     */
    public static ErrorCode fromString(String value) {
        if (value == null) return null;
        try {
            return ErrorCode.valueOf(value);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    /**
     * Returns {@code true} if this error code indicates a transient failure that may succeed on retry.
     *
     * @return {@code true} if this error code indicates a transient failure that may succeed on retry
     */
    public boolean isRetryable() {
        return this == INTERNAL_ERROR || this == UNKNOWN;
    }
}
