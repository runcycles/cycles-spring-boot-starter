package io.runcycles.client.java.spring.client;

import io.runcycles.client.java.spring.model.CyclesResponse;
import io.runcycles.client.java.spring.model.ErrorResponse;
import io.runcycles.client.java.spring.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * Default {@link CyclesClient} implementation backed by Spring {@link WebClient}.
 *
 * <p>Communicates with the Cycles API over HTTP, automatically extracting idempotency
 * keys from request bodies and mapping responses into {@link CyclesResponse} instances.
 * Transport errors are captured rather than thrown, allowing callers to handle them
 * uniformly via {@link CyclesResponse#isTransportError()}.
 */
public class DefaultCyclesClient implements CyclesClient {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCyclesClient.class);
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;

    /**
     * Creates a new client backed by the given WebClient.
     *
     * @param webClient the pre-configured WebClient for Cycles API calls
     */
    public DefaultCyclesClient(WebClient webClient) {
        this.webClient = webClient;
    }

    // ---- Core reservation lifecycle ----

    @Override
    public CyclesResponse<Map<String, Object>> createReservation(Object body) {
        LOG.debug("Executing reservation create via cycles remote service");
        return executePost("/v1/reservations", null, body);
    }

    @Override
    public CyclesResponse<Map<String, Object>> commitReservation(String reservationId, Object body) {
        return executePost("/v1/reservations/{reservation_id}/commit", reservationId, body);
    }

    @Override
    public CyclesResponse<Map<String, Object>> releaseReservation(String reservationId, Object body) {
        return executePost("/v1/reservations/{reservation_id}/release", reservationId, body);
    }

    @Override
    public CyclesResponse<Map<String, Object>> extendReservation(String reservationId, Object body) {
        return executePost("/v1/reservations/{reservation_id}/extend", reservationId, body);
    }

    // ---- Optional endpoints ----

    @Override
    public CyclesResponse<Map<String, Object>> decide(Object body) {
        LOG.debug("Executing decide via cycles remote service");
        return executePost("/v1/decide", null, body);
    }

    @Override
    public CyclesResponse<Map<String, Object>> listReservations(Map<String, String> queryParams) {
        LOG.debug("Listing reservations: queryParams={}", queryParams);
        return executeGet("/v1/reservations", null, queryParams);
    }

    @Override
    public CyclesResponse<Map<String, Object>> getReservation(String reservationId) {
        LOG.debug("Getting reservation: reservationId={}", reservationId);
        return executeGet("/v1/reservations/{reservation_id}", reservationId, null);
    }

    @Override
    public CyclesResponse<Map<String, Object>> getBalances(Map<String, String> queryParams) {
        LOG.debug("Querying balances: queryParams={}", queryParams);
        return executeGet("/v1/balances", null, queryParams);
    }

    @Override
    public CyclesResponse<Map<String, Object>> createEvent(Object body) {
        LOG.debug("Creating event via cycles remote service");
        return executePost("/v1/events", null, body);
    }

    // ---- HTTP helpers ----

    private CyclesResponse<Map<String, Object>> executePost(String uri, String pathParam, Object body) {
        LOG.debug("Started executing POST: uri={}, pathParam={}", uri, pathParam);
        try {
            String idempotencyKey = extractIdempotencyKey(body);

            WebClient.RequestHeadersSpec<?> request = webClient.post()
                    .uri(uriBuilder -> {
                        if (pathParam != null) {
                            return uriBuilder.path(uri).build(pathParam);
                        }
                        return uriBuilder.path(uri).build();
                    })
                    .headers(headers -> {
                        if (idempotencyKey != null) {
                            headers.set(Constants.X_IDEMPOTENCY_KEY_HEADER, idempotencyKey);
                        }
                    })
                    .bodyValue(body);

            return exchangeAndMap(request);

        } catch (Exception ex) {
            LOG.error("Failed to execute POST: uri={}, pathParam={}", uri, pathParam, ex);
            return CyclesResponse.transportError(ex);
        } finally {
            LOG.debug("Finished POST: uri={}, pathParam={}", uri, pathParam);
        }
    }

    private CyclesResponse<Map<String, Object>> executeGet(String uri, String pathParam,
                                                           Map<String, String> queryParams) {
        LOG.debug("Started executing GET: uri={}, pathParam={}, queryParams={}", uri, pathParam, queryParams);
        try {
            WebClient.RequestHeadersSpec<?> request = webClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(uri);
                        if (queryParams != null) {
                            queryParams.forEach(uriBuilder::queryParam);
                        }
                        if (pathParam != null) {
                            return uriBuilder.build(pathParam);
                        }
                        return uriBuilder.build();
                    });

            return exchangeAndMap(request);

        } catch (Exception ex) {
            LOG.error("Failed to execute GET: uri={}, pathParam={}", uri, pathParam, ex);
            return CyclesResponse.transportError(ex);
        } finally {
            LOG.debug("Finished GET: uri={}, pathParam={}", uri, pathParam);
        }
    }

    private CyclesResponse<Map<String, Object>> exchangeAndMap(WebClient.RequestHeadersSpec<?> request) {
        return request.exchangeToMono(response ->
                response.bodyToMono(MAP_TYPE)
                        .defaultIfEmpty(Map.of())
                        .map(responseBody -> {
                            int status = response.statusCode().value();
                            if (response.statusCode().is2xxSuccessful()) {
                                return CyclesResponse.success(status, responseBody);
                            }
                            // Use structured ErrorResponse parsing for consistent error extraction
                            ErrorResponse errorResponse = ErrorResponse.fromMap(responseBody);
                            String errorMessage;
                            if (errorResponse != null && errorResponse.getMessage() != null) {
                                errorMessage = errorResponse.getMessage();
                            } else if (responseBody.get("error") != null) {
                                errorMessage = String.valueOf(responseBody.get("error"));
                            } else {
                                errorMessage = "HTTP " + status;
                            }
                            return CyclesResponse.httpError(status, errorMessage, responseBody);
                        })
        ).block();
    }

    private String extractIdempotencyKey(Object body) {
        // Fast path: Map bodies (used by CyclesRequestBuilderService)
        if (body instanceof Map<?, ?> map) {
            Object key = map.get(Constants.IDEMPOTENCY_KEY);
            return key != null ? String.valueOf(key) : null;
        }
        // POJO bodies: try reflection for getIdempotencyKey() getter
        try {
            Method getter = body.getClass().getMethod("getIdempotencyKey");
            Object key = getter.invoke(body);
            return key != null ? String.valueOf(key) : null;
        } catch (Exception e) {
            LOG.debug("Could not extract idempotency_key from body type {}: {}",
                    body.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }
}
