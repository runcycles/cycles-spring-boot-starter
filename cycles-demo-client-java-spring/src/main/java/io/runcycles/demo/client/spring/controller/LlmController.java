package io.runcycles.demo.client.spring.controller;

import io.runcycles.client.java.spring.model.CyclesProtocolException;
import io.runcycles.demo.client.spring.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/llm")
public class LlmController {
    private static final Logger LOG = LoggerFactory.getLogger(LlmController.class);

    @Autowired
    private LlmService llmService;

    @PostMapping("/generate")
    public String generate(
            @RequestParam String prompt,
            @RequestParam int tokens) {
        return llmService.generateText(prompt, tokens);
    }
    @PostMapping ("/chat")
    public ResponseEntity<Map<String,Object>> handleChat(@RequestBody Map<String,Object> request) {
        LOG.info("Got request to chat: request={}",request);
        long t1 = System.currentTimeMillis() ;
        try {
            String prompt = (String) request.get("prompt");
            int tokens = ((Number) request.get("tokens")).intValue();
            String result = llmService.generateText(prompt, tokens);
            Map<String, Object> responseBody = new LinkedHashMap<>();
            responseBody.put("prompt", prompt);
            responseBody.put("response", result);
            long t2 = System.currentTimeMillis() ;
            LOG.info("Chat elapse time(ms): elapseTime={}ms",(t2-t1));
            return ResponseEntity.ok().body(responseBody);
        } catch (CyclesProtocolException e) {
            // Budget-related errors get specific handling
            LOG.warn("Cycles budget error during chat: errorCode={}, message={}",
                    e.getErrorCode(), e.getMessage());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", e.getMessage());
            result.put("errorCode", e.getErrorCode() != null ? e.getErrorCode().name() : null);
            result.put("budgetExceeded", e.isBudgetExceeded());
            return ResponseEntity.status(e.getHttpStatus() > 0 ? e.getHttpStatus() : 500).body(result);
        } catch (Exception e) {
            LOG.error("Failed to chat: request={}", request, e);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("application", "Cycles Protocol Demo Application");
        info.put("endpointGroups", Map.of(
                "/api/llm/*", "LLM endpoints with @Cycles annotation, CyclesContextHolder, and metrics",
                "/api/demo/annotation/*", "Annotation variations (units, TTL, overdraft, dry-run, dimensions)",
                "/api/demo/client/*", "Programmatic CyclesClient usage (reserve/commit/release/decide/balances)",
                "/api/demo/events/*", "Standalone events (direct debit without reservation)",
                "/api/demo/index", "Full endpoint listing with descriptions"));
        return ResponseEntity.ok(info);
    }
}