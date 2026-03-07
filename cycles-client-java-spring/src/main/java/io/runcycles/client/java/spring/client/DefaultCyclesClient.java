package io.runcycles.client.java.spring.client;

import io.runcycles.client.java.spring.model.CyclesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

public class DefaultCyclesClient implements CyclesClient {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCyclesClient.class);

    private final WebClient webClient;

    public DefaultCyclesClient(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public CyclesResponse<Map<String,Object>> createReservation(Object body) {
        LOG.info("Executing reservation create via cycles remote service");
        return executeHttpPostRequest("/v1/reservations", body);
    }

    @Override
    public CyclesResponse<Map<String,Object>> commitReservation(String reservationId, Object body) {
        return executeHttpPostRequest("/v1/reservations/{id}/commit", reservationId, body);
    }

    @Override
    public CyclesResponse<Map<String,Object>> releaseReservation(String reservationId, Object body) {
        return executeHttpPostRequest("/v1/reservations/{id}/release", reservationId, body);
    }

    @Override
    public CyclesResponse<Map<String,Object>> extendReservation(String reservationId, Object body) {
        return executeHttpPostRequest("/v1/reservations/{id}/extend", reservationId, body);
    }

    private CyclesResponse<Map<String,Object>> executeHttpPostRequest(String uri, Object body) {
        return executeHttpPostRequest(uri, null, body);
    }

    private CyclesResponse<Map<String,Object>> executeHttpPostRequest(String uri, String pathParam, Object body) {
        LOG.info("Started executing HTTP request: uri={}, pathParam={}", uri, pathParam);
        try {
            WebClient.RequestHeadersSpec<?> request = webClient.post()
                    .uri(uriBuilder -> {
                        if (pathParam != null) {
                            return uriBuilder.path(uri).build(pathParam);
                        }
                        return uriBuilder.path(uri).build();
                    })
                    .bodyValue(body);

            return request.exchangeToMono(response ->
                            response.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                                    .defaultIfEmpty(Map.of())
                                    .map(responseBody -> {
                                        int status = response.statusCode().value();
                                        if (response.statusCode().is2xxSuccessful()) {
                                            return CyclesResponse.success(status, responseBody);
                                        }
                                        String error = responseBody.get("message") != null
                                                ? String.valueOf(responseBody.get("message"))
                                                : responseBody.get("error") != null
                                                ? String.valueOf(responseBody.get("error"))
                                                : null;
                                        return CyclesResponse.httpError(status, error, responseBody);
                                    })
                    ).block();

        } catch (Exception ex) {
            LOG.error("Failed to execute HTTP request: uri={}, pathParam={}", uri, pathParam, ex);
            return CyclesResponse.transportError(ex);
        } finally {
            LOG.info("Finished execution of HTTP request: uri={}, pathParam={}", uri, pathParam);
        }
    }
}
