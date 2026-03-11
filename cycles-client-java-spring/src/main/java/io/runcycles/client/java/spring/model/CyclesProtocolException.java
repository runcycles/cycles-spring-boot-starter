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

    /** Creates an exception with a message only (used for client-side validation errors). */
    public CyclesProtocolException(String message) {
        this(message, null, null, -1, null);
    }

    /** Creates an exception with error details from the API response. */
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

    public ErrorCode getErrorCode() { return errorCode; }
    public String getReasonCode() { return reasonCode; }
    public int getHttpStatus() { return httpStatus; }
    public Integer getRetryAfterMs() { return retryAfterMs; }
    public boolean isBudgetExceeded() { return errorCode == ErrorCode.BUDGET_EXCEEDED; }
    public boolean isOverdraftLimitExceeded() { return errorCode == ErrorCode.OVERDRAFT_LIMIT_EXCEEDED; }
    public boolean isDebtOutstanding() { return errorCode == ErrorCode.DEBT_OUTSTANDING; }
    public boolean isReservationExpired() { return errorCode == ErrorCode.RESERVATION_EXPIRED; }
    public boolean isReservationFinalized() { return errorCode == ErrorCode.RESERVATION_FINALIZED; }
    public boolean isIdempotencyMismatch() { return errorCode == ErrorCode.IDEMPOTENCY_MISMATCH; }
    public boolean isUnitMismatch() { return errorCode == ErrorCode.UNIT_MISMATCH; }
}
