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
        LOG.info("Executing reservation create via cycles remote service: requestBody={}",body);
        /*try {
            return webClient.post()
                    .uri("/v1/reservations")
                    .bodyValue(body)
                    .exchangeToMono(response ->
                            response.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                                    .defaultIfEmpty(Map.of())
                                    .map(responseBody -> {
                                        int status = response.statusCode().value();
                                        if (response.statusCode().is2xxSuccessful()) {
                                            return CyclesResponse.success(status, responseBody);
                                        }
                                        String error = (String) responseBody.get("error");
                                        return CyclesResponse.httpError(status, error, responseBody);
                                    })
                    ).block();


        }catch (Exception ex){
            LOG.error("Failed to execute reservation create HTTP request",ex);
            return CyclesResponse.transportError(ex) ;
        }*/
        return executeHttpPostRequest ("/v1/reservations",null,body);
    }

    @Override
    public CyclesResponse<Map<String,Object>> commitReservation(String reservationId, Object body) {
        return executeHttpPostRequest ("/v1/reservations/{id}/commit",reservationId,body);
        /*webClient.post()
                .uri("/v1/reservations/{id}/commit", reservationId)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block();

         */
    }

    @Override
    public CyclesResponse<Map<String,Object>> releaseReservation(String reservationId, Object body) {
        return executeHttpPostRequest ("/v1/reservations/{id}/release",reservationId,body);
        /*webClient.post()
                .uri("/v1/reservations/{id}/release", reservationId)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block();*/
    }
    private CyclesResponse<Map<String,Object>> executeHttpPostRequest (String uri, String pathParam, Object body){
        LOG.info("Started executing HTTP request:  uri={},pathParam={}, body={}",uri,pathParam,body);
        try {
            return webClient.post()
                    .uri(uri,pathParam)
                    .bodyValue(body)
                    .exchangeToMono(response ->
                            response.bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                                    .defaultIfEmpty(Map.of())
                                    .map(responseBody -> {
                                        int status = response.statusCode().value();
                                        if (response.statusCode().is2xxSuccessful()) {
                                            return CyclesResponse.success(status, responseBody);
                                        }
                                        String error = (String) responseBody.get("error");
                                        return CyclesResponse.httpError(status, error, responseBody);
                                    })
                    ).block();

        }catch (Exception ex){
            LOG.error("Failed to execute HTTP request: uri={}, pathParam={}, body={}",uri,pathParam,body,ex);
            return CyclesResponse.transportError(ex) ;
        }
        finally {
            LOG.info("Finished execution of HTTP request: uri={},pathParam={}, body={}",uri,pathParam,body);
        }

    }
    public record CreateReservationResponse(String reservation_id) {}
}
