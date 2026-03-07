package io.runcycles.client.java.spring.model;

public class CyclesProtocolException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String reasonCode;
    private final int httpStatus;
    private final Long retryAfterMs;

    public CyclesProtocolException(String message) {
        this(message, null, null, -1, null);
    }

    public CyclesProtocolException(String message, ErrorCode errorCode, String reasonCode, int httpStatus) {
        this(message, errorCode, reasonCode, httpStatus, null);
    }

    public CyclesProtocolException(String message, ErrorCode errorCode, String reasonCode,
                                   int httpStatus, Long retryAfterMs) {
        super(message);
        this.errorCode = errorCode;
        this.reasonCode = reasonCode;
        this.httpStatus = httpStatus;
        this.retryAfterMs = retryAfterMs;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public String getReasonCode() { return reasonCode; }
    public int getHttpStatus() { return httpStatus; }
    public Long getRetryAfterMs() { return retryAfterMs; }
    public boolean isBudgetExceeded() { return errorCode == ErrorCode.BUDGET_EXCEEDED; }
    public boolean isOverdraftLimitExceeded() { return errorCode == ErrorCode.OVERDRAFT_LIMIT_EXCEEDED; }
    public boolean isDebtOutstanding() { return errorCode == ErrorCode.DEBT_OUTSTANDING; }
    public boolean isReservationExpired() { return errorCode == ErrorCode.RESERVATION_EXPIRED; }
    public boolean isReservationFinalized() { return errorCode == ErrorCode.RESERVATION_FINALIZED; }
}
