package io.runcycles.demo.client.spring.service;

import io.runcycles.client.java.spring.annotation.Cycles;
import io.runcycles.client.java.spring.context.CyclesContextHolder;
import io.runcycles.client.java.spring.context.CyclesReservationContext;
import io.runcycles.client.java.spring.model.CyclesMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class LlmService {
    private static final Logger LOG = LoggerFactory.getLogger(LlmService.class);

    /**
     * Budget-guarded LLM text generation.
     *
     * Demonstrates:
     * - @Cycles annotation with SpEL estimate (#p1 * 10) and actual (#result.length() * 5)
     * - CyclesContextHolder.get() to access the active reservation context
     * - CyclesMetrics to report token counts, latency, and custom metrics on commit
     * - commitMetadata to attach audit/debugging data to the commit
     */
    @Cycles(value = "#p1 * 10",
            actual = "#result.length() * 5",
            actionKind = "llm.completion",
            actionName = "gpt-4")
    public String generateText(String prompt, int tokens) {
        long startTime = System.currentTimeMillis();
        LOG.info("Calling LLM for text generation: prompt={}, tokens={}", prompt, tokens);

        // Access the active reservation context inside the guarded method
        CyclesReservationContext ctx = CyclesContextHolder.get();
        if (ctx != null) {
            LOG.info("Reservation context available: reservationId={}, decision={}, estimate={}",
                    ctx.getReservationId(), ctx.getDecision(), ctx.getEstimate());

            // Check if caps are present (ALLOW_WITH_CAPS decision)
            if (ctx.hasCaps()) {
                LOG.info("Budget caps present: {}", ctx.getCaps());
            }
        }

        // Simulate LLM response
        String result = "Generated response for: " + prompt;

        // Report metrics via the context — these are included in the commit request
        if (ctx != null) {
            CyclesMetrics metrics = new CyclesMetrics();
            metrics.setTokensInput(tokens);
            metrics.setTokensOutput(result.length());
            metrics.setLatencyMs((int) (System.currentTimeMillis() - startTime));
            metrics.setModelVersion("gpt-4-0613");
            metrics.putCustom("prompt_length", prompt.length());
            ctx.setMetrics(metrics);

            // Attach audit metadata to the commit
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("request_id", UUID.randomUUID().toString());
            metadata.put("prompt_hash", Integer.toHexString(prompt.hashCode()));
            ctx.setCommitMetadata(metadata);
        }

        LOG.info("Response from LLM services: result={}", result);
        return result;
    }
}
