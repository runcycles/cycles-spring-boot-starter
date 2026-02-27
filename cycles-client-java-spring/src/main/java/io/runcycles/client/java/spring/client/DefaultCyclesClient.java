package io.runcycles.client.java.spring.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.reactive.function.client.WebClient;

public class DefaultCyclesClient implements CyclesClient {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultCyclesClient.class);

    private final WebClient webClient;

    public DefaultCyclesClient(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public String createReservation(Object body) {
        LOG.info("Executing reservation create via cycles remote service: requestBody={}",body);
        return webClient.post()
                .uri("/v1/reservations")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(CreateReservationResponse.class)
                .block()
                .reservation_id();
    }

    @Override
    public void commitReservation(String reservationId, Object body) {
        webClient.post()
                .uri("/v1/reservations/{id}/commit", reservationId)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    @Override
    public void releaseReservation(String reservationId, Object body) {
        webClient.post()
                .uri("/v1/reservations/{id}/release", reservationId)
                .bodyValue(body)
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    public record CreateReservationResponse(String reservation_id) {}
}
