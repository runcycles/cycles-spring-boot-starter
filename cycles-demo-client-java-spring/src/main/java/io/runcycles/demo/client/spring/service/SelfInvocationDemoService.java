package io.runcycles.demo.client.spring.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Demonstrates the correct way to call a @Cycles-annotated method from
 * another method, avoiding the Spring AOP self-invocation pitfall.
 *
 * <p><b>Problem:</b> Calling a @Cycles method internally within the same
 * class (e.g., {@code this.guardedCall()}) bypasses the Spring proxy and
 * the aspect never fires.
 *
 * <p><b>Solution:</b> Extract the @Cycles method into a separate bean
 * ({@link GuardedLlmService}) and inject it. The call now goes through
 * the proxy, so the aspect intercepts it.
 *
 * @see GuardedLlmService
 */
@Service
public class SelfInvocationDemoService {

    private static final Logger LOG = LoggerFactory.getLogger(SelfInvocationDemoService.class);

    @Autowired
    private GuardedLlmService guardedLlmService;

    /**
     * Calls the @Cycles-annotated method through an injected bean,
     * ensuring the AOP proxy intercepts the call.
     */
    public String handleRequest(String input) {
        LOG.info("handleRequest: delegating to GuardedLlmService (external bean call)");
        return guardedLlmService.guardedCall(input);
    }
}
