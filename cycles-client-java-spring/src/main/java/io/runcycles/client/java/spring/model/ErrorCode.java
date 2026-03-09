package io.runcycles.client.java.spring.model;

public enum ErrorCode {
    INVALID_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    BUDGET_EXCEEDED,
    RESERVATION_EXPIRED,
    RESERVATION_FINALIZED,
    IDEMPOTENCY_MISMATCH,
    UNIT_MISMATCH,
    OVERDRAFT_LIMIT_EXCEEDED,
    DEBT_OUTSTANDING,
    INTERNAL_ERROR,
    TENANT_NOT_FOUND,
    TENANT_SUSPENDED,
    TENANT_CLOSED,
    BUDGET_NOT_FOUND,
    BUDGET_FROZEN,
    POLICY_VIOLATION,
    INSUFFICIENT_PERMISSIONS,
    KEY_REVOKED,
    KEY_EXPIRED,
    DUPLICATE_RESOURCE,
    UNKNOWN;

    public static ErrorCode fromString(String value) {
        if (value == null) return null;
        try {
            return ErrorCode.valueOf(value);
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    public boolean isRetryable() {
        return this == INTERNAL_ERROR || this == UNKNOWN;
    }
}
