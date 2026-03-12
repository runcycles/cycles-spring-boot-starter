package io.runcycles.demo.client.spring.service;

import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.config.CyclesProperties;
import io.runcycles.client.java.spring.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Demonstrates direct, programmatic usage of the CyclesClient interface.
 *
 * Unlike the @Cycles annotation (which handles the lifecycle automatically via AOP),
 * this service calls CyclesClient methods directly for full manual control over
 * the reserve/execute/commit lifecycle.
 *
 * Note: CyclesContextHolder is NOT available here — it is only populated by the
 * CyclesAspect for @Cycles-annotated methods.
 */
@Service
public class ProgrammaticClientService {

    private static final Logger LOG = LoggerFactory.getLogger(ProgrammaticClientService.class);

    @Autowired
    private CyclesClient cyclesClient;

    @Autowired
    private CyclesProperties cyclesProperties;

    /**
     * Full manual reserve → execute → commit lifecycle.
     * Returns a structured map showing each step's response.
     */
    public Map<String, Object> manualReservationLifecycle(long estimateAmount) {
        LOG.info("Starting manual reservation lifecycle: estimate={}", estimateAmount);
        Map<String, Object> result = new LinkedHashMap<>();

        // Step 1: Build and create the reservation
        Subject subject = Subject.builder()
                .tenant(cyclesProperties.getTenant())
                .workspace(cyclesProperties.getWorkspace())
                .app(cyclesProperties.getApp())
                .build();

        Action action = new Action("llm.completion", "manual-lifecycle-demo", null);

        ReservationCreateRequest createRequest = ReservationCreateRequest.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .subject(subject)
                .action(action)
                .estimate(new Amount(Unit.USD_MICROCENTS, estimateAmount))
                .ttlMs(60000L)
                .build();

        CyclesResponse<Map<String, Object>> createResponse = cyclesClient.createReservation(createRequest);
        result.put("step1_createReservation", formatResponse(createResponse));

        if (!createResponse.is2xx()) {
            result.put("outcome", "Failed at reservation creation");
            return result;
        }

        // Extract reservation ID from the response
        String reservationId = createResponse.getBodyAttributeAsString("reservation_id");
        result.put("reservationId", reservationId);

        // Step 2: Simulate work
        String workResult = "Simulated LLM completion for manual lifecycle demo";
        result.put("step2_workExecuted", workResult);

        // Step 3: Commit with actual usage, metrics, and metadata
        long actualAmount = estimateAmount - 200; // Simulate actual < estimate

        CyclesMetrics metrics = new CyclesMetrics();
        metrics.setTokensInput(100);
        metrics.setTokensOutput(250);
        metrics.setLatencyMs(150);
        metrics.setModelVersion("gpt-4-0613");

        Map<String, Object> commitMetadata = new LinkedHashMap<>();
        commitMetadata.put("demo", "programmatic-client");
        commitMetadata.put("timestamp", System.currentTimeMillis());

        CommitRequest commitRequest = CommitRequest.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .actual(new Amount(Unit.USD_MICROCENTS, actualAmount))
                .metrics(metrics)
                .metadata(commitMetadata)
                .build();

        CyclesResponse<Map<String, Object>> commitResponse =
                cyclesClient.commitReservation(reservationId, commitRequest);
        result.put("step3_commitReservation", formatResponse(commitResponse));
        result.put("outcome", commitResponse.is2xx() ? "Success — full lifecycle completed" : "Failed at commit");

        return result;
    }

