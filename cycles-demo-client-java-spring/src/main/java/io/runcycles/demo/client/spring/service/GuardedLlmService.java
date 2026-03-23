package io.runcycles.demo.client.spring.service;

import io.runcycles.client.java.spring.annotation.Cycles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Extracted service containing the @Cycles-annotated method.
 *
 * <p>This demonstrates the recommended workaround for the Spring AOP
 * self-invocation limitation: move the @Cycles-annotated method into its
 * own bean so calls go through the Spring proxy.
 *
 * @see SelfInvocationDemoService
 */
@Service
public class GuardedLlmService {

    private static final Logger LOG = LoggerFactory.getLogger(GuardedLlmService.class);

    @Cycles(value = "#input.length() * 10",
            actionKind = "llm.completion",
            actionName = "self-invocation-demo")
    public String guardedCall(String input) {
        LOG.info("guardedCall executing with budget enforcement — input={}", input);
        return "LLM response for: " + input;
    }
}
