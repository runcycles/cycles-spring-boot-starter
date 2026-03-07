package io.runcycles.client.java.spring.model;

public class CyclesProtocolException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String reasonCode;
    private final int httpStatus;

    public CyclesProtocolException(String message) {
        this(message, null, null, -1);
    }

    public CyclesProtocolException(String message, ErrorCode errorCode, String reasonCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.reasonCode = reasonCode;
        this.httpStatus = httpStatus;
    }

    public ErrorCode getErrorCode() { return errorCode; }
    public String getReasonCode() { return reasonCode; }
    public int getHttpStatus() { return httpStatus; }
    public boolean isBudgetExceeded() { return errorCode == ErrorCode.BUDGET_EXCEEDED; }
    public boolean isOverdraftLimitExceeded() { return errorCode == ErrorCode.OVERDRAFT_LIMIT_EXCEEDED; }
    public boolean isReservationExpired() { return errorCode == ErrorCode.RESERVATION_EXPIRED; }
    public boolean isReservationFinalized() { return errorCode == ErrorCode.RESERVATION_FINALIZED; }
}
