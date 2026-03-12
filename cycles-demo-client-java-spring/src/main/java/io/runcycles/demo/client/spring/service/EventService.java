package io.runcycles.demo.client.spring.service;

import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.config.CyclesProperties;
import io.runcycles.client.java.spring.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Demonstrates standalone event creation via CyclesClient.createEvent().
 *
 * Events are "direct debit" operations — they record actual usage without
 * going through the reservation lifecycle (no reserve/commit/release).
 * Useful for after-the-fact accounting or lightweight usage tracking.
 */
@Service
public class EventService {

    private static final Logger LOG = LoggerFactory.getLogger(EventService.class);

    @Autowired
    private CyclesClient cyclesClient;

    @Autowired
    private CyclesProperties cyclesProperties;

    /**
     * Records a standalone usage event (direct debit, no reservation needed).
     */
    public Map<String, Object> recordUsageEvent(long amount, String description) {
        LOG.info("Recording usage event: amount={}, description={}", amount, description);

        Subject subject = Subject.builder()
                .tenant(cyclesProperties.getTenant())
                .workspace(cyclesProperties.getWorkspace())
                .app(cyclesProperties.getApp())
                .build();

        Action action = new Action("api.call", "external-service", null);

        CyclesMetrics metrics = new CyclesMetrics();
        metrics.setLatencyMs(42);
        metrics.putCustom("description", description);
        metrics.putCustom("source", "event-demo");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("description", description);
        metadata.put("recorded_at", System.currentTimeMillis());

        EventCreateRequest request = EventCreateRequest.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .subject(subject)
                .action(action)
                .actual(new Amount(Unit.USD_MICROCENTS, amount))
                .metrics(metrics)
                .metadata(metadata)
                .build();

        CyclesResponse<Map<String, Object>> response = cyclesClient.createEvent(request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", response.is2xx());
        result.put("httpStatus", response.getStatus());
        if (response.is2xx()) {
            result.put("eventResponse", response.getBody());
        } else {
            result.put("error", response.getErrorMessage());
        }
        return result;
    }
}
