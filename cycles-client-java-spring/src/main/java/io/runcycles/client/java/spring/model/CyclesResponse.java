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

    /**
     * Creates a successful response with the given HTTP status and body.
     *
     * @param status the HTTP status code
     * @param body   the response body
     * @param <T>    the body type
     * @return a new success response
     */
    public static <T> CyclesResponse<T> success(int status, T body) {
        return new CyclesResponse<>(status, body, null, false, null);
    }

    /**
     * Creates an HTTP-error response (4xx/5xx) with the error message and body.
     *
     * @param status       the HTTP status code
     * @param errorMessage the error message
     * @param body         the response body
     * @param <T>          the body type
     * @return a new HTTP-error response
     */
    public static <T> CyclesResponse<T> httpError(int status, String errorMessage, T body) {
        return new CyclesResponse<>(status, body, errorMessage, false, null);
    }

    /**
     * Creates a transport-error response (e.g. connection timeout, DNS failure).
     *
     * @param ex  the transport exception
     * @param <T> the body type
     * @return a new transport-error response
     */
    public static <T> CyclesResponse<T> transportError(Throwable ex) {
        String message = ex != null ? ex.getMessage() : "Unknown transport error";
        return new CyclesResponse<>(-1, null, message, true, ex);
    }

    /**
     * Extracts a string attribute from the body map.
     *
     * @param key the attribute key
     * @return the string value, or {@code null}
     */
    public String getBodyAttributeAsString(String key) {
        if (key != null && body != null && body instanceof Map<?, ?> map) {
            Object value = map.get(key);
            return value != null ? String.valueOf(value) : null;
        }
        return null;
    }

    /**
     * Returns whether the response status is in the 2xx range.
     *
     * @return {@code true} if successful
     */
    public boolean is2xx() {
        return status >= 200 && status < 300;
    }

    /**
     * Returns whether the response status is in the 4xx range.
     *
     * @return {@code true} if a client error
     */
    public boolean is4xx() {
        return status >= 400 && status < 500;
    }

    /**
     * Returns whether the response status is in the 5xx range.
     *
     * @return {@code true} if a server error
     */
    public boolean is5xx() {
        return status >= 500 && status < 600;
    }

    /**
     * Returns whether this is a transport-level error.
     *
     * @return {@code true} if the request failed at the transport layer
     */
    public boolean isTransportError() {
        return transportError;
    }

    /**
     * Returns the HTTP status code.
     *
     * @return the status code, or {@code -1} for transport errors
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the response body.
     *
     * @return the body, or {@code null} for transport errors
     */
    public T getBody() {
        return body;
    }

    /**
     * Returns the error message.
     *
     * @return the error message, or {@code null} for successful responses
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Returns the transport exception.
     *
     * @return the exception, or {@code null} if not a transport error
     */
    public Throwable getTransportException() {
        return transportException;
    }

    /**
     * Parses the body as a typed {@link ErrorResponse} if this is an error response.
     *
     * @return the parsed error response, or {@code null} if the body does not
     *         contain a recognizable error envelope
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
