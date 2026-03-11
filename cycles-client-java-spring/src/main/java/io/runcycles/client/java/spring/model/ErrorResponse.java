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
     * Parse an ErrorResponse from the raw response body map.
     * Returns null if the body does not contain a recognizable error envelope.
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

    /** Returns the structured error code. */
    public ErrorCode getErrorCode() { return errorCode; }
    /** Returns the human-readable error message. */
    public String getMessage() { return message; }
    /** Returns the server-assigned request ID for debugging. */
    public String getRequestId() { return requestId; }
    /** Returns additional error details, or {@code null}. */
    public Map<String, Object> getDetails() { return details; }

    /**
     * Convert this error response into a CyclesProtocolException.
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
