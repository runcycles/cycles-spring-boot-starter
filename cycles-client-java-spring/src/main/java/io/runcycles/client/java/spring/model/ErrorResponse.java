package io.runcycles.client.java.spring.model;

import java.util.Map;

/**
 * Typed representation of the Cycles Protocol error response envelope.
 * Matches the server's ErrorResponse schema: {error, message, request_id, details?}.
 */
public class ErrorResponse {
    private final ErrorCode errorCode;
    private final String message;
    private final String requestId;
    private final Map<String, Object> details;

    private ErrorResponse(ErrorCode errorCode, String message, String requestId, Map<String, Object> details) {
        this.errorCode = errorCode;
        this.message = message;
        this.requestId = requestId;
        this.details = details;
    }

    /**
     * Parses an {@code ErrorResponse} from the raw response body map.
     *
     * @param body the response body map, or {@code null}
     * @return the parsed error response, or {@code null} if the body does not
     *         contain a recognizable error envelope
     */
    @SuppressWarnings("unchecked")
    public static ErrorResponse fromMap(Map<String, Object> body) {
        if (body == null) return null;

        Object errorField = body.get("error");
        if (errorField == null) return null;

        ErrorCode errorCode = ErrorCode.fromString(String.valueOf(errorField));
        String message = body.get("message") instanceof String s ? s : null;
        String requestId = body.get("request_id") instanceof String s ? s : null;
        Map<String, Object> details = body.get("details") instanceof Map<?, ?> m
                ? (Map<String, Object>) m : null;

        return new ErrorResponse(errorCode, message, requestId, details);
    }

    /**
     * Returns the structured error code.
     *
     * @return The structured error code
     */
    public ErrorCode getErrorCode() { return errorCode; }
    /**
     * Returns the human-readable error message.
     *
     * @return The human-readable error message
     */
    public String getMessage() { return message; }
    /**
     * Returns the server-assigned request ID for debugging.
     *
     * @return The server-assigned request id for debugging
     */
    public String getRequestId() { return requestId; }
    /**
     * Returns additional error details, or {@code null}.
     *
     * @return Additional error details, or {@code null}
     */
    public Map<String, Object> getDetails() { return details; }

    /**
     * Converts this error response into a {@link CyclesProtocolException}.
     *
     * @param httpStatus the HTTP status code from the response
     * @return the protocol exception
     */
    public CyclesProtocolException toException(int httpStatus) {
        return new CyclesProtocolException(
                message != null ? message : String.valueOf(errorCode),
                errorCode,
                String.valueOf(errorCode),
                httpStatus
        );
    }

    @Override
    public String toString() {
        return "ErrorResponse{" +
                "errorCode=" + errorCode +
                ", message='" + message + '\'' +
                ", requestId='" + requestId + '\'' +
                ", details=" + details +
                '}';
    }
}
