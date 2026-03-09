package io.runcycles.client.java.spring.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CyclesProtocolException")
class CyclesProtocolExceptionTest {

    // ========================================================================
    // Constructors
    // ========================================================================

    @Nested
    @DisplayName("Constructors")
    class Constructors {

        @Test
        void messageOnlyConstructor() {
            var ex = new CyclesProtocolException("something failed");

            assertThat(ex.getMessage()).isEqualTo("something failed");
            assertThat(ex.getErrorCode()).isNull();
            assertThat(ex.getReasonCode()).isNull();
            assertThat(ex.getHttpStatus()).isEqualTo(-1);
            assertThat(ex.getRetryAfterMs()).isNull();
        }

        @Test
        void fourArgConstructor() {
            var ex = new CyclesProtocolException("denied", ErrorCode.BUDGET_EXCEEDED, "BUDGET_EXCEEDED", 409);

            assertThat(ex.getMessage()).isEqualTo("denied");
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.BUDGET_EXCEEDED);
            assertThat(ex.getReasonCode()).isEqualTo("BUDGET_EXCEEDED");
            assertThat(ex.getHttpStatus()).isEqualTo(409);
            assertThat(ex.getRetryAfterMs()).isNull();
        }

        @Test
        void fiveArgConstructorWithRetryAfter() {
            var ex = new CyclesProtocolException("denied", ErrorCode.BUDGET_EXCEEDED,
                    "BUDGET_EXCEEDED", 409, 5000);

            assertThat(ex.getRetryAfterMs()).isEqualTo(5000);
        }

        @Test
        void constructorWithNullErrorCode() {
            var ex = new CyclesProtocolException("error", null, null, 500, null);

            assertThat(ex.getErrorCode()).isNull();
            assertThat(ex.getReasonCode()).isNull();
        }
    }

    // ========================================================================
    // Boolean helpers
    // ========================================================================

    @Nested
    @DisplayName("Boolean helpers")
    class BooleanHelpers {

        @Test
        void isBudgetExceeded() {
            assertThat(exceptionWith(ErrorCode.BUDGET_EXCEEDED).isBudgetExceeded()).isTrue();
            assertThat(exceptionWith(ErrorCode.INTERNAL_ERROR).isBudgetExceeded()).isFalse();
            assertThat(exceptionWith(null).isBudgetExceeded()).isFalse();
        }

        @Test
        void isOverdraftLimitExceeded() {
            assertThat(exceptionWith(ErrorCode.OVERDRAFT_LIMIT_EXCEEDED).isOverdraftLimitExceeded()).isTrue();
            assertThat(exceptionWith(ErrorCode.BUDGET_EXCEEDED).isOverdraftLimitExceeded()).isFalse();
            assertThat(exceptionWith(null).isOverdraftLimitExceeded()).isFalse();
        }

        @Test
        void isDebtOutstanding() {
            assertThat(exceptionWith(ErrorCode.DEBT_OUTSTANDING).isDebtOutstanding()).isTrue();
            assertThat(exceptionWith(ErrorCode.BUDGET_EXCEEDED).isDebtOutstanding()).isFalse();
            assertThat(exceptionWith(null).isDebtOutstanding()).isFalse();
        }

        @Test
        void isReservationExpired() {
            assertThat(exceptionWith(ErrorCode.RESERVATION_EXPIRED).isReservationExpired()).isTrue();
            assertThat(exceptionWith(ErrorCode.BUDGET_EXCEEDED).isReservationExpired()).isFalse();
            assertThat(exceptionWith(null).isReservationExpired()).isFalse();
        }

        @Test
        void isReservationFinalized() {
            assertThat(exceptionWith(ErrorCode.RESERVATION_FINALIZED).isReservationFinalized()).isTrue();
            assertThat(exceptionWith(ErrorCode.BUDGET_EXCEEDED).isReservationFinalized()).isFalse();
            assertThat(exceptionWith(null).isReservationFinalized()).isFalse();
        }

        @Test
        void isIdempotencyMismatch() {
            assertThat(exceptionWith(ErrorCode.IDEMPOTENCY_MISMATCH).isIdempotencyMismatch()).isTrue();
            assertThat(exceptionWith(ErrorCode.BUDGET_EXCEEDED).isIdempotencyMismatch()).isFalse();
            assertThat(exceptionWith(null).isIdempotencyMismatch()).isFalse();
        }

        @Test
        void isUnitMismatch() {
            assertThat(exceptionWith(ErrorCode.UNIT_MISMATCH).isUnitMismatch()).isTrue();
            assertThat(exceptionWith(ErrorCode.BUDGET_EXCEEDED).isUnitMismatch()).isFalse();
            assertThat(exceptionWith(null).isUnitMismatch()).isFalse();
        }

        @Test
        void eachHelperReturnsTrueOnlyForItsOwnErrorCode() {
            // Verify mutual exclusivity: each error code triggers exactly one helper
            for (ErrorCode code : new ErrorCode[]{
                    ErrorCode.BUDGET_EXCEEDED, ErrorCode.OVERDRAFT_LIMIT_EXCEEDED,
                    ErrorCode.DEBT_OUTSTANDING, ErrorCode.RESERVATION_EXPIRED,
                    ErrorCode.RESERVATION_FINALIZED, ErrorCode.IDEMPOTENCY_MISMATCH,
                    ErrorCode.UNIT_MISMATCH}) {
                var ex = exceptionWith(code);
                int trueCount = 0;
                if (ex.isBudgetExceeded()) trueCount++;
                if (ex.isOverdraftLimitExceeded()) trueCount++;
                if (ex.isDebtOutstanding()) trueCount++;
                if (ex.isReservationExpired()) trueCount++;
                if (ex.isReservationFinalized()) trueCount++;
                if (ex.isIdempotencyMismatch()) trueCount++;
                if (ex.isUnitMismatch()) trueCount++;
                assertThat(trueCount).as("ErrorCode %s should trigger exactly one helper", code).isEqualTo(1);
            }
        }
    }

    private CyclesProtocolException exceptionWith(ErrorCode errorCode) {
        return new CyclesProtocolException("test", errorCode, null, 400, null);
    }
}
