package io.runcycles.demo.client.spring.service;


import io.runcycles.client.java.spring.annotation.Cycles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LlmService {
    private static final Logger LOG = LoggerFactory.getLogger(LlmService.class);

    @Cycles(
            tenant = "ecosystem-saulius-1",
            workspace = "development",
            app = "scalerx",
            estimateExpression = "#p1 * 10",
            actualExpression = "#result.length() * 5",
            useEstimatedIfActualNotProvided = false,
            actionKind = "llm.completion",
            actionName = "gpt-4",
            overagePolicy = "ALLOW_WITH_OVERDRAFT",
            unit = "USD_MICROCENTS"
    )
    public String generateText(String prompt, int tokens) {
        LOG.info("Calling LLM for text generation: prompt={}, tokens={}",prompt,tokens) ;

        // Simulate LLM response
        String result = "Generated response for: " + prompt;

        // Business logic executed normally

        LOG.info("Response from LLM services: result={}",result);
        return result;
    }
}