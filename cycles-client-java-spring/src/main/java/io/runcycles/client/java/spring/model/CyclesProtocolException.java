package io.runcycles.client.java.spring.model;

/**
 * Runtime exception thrown when a Cycles protocol-level error occurs.
 *
 * <p>Carries structured error information from the API response — including an
 * {@link ErrorCode}, reason code, HTTP status, and optional retry-after hint — so
 * that callers can make informed decisions about retries and error handling.
 *
 * <p>Convenience query methods (e.g. {@link #isBudgetExceeded()},
 * {@link #isReservationExpired()}) are provided for common error conditions.
 */
public class CyclesProtocolException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String reasonCode;
    private final int httpStatus;
    private final Integer retryAfterMs;

    /**
     * Creates an exception with a message only (used for client-side validation errors).
     *
     * @param message human-readable error description
     */
    public CyclesProtocolException(String message) {
        this(message, null, null, -1, null);
    }

    /**
     * Creates an exception with error details from the API response.
     *
     * @param message    human-readable error description
     * @param errorCode  the structured error code, or {@code null}
     * @param reasonCode the server-provided reason code, or {@code null}
     * @param httpStatus the HTTP status code ({@code -1} if not applicable)
     */
    public CyclesProtocolException(String message, ErrorCode errorCode, String reasonCode, int httpStatus) {
        this(message, errorCode, reasonCode, httpStatus, null);
    }

    /**
     * Full constructor.
     *
     * @param message      human-readable error description
     * @param errorCode    the structured error code, or {@code null}
     * @param reasonCode   the server-provided reason code, or {@code null}
     * @param httpStatus   the HTTP status code ({@code -1} if not applicable)
     * @param retryAfterMs suggested retry delay in milliseconds, or {@code null}
     */
    public CyclesProtocolException(String message, ErrorCode errorCode, String reasonCode,
                                   int httpStatus, Integer retryAfterMs) {
        super(message);
        this.errorCode = errorCode;
        this.reasonCode = reasonCode;
        this.httpStatus = httpStatus;
        this.retryAfterMs = retryAfterMs;
    }

    /**
     * Returns the structured error code.
     *
     * @return the error code, or {@code null}
     */
    public ErrorCode getErrorCode() { return errorCode; }

    /**
     * Returns the server-provided reason code.
     *
     * @return the reason code, or {@code null}
     */
    public String getReasonCode() { return reasonCode; }

    /**
     * Returns the HTTP status code.
     *
     * @return the HTTP status, or {@code -1} if not applicable
     */
    public int getHttpStatus() { return httpStatus; }

    /**
     * Returns the suggested retry delay in milliseconds.
     *
     * @return the retry-after hint, or {@code null}
     */
    public Integer getRetryAfterMs() { return retryAfterMs; }

    /**
     * Returns whether this error indicates budget exceeded.
     *
     * @return {@code true} if the error code is {@code BUDGET_EXCEEDED}
     */
    public boolean isBudgetExceeded() { return errorCode == ErrorCode.BUDGET_EXCEEDED; }

    /**
     * Returns whether this error indicates overdraft limit exceeded.
     *
     * @return {@code true} if the error code is {@code OVERDRAFT_LIMIT_EXCEEDED}
     */
    public boolean isOverdraftLimitExceeded() { return errorCode == ErrorCode.OVERDRAFT_LIMIT_EXCEEDED; }

    /**
     * Returns whether this error indicates outstanding debt.
     *
     * @return {@code true} if the error code is {@code DEBT_OUTSTANDING}
     */
    public boolean isDebtOutstanding() { return errorCode == ErrorCode.DEBT_OUTSTANDING; }

    /**
     * Returns whether this error indicates the reservation has expired.
     *
     * @return {@code true} if the error code is {@code RESERVATION_EXPIRED}
     */
    public boolean isReservationExpired() { return errorCode == ErrorCode.RESERVATION_EXPIRED; }

    /**
     * Returns whether this error indicates the reservation is already finalized.
     *
     * @return {@code true} if the error code is {@code RESERVATION_FINALIZED}
     */
    public boolean isReservationFinalized() { return errorCode == ErrorCode.RESERVATION_FINALIZED; }

    /**
     * Returns whether this error indicates an idempotency key mismatch.
     *
     * @return {@code true} if the error code is {@code IDEMPOTENCY_MISMATCH}
     */
    public boolean isIdempotencyMismatch() { return errorCode == ErrorCode.IDEMPOTENCY_MISMATCH; }

    /**
     * Returns whether this error indicates a unit mismatch.
     *
     * @return {@code true} if the error code is {@code UNIT_MISMATCH}
     */
    public boolean isUnitMismatch() { return errorCode == ErrorCode.UNIT_MISMATCH; }
}
