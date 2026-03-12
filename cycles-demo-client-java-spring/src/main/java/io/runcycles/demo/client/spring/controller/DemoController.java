package io.runcycles.demo.client.spring.controller;

import io.runcycles.demo.client.spring.service.AnnotationShowcaseService;
import io.runcycles.demo.client.spring.service.EventService;
import io.runcycles.demo.client.spring.service.ProgrammaticClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Central demo controller that exposes one endpoint per Cycles feature.
 * Each response includes a "scenario" field explaining what the endpoint demonstrates.
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private static final Logger LOG = LoggerFactory.getLogger(DemoController.class);

    @Autowired
    private AnnotationShowcaseService annotationShowcaseService;

    @Autowired
    private ProgrammaticClientService programmaticClientService;

    @Autowired
    private EventService eventService;

    // ---- Annotation Showcase Endpoints ----

    @PostMapping("/annotation/minimal")
    public ResponseEntity<Map<String, Object>> annotationMinimal(
            @RequestParam(defaultValue = "hello world") String input) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", "Simplest possible @Cycles — fixed estimate, all defaults");
        response.put("annotationConfig", Map.of("value", "1000"));
        response.put("result", annotationShowcaseService.minimal(input));
        response.put("note", "Reserves 1000 USD_MICROCENTS, runs the method, commits the same amount");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/annotation/caps")
    public ResponseEntity<Map<String, Object>> annotationCaps(
            @RequestParam(defaultValue = "500") int maxTokens) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", "@Cycles with ALLOW_WITH_CAPS — reading and respecting server-imposed constraints");
        response.put("annotationConfig", Map.of(
                "value", "#maxTokens * 10",
                "actionKind", "llm.completion",
                "actionName", "gpt-4o"));
        response.put("result", annotationShowcaseService.capsAwareGeneration(maxTokens));
        response.put("note", "If the server returns caps (maxTokens, tool restrictions), the method adjusts its behavior");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/annotation/tokens")
    public ResponseEntity<Map<String, Object>> annotationTokens(
            @RequestParam(defaultValue = "500") int tokenCount) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", "@Cycles with unit=TOKENS and actionTags");
        response.put("annotationConfig", Map.of(
                "estimate", "#tokenCount * 2",
                "unit", "TOKENS",
                "actionKind", "llm.embedding",
                "actionTags", List.of("embedding", "search")));
        response.put("result", annotationShowcaseService.processWithTokens(tokenCount));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/annotation/credits")
    public ResponseEntity<Map<String, Object>> annotationCredits(
            @RequestParam(defaultValue = "100") int creditAmount) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", "@Cycles with unit=CREDITS, workflow/agent subject fields, and custom dimensions");
        response.put("annotationConfig", Map.of(
                "estimate", "#creditAmount",
                "unit", "CREDITS",
                "workflow", "data-pipeline",
                "agent", "etl-agent",
                "dimensions", List.of("cost_center=engineering", "team=platform")));
        response.put("result", annotationShowcaseService.processWithCredits(creditAmount));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/annotation/overdraft")
    public ResponseEntity<Map<String, Object>> annotationOverdraft(
            @RequestParam(defaultValue = "1000") int amount) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", "@Cycles with overagePolicy=ALLOW_WITH_OVERDRAFT");
        response.put("annotationConfig", Map.of(
                "estimate", "#amount",
                "actual", "#result.length() * 3",
                "overagePolicy", "ALLOW_WITH_OVERDRAFT"));
        response.put("result", annotationShowcaseService.processWithOverdraft(amount));
        response.put("note", "If budget is insufficient, the server allows the operation and records overdraft debt");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/annotation/custom-ttl")
    public ResponseEntity<Map<String, Object>> annotationCustomTtl(
            @RequestParam(defaultValue = "200") int amount) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", "@Cycles with custom ttlMs=120000 and gracePeriodMs=10000");
        response.put("annotationConfig", Map.of(
                "estimate", "#amount * 5",
                "ttlMs", 120000,
                "gracePeriodMs", 10000));
        response.put("result", annotationShowcaseService.processWithCustomTtl(amount));
        response.put("note", "Heartbeat extends reservation automatically at ttlMs/2 intervals");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/annotation/dry-run")
    public ResponseEntity<Map<String, Object>> annotationDryRun(
            @RequestParam(defaultValue = "500") int amount) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", "@Cycles with dryRun=true (shadow-mode evaluation)");
        response.put("annotationConfig", Map.of(
                "estimate", "#amount",
                "dryRun", true));
        Object dryRunResult = annotationShowcaseService.dryRunEvaluation(amount);
        response.put("result", dryRunResult);
        response.put("note", "Method body does NOT execute. Server evaluates without persisting or locking budget.");
        return ResponseEntity.ok(response);
    }

    // ---- Programmatic Client Endpoints ----

    @PostMapping("/client/reserve-commit")
    public ResponseEntity<Map<String, Object>> clientReserveCommit(
            @RequestParam(defaultValue = "5000") long estimate) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", "Programmatic CyclesClient: full reserve → execute → commit lifecycle");
        response.put("result", programmaticClientService.manualReservationLifecycle(estimate));
        response.put("note", "Uses CyclesClient directly — no @Cycles annotation or AOP involved");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/client/reserve-release")
    public ResponseEntity<Map<String, Object>> clientReserveRelease(
            @RequestParam(defaultValue = "3000") long estimate) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", "Programmatic CyclesClient: reserve → release (cancellation path)");
        response.put("result", programmaticClientService.manualReservationWithRelease(estimate));
        response.put("note", "Release returns budget to the pool when work is cancelled or fails");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/client/decide")
    public ResponseEntity<Map<String, Object>> clientDecide(
            @RequestParam(defaultValue = "10000") long estimate) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", "Programmatic CyclesClient: preflight decision (no reservation created)");
        response.put("result", programmaticClientService.preflightDecision(estimate));
        response.put("note", "Checks 'can I afford this?' without creating a reservation");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/client/balances")
    public ResponseEntity<Map<String, Object>> clientBalances() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", "Programmatic CyclesClient: query current balances");
        response.put("result", programmaticClientService.queryBalances());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/client/reservations")
    public ResponseEntity<Map<String, Object>> clientReservations() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", "Programmatic CyclesClient: list reservations");
        response.put("result", programmaticClientService.listReservations());
        return ResponseEntity.ok(response);
    }

    // ---- Event Endpoints ----

    @PostMapping("/events/record")
    public ResponseEntity<Map<String, Object>> eventsRecord(
            @RequestParam(defaultValue = "1500") long amount,
            @RequestParam(defaultValue = "External API call") String description) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("scenario", "Standalone event (direct debit without reservation lifecycle)");
        response.put("result", eventService.recordUsageEvent(amount, description));
        response.put("note", "Events record actual usage directly — no reserve/commit needed");
        return ResponseEntity.ok(response);
    }

    // ---- Index ----

    @GetMapping("/index")
    public ResponseEntity<Map<String, Object>> index() {
        String base = "http://localhost:7955";
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("application", "Cycles Spring Boot Starter Demo");
        response.put("description", "Each endpoint demonstrates a specific Cycles feature. " +
                "Copy any curl command below to try it.");

        List<Map<String, String>> endpoints = new ArrayList<>();

        // Start here
        endpoints.add(endpointInfo("POST", "/api/demo/annotation/minimal?input=hello",
                "Start here — simplest @Cycles(\"1000\") with all defaults",
                "curl -X POST " + base + "/api/demo/annotation/minimal?input=hello"));

        // Annotation features
        endpoints.add(endpointInfo("POST", "/api/demo/annotation/caps?maxTokens=500",
                "@Cycles with ALLOW_WITH_CAPS — reading and respecting server constraints",
                "curl -X POST " + base + "/api/demo/annotation/caps?maxTokens=500"));
        endpoints.add(endpointInfo("POST", "/api/llm/generate?prompt=hello&tokens=100",
                "@Cycles with CyclesContextHolder, metrics, and commitMetadata",
                "curl -X POST '" + base + "/api/llm/generate?prompt=hello&tokens=100'"));
        endpoints.add(endpointInfo("POST", "/api/demo/annotation/tokens?tokenCount=500",
                "@Cycles with unit=TOKENS and actionTags",
                "curl -X POST " + base + "/api/demo/annotation/tokens?tokenCount=500"));
        endpoints.add(endpointInfo("POST", "/api/demo/annotation/credits?creditAmount=100",
                "@Cycles with unit=CREDITS, workflow/agent, dimensions",
                "curl -X POST " + base + "/api/demo/annotation/credits?creditAmount=100"));
        endpoints.add(endpointInfo("POST", "/api/demo/annotation/overdraft?amount=1000",
                "@Cycles with overagePolicy=ALLOW_WITH_OVERDRAFT",
                "curl -X POST " + base + "/api/demo/annotation/overdraft?amount=1000"));
        endpoints.add(endpointInfo("POST", "/api/demo/annotation/custom-ttl?amount=200",
                "@Cycles with custom ttlMs and gracePeriodMs",
                "curl -X POST " + base + "/api/demo/annotation/custom-ttl?amount=200"));
        endpoints.add(endpointInfo("POST", "/api/demo/annotation/dry-run?amount=500",
                "@Cycles with dryRun=true (shadow-mode)",
                "curl -X POST " + base + "/api/demo/annotation/dry-run?amount=500"));

        // Programmatic client
        endpoints.add(endpointInfo("POST", "/api/demo/client/reserve-commit?estimate=5000",
                "Programmatic: full reserve → commit lifecycle",
                "curl -X POST " + base + "/api/demo/client/reserve-commit?estimate=5000"));
        endpoints.add(endpointInfo("POST", "/api/demo/client/reserve-release?estimate=3000",
                "Programmatic: reserve → release (cancellation)",
                "curl -X POST " + base + "/api/demo/client/reserve-release?estimate=3000"));
        endpoints.add(endpointInfo("POST", "/api/demo/client/decide?estimate=10000",
                "Programmatic: preflight decision check",
                "curl -X POST " + base + "/api/demo/client/decide?estimate=10000"));
        endpoints.add(endpointInfo("GET", "/api/demo/client/balances",
                "Programmatic: query current balances",
                "curl " + base + "/api/demo/client/balances"));
        endpoints.add(endpointInfo("GET", "/api/demo/client/reservations",
                "Programmatic: list reservations",
                "curl " + base + "/api/demo/client/reservations"));

        // Events
        endpoints.add(endpointInfo("POST", "/api/demo/events/record?amount=1500&description=API+call",
                "Standalone event (direct debit, no reservation)",
                "curl -X POST '" + base + "/api/demo/events/record?amount=1500&description=API+call'"));

        response.put("endpoints", endpoints);
        return ResponseEntity.ok(response);
    }

    private Map<String, String> endpointInfo(String method, String path, String description, String curl) {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("method", method);
        info.put("path", path);
        info.put("description", description);
        info.put("curl", curl);
        return info;
    }
}
