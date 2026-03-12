package io.runcycles.demo.client.spring.error;

import io.runcycles.client.java.spring.model.CyclesProtocolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global error handler that converts CyclesProtocolException into structured JSON responses.
 *
 * Demonstrates how to handle budget-related errors from the Cycles protocol,
 * including budget exceeded, reservation expired, and overdraft limit errors.
 */
@RestControllerAdvice
public class CyclesExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(CyclesExceptionHandler.class);

    @ExceptionHandler(CyclesProtocolException.class)
    public ResponseEntity<Map<String, Object>> handleCyclesException(CyclesProtocolException ex) {
        LOG.warn("Cycles protocol error: errorCode={}, reasonCode={}, httpStatus={}, message={}",
                ex.getErrorCode(), ex.getReasonCode(), ex.getHttpStatus(), ex.getMessage());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", true);
        body.put("message", ex.getMessage());

        if (ex.getErrorCode() != null) {
            body.put("errorCode", ex.getErrorCode().name());
        }
        if (ex.getReasonCode() != null) {
            body.put("reasonCode", ex.getReasonCode());
        }
        body.put("cyclesHttpStatus", ex.getHttpStatus());

        if (ex.getRetryAfterMs() != null) {
            body.put("retryAfterMs", ex.getRetryAfterMs());
        }

        // Categorize the error using convenience methods
        String category;
        if (ex.isBudgetExceeded()) {
            category = "BUDGET_EXCEEDED";
        } else if (ex.isOverdraftLimitExceeded()) {
            category = "OVERDRAFT_LIMIT_EXCEEDED";
        } else if (ex.isDebtOutstanding()) {
            category = "DEBT_OUTSTANDING";
        } else if (ex.isReservationExpired()) {
            category = "RESERVATION_EXPIRED";
        } else if (ex.isReservationFinalized()) {
            category = "RESERVATION_FINALIZED";
        } else if (ex.isIdempotencyMismatch()) {
            category = "IDEMPOTENCY_MISMATCH";
        } else if (ex.isUnitMismatch()) {
            category = "UNIT_MISMATCH";
        } else {
            category = "OTHER";
        }
        body.put("errorCategory", category);

        // Map Cycles server HTTP status to the outgoing response status.
        // Protocol error codes and their HTTP statuses:
        //   409 = BUDGET_EXCEEDED, OVERDRAFT_LIMIT_EXCEEDED, DEBT_OUTSTANDING,
        //         RESERVATION_FINALIZED, IDEMPOTENCY_MISMATCH
        //   410 = RESERVATION_EXPIRED
        //   400 = INVALID_REQUEST, UNIT_MISMATCH
        //   401 = UNAUTHORIZED, 403 = FORBIDDEN, 404 = NOT_FOUND, 500 = INTERNAL_ERROR
        HttpStatus status = switch (ex.getHttpStatus()) {
            case 409 -> HttpStatus.CONFLICT;            // Budget exceeded, debt, overdraft, conflicts
            case 410 -> HttpStatus.GONE;                // Reservation expired
            case 400 -> HttpStatus.BAD_REQUEST;         // Validation errors, unit mismatch
            case 404 -> HttpStatus.NOT_FOUND;           // Reservation not found
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };

        return ResponseEntity.status(status).body(body);
    }
}