    /**
     * Reserve → release (cancellation path).
     * Demonstrates returning budget when work is cancelled or fails.
     */
    public Map<String, Object> manualReservationWithRelease(long estimateAmount) {
        LOG.info("Starting reservation with release: estimate={}", estimateAmount);
        Map<String, Object> result = new LinkedHashMap<>();

        Subject subject = Subject.builder()
                .tenant(cyclesProperties.getTenant())
                .workspace(cyclesProperties.getWorkspace())
                .app(cyclesProperties.getApp())
                .build();

        Action action = new Action("llm.completion", "release-demo", null);

        ReservationCreateRequest createRequest = ReservationCreateRequest.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .subject(subject)
                .action(action)
                .estimate(new Amount(Unit.USD_MICROCENTS, estimateAmount))
                .build();

        CyclesResponse<Map<String, Object>> createResponse = cyclesClient.createReservation(createRequest);
        result.put("step1_createReservation", formatResponse(createResponse));

        if (!createResponse.is2xx()) {
            result.put("outcome", "Failed at reservation creation");
            return result;
        }

        String reservationId = createResponse.getBodyAttributeAsString("reservation_id");
        result.put("reservationId", reservationId);

        // Release the reservation (return budget to the pool)
        ReleaseRequest releaseRequest = ReleaseRequest.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .reason("Work cancelled — demonstrating release path")
                .build();

        CyclesResponse<Map<String, Object>> releaseResponse =
                cyclesClient.releaseReservation(reservationId, releaseRequest);
        result.put("step2_releaseReservation", formatResponse(releaseResponse));
        result.put("outcome", releaseResponse.is2xx()
                ? "Success — budget returned to pool via release"
                : "Failed at release");

        return result;
    }

    /**
     * Preflight decision check without creating a reservation.
     * Useful for "can I afford this?" checks before starting work.
     */
    public Map<String, Object> preflightDecision(long estimateAmount) {
        LOG.info("Performing preflight decision: estimate={}", estimateAmount);

        Subject subject = Subject.builder()
                .tenant(cyclesProperties.getTenant())
                .workspace(cyclesProperties.getWorkspace())
                .app(cyclesProperties.getApp())
                .build();

        Action action = new Action("llm.completion", "preflight-check", null);

        DecisionRequest request = DecisionRequest.builder()
                .idempotencyKey(UUID.randomUUID().toString())
                .subject(subject)
                .action(action)
                .estimate(new Amount(Unit.USD_MICROCENTS, estimateAmount))
                .build();

        CyclesResponse<Map<String, Object>> response = cyclesClient.decide(request);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("response", formatResponse(response));
        return result;
    }

    /**
     * Queries current balance information for the configured scope.
     */
    public Map<String, Object> queryBalances() {
        LOG.info("Querying balances");

        Map<String, String> params = new LinkedHashMap<>();
        if (cyclesProperties.getTenant() != null) params.put("tenant", cyclesProperties.getTenant());
        if (cyclesProperties.getWorkspace() != null) params.put("workspace", cyclesProperties.getWorkspace());
        if (cyclesProperties.getApp() != null) params.put("app", cyclesProperties.getApp());

        CyclesResponse<Map<String, Object>> response = cyclesClient.getBalances(params);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queryParams", params);
        result.put("response", formatResponse(response));
        return result;
    }

    /**
     * Lists active reservations.
     */
    public Map<String, Object> listReservations() {
        LOG.info("Listing reservations");

        Map<String, String> params = new LinkedHashMap<>();
        if (cyclesProperties.getTenant() != null) params.put("tenant", cyclesProperties.getTenant());

        CyclesResponse<Map<String, Object>> response = cyclesClient.listReservations(params);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("queryParams", params);
        result.put("response", formatResponse(response));
        return result;
    }

    private Map<String, Object> formatResponse(CyclesResponse<Map<String, Object>> response) {
        Map<String, Object> formatted = new LinkedHashMap<>();
        formatted.put("success", response.is2xx());
        formatted.put("httpStatus", response.getStatus());
        if (response.is2xx()) {
            formatted.put("body", response.getBody());
        } else if (response.isTransportError()) {
            formatted.put("transportError", response.getErrorMessage());
        } else {
            formatted.put("error", response.getErrorMessage());
        }
        return formatted;
    }
}
