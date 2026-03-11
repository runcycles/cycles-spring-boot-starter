package io.runcycles.client.java.spring.model;

import java.util.Map;

/**
 * Uniform wrapper for all Cycles API responses, distinguishing between
 * successful results, HTTP-level errors, and transport-level failures.
 *
 * <p>Use the static factory methods ({@link #success}, {@link #httpError},
 * {@link #transportError}) to create instances. Query methods like
 * {@link #is2xx()}, {@link #is4xx()}, {@link #is5xx()}, and
 * {@link #isTransportError()} enable branching on response category
 * without inspecting raw status codes.
 *
 * @param <T> the response body type (typically {@code Map<String, Object>})
 */
public class CyclesResponse<T> {
    private final int status;
    private final T body;
    private final String errorMessage;
    private final boolean transportError;
    private final Throwable transportException;

    private CyclesResponse(int status,
                           T body,
                           String errorMessage,
                           boolean transportError,
                           Throwable transportException) {
        this.status = status;
        this.body = body;
        this.errorMessage = errorMessage;
        this.transportError = transportError;
        this.transportException = transportException;
    }

    /** Creates a successful response with the given HTTP status and body. */
    public static <T> CyclesResponse<T> success(int status, T body) {
        return new CyclesResponse<>(status, body, null, false, null);
    }

    /** Creates an HTTP-error response (4xx/5xx) with the error message and body. */
    public static <T> CyclesResponse<T> httpError(int status, String errorMessage, T body) {
        return new CyclesResponse<>(status, body, errorMessage, false, null);
    }

    /** Creates a transport-error response (e.g. connection timeout, DNS failure). */
    public static <T> CyclesResponse<T> transportError(Throwable ex) {
        String message = ex != null ? ex.getMessage() : "Unknown transport error";
        return new CyclesResponse<>(-1, null, message, true, ex);
    }

    public String getBodyAttributeAsString(String key) {
        if (key != null && body != null && body instanceof Map<?, ?> map) {
            Object value = map.get(key);
            return value != null ? String.valueOf(value) : null;
        }
        return null;
    }

    public boolean is2xx() {
        return status >= 200 && status < 300;
    }

    public boolean is4xx() {
        return status >= 400 && status < 500;
    }

    public boolean is5xx() {
        return status >= 500 && status < 600;
    }

    public boolean isTransportError() {
        return transportError;
    }

    public int getStatus() {
        return status;
    }

    public T getBody() {
        return body;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Throwable getTransportException() {
        return transportException;
    }

    /**
     * Parse the body as a typed ErrorResponse if this is an error response.
     * Returns null if body is not a Map or does not contain a recognizable error envelope.
     */
    @SuppressWarnings("unchecked")
    public ErrorResponse getErrorResponse() {
        if (body instanceof Map<?, ?> map) {
            return ErrorResponse.fromMap((Map<String, Object>) map);
        }
        return null;
    }

    @Override
    public String toString() {
        return "CyclesResponse{" +
                "status=" + status +
                ", body=" + body +
                ", errorMessage='" + errorMessage + '\'' +
                ", transportError=" + transportError +
                ", transportException=" + transportException +
                '}';
    }
}
