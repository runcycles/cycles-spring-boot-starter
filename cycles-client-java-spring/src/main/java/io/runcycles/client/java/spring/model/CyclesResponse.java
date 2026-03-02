package io.runcycles.client.java.spring.model;

import org.springframework.util.StringUtils;

import java.util.Map;

public class CyclesResponse <T>{
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
    public static <T> CyclesResponse<T> success(int status, T body) {
        return new CyclesResponse<>(status, body, null, false, null);
    }

    public static <T> CyclesResponse<T> httpError(int status, String errorMessage, T body) {
        return new CyclesResponse<>(status, body, errorMessage, false, null);
    }

    public static <T> CyclesResponse<T> transportError(Throwable ex) {
        return new CyclesResponse<>(-1, null, null, true, ex);
    }
    public String getBodyAttributeAsString(String key){
        if (key != null && body != null && body instanceof Map<?, ?> map){
            return (String)map.get(key) ;
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
        return status >= 500;
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
