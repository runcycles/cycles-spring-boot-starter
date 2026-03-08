package io.runcycles.client.java.spring.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CyclesResponse")
class CyclesResponseTest {

    // ========================================================================
    // Factory methods
    // ========================================================================

    @Nested
    @DisplayName("Factory methods")
    class FactoryMethods {

        @Test
        void successShouldSetFieldsCorrectly() {
            CyclesResponse<String> resp = CyclesResponse.success(200, "body");

            assertThat(resp.getStatus()).isEqualTo(200);
            assertThat(resp.getBody()).isEqualTo("body");
            assertThat(resp.getErrorMessage()).isNull();
            assertThat(resp.isTransportError()).isFalse();
            assertThat(resp.getTransportException()).isNull();
        }

        @Test
        void httpErrorShouldSetFieldsCorrectly() {
            CyclesResponse<String> resp = CyclesResponse.httpError(409, "Budget exceeded", "error-body");

            assertThat(resp.getStatus()).isEqualTo(409);
            assertThat(resp.getBody()).isEqualTo("error-body");
            assertThat(resp.getErrorMessage()).isEqualTo("Budget exceeded");
            assertThat(resp.isTransportError()).isFalse();
        }

        @Test
        void transportErrorShouldSetFieldsCorrectly() {
            RuntimeException ex = new RuntimeException("connection reset");
            CyclesResponse<String> resp = CyclesResponse.transportError(ex);

            assertThat(resp.getStatus()).isEqualTo(-1);
            assertThat(resp.getBody()).isNull();
            assertThat(resp.getErrorMessage()).isEqualTo("connection reset");
            assertThat(resp.isTransportError()).isTrue();
            assertThat(resp.getTransportException()).isSameAs(ex);
        }

        @Test
        void transportErrorWithNullException() {
            CyclesResponse<String> resp = CyclesResponse.transportError(null);

            assertThat(resp.isTransportError()).isTrue();
            assertThat(resp.getErrorMessage()).isEqualTo("Unknown transport error");
        }
    }

    // ========================================================================
    // Status classification
    // ========================================================================

    @Nested
    @DisplayName("Status classification")
    class StatusClassification {

        @Test
        void is2xxBoundaries() {
            assertThat(CyclesResponse.success(199, null).is2xx()).isFalse();
            assertThat(CyclesResponse.success(200, null).is2xx()).isTrue();
            assertThat(CyclesResponse.success(204, null).is2xx()).isTrue();
            assertThat(CyclesResponse.success(299, null).is2xx()).isTrue();
            assertThat(CyclesResponse.success(300, null).is2xx()).isFalse();
        }

        @Test
        void is4xxBoundaries() {
            assertThat(CyclesResponse.httpError(399, null, null).is4xx()).isFalse();
            assertThat(CyclesResponse.httpError(400, null, null).is4xx()).isTrue();
            assertThat(CyclesResponse.httpError(409, null, null).is4xx()).isTrue();
            assertThat(CyclesResponse.httpError(499, null, null).is4xx()).isTrue();
            assertThat(CyclesResponse.httpError(500, null, null).is4xx()).isFalse();
        }

        @Test
        void is5xxBoundaries() {
            assertThat(CyclesResponse.httpError(499, null, null).is5xx()).isFalse();
            assertThat(CyclesResponse.httpError(500, null, null).is5xx()).isTrue();
            assertThat(CyclesResponse.httpError(503, null, null).is5xx()).isTrue();
            assertThat(CyclesResponse.httpError(599, null, null).is5xx()).isTrue();
            assertThat(CyclesResponse.httpError(600, null, null).is5xx()).isFalse();
        }

        @Test
        void transportErrorStatusCodes() {
            CyclesResponse<String> resp = CyclesResponse.transportError(new RuntimeException());
            assertThat(resp.is2xx()).isFalse();
            assertThat(resp.is4xx()).isFalse();
            assertThat(resp.is5xx()).isFalse();
            assertThat(resp.isTransportError()).isTrue();
        }
    }

    // ========================================================================
    // getBodyAttributeAsString
    // ========================================================================

    @Nested
    @DisplayName("getBodyAttributeAsString")
    class BodyAttributeAsString {

        @Test
        void shouldExtractFromMapBody() {
            CyclesResponse<Map<String, Object>> resp = CyclesResponse.success(200,
                    Map.of("error", "BUDGET_EXCEEDED", "count", 42));

            assertThat(resp.getBodyAttributeAsString("error")).isEqualTo("BUDGET_EXCEEDED");
            assertThat(resp.getBodyAttributeAsString("count")).isEqualTo("42");
        }

        @Test
        void shouldReturnNullForMissingKey() {
            CyclesResponse<Map<String, Object>> resp = CyclesResponse.success(200,
                    Map.of("error", "BUDGET_EXCEEDED"));

            assertThat(resp.getBodyAttributeAsString("missing")).isNull();
        }

        @Test
        void shouldReturnNullForNullKey() {
            CyclesResponse<Map<String, Object>> resp = CyclesResponse.success(200,
                    Map.of("error", "BUDGET_EXCEEDED"));

            assertThat(resp.getBodyAttributeAsString(null)).isNull();
        }

        @Test
        void shouldReturnNullForNullBody() {
            CyclesResponse<Map<String, Object>> resp = CyclesResponse.success(200, null);

            assertThat(resp.getBodyAttributeAsString("error")).isNull();
        }

        @Test
        void shouldReturnNullForNonMapBody() {
            CyclesResponse<String> resp = CyclesResponse.success(200, "not a map");

            assertThat(resp.getBodyAttributeAsString("error")).isNull();
        }
    }

    // ========================================================================
    // getErrorResponse
    // ========================================================================

    @Nested
    @DisplayName("getErrorResponse")
    class GetErrorResponse {

        @Test
        void shouldParseErrorFromMapBody() {
            Map<String, Object> body = Map.of(
                    "error", "BUDGET_EXCEEDED",
                    "message", "No budget",
                    "request_id", "req-1"
            );
            CyclesResponse<Map<String, Object>> resp = CyclesResponse.httpError(409, "No budget", body);

            ErrorResponse errorResp = resp.getErrorResponse();

            assertThat(errorResp).isNotNull();
            assertThat(errorResp.getErrorCode()).isEqualTo(ErrorCode.BUDGET_EXCEEDED);
            assertThat(errorResp.getMessage()).isEqualTo("No budget");
        }

        @Test
        void shouldReturnNullForNullBody() {
            CyclesResponse<Map<String, Object>> resp = CyclesResponse.transportError(new RuntimeException());

            assertThat(resp.getErrorResponse()).isNull();
        }

        @Test
        void shouldReturnNullForNonErrorBody() {
            Map<String, Object> body = Map.of("status", "COMMITTED");
            CyclesResponse<Map<String, Object>> resp = CyclesResponse.success(200, body);

            ErrorResponse errorResp = resp.getErrorResponse();

            // No "error" field, but fromMap returns null when error field is absent
            assertThat(errorResp).isNull();
        }
    }

    // ========================================================================
    // toString
    // ========================================================================

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        void shouldIncludeAllFields() {
            CyclesResponse<String> resp = CyclesResponse.success(200, "ok");
            String str = resp.toString();

            assertThat(str).contains("status=200");
            assertThat(str).contains("body=ok");
        }
    }
}
