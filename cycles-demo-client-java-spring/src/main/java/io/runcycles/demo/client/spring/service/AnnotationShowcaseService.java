package io.runcycles.demo.client.spring.service;

import io.runcycles.client.java.spring.annotation.Cycles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Demonstrates various @Cycles annotation configurations.
 * Each method showcases a different set of annotation attributes.
 */
@Service
public class AnnotationShowcaseService {

    private static final Logger LOG = LoggerFactory.getLogger(AnnotationShowcaseService.class);

    /**
     * Demonstrates: unit = TOKENS, actionTags for filtering.
     * The estimate is computed from the tokenCount parameter using SpEL.
     */
    @Cycles(estimate = "#tokenCount * 2",
            unit = "TOKENS",
            actionKind = "llm.embedding",
            actionName = "text-embedding-ada-002",
            actionTags = {"embedding", "search"})
    public String processWithTokens(int tokenCount) {
        LOG.info("Processing with TOKENS unit: tokenCount={}", tokenCount);
        return "Embedded " + tokenCount + " tokens for search indexing";
    }

    /**
     * Demonstrates: unit = CREDITS, workflow/agent subject fields, custom dimensions.
     * Shows how to scope budget to a specific workflow and agent within the subject hierarchy.
     */
    @Cycles(estimate = "#creditAmount",
            unit = "CREDITS",
            workflow = "data-pipeline",
            agent = "etl-agent",
            dimensions = {"cost_center=engineering", "team=platform"},
            actionKind = "compute.job",
            actionName = "batch-transform")
    public String processWithCredits(int creditAmount) {
        LOG.info("Processing with CREDITS unit: creditAmount={}", creditAmount);
        return "Batch transform completed using " + creditAmount + " credits";
    }

    /**
     * Demonstrates: overagePolicy = ALLOW_WITH_OVERDRAFT, separate actual expression.
     * When the budget is insufficient, the server allows the operation and records overdraft debt
     * instead of rejecting. The actual cost is computed from the result length.
     */
    @Cycles(estimate = "#amount",
            actual = "#result.length() * 3",
            overagePolicy = "ALLOW_WITH_OVERDRAFT",
            actionKind = "llm.completion",
            actionName = "gpt-4-turbo")
    public String processWithOverdraft(int amount) {
        LOG.info("Processing with ALLOW_WITH_OVERDRAFT policy: estimateAmount={}", amount);
        return "Generated completion with overdraft-tolerant budget policy";
    }

    /**
     * Demonstrates: custom ttlMs and gracePeriodMs for long-running operations.
     * TTL of 120 seconds (instead of default 60s) with a 10-second grace period.
     * The heartbeat mechanism will extend the reservation automatically at ttlMs/2 intervals.
     */
    @Cycles(estimate = "#amount * 5",
            ttlMs = 120000,
            gracePeriodMs = 10000,
            actionKind = "llm.long-running",
            actionName = "claude-3-opus")
    public String processWithCustomTtl(int amount) {
        LOG.info("Processing with custom TTL (120s) and grace period (10s): amount={}", amount);
        return "Long-running operation completed within extended TTL window";
    }

    /**
     * Demonstrates: dryRun = true (shadow-mode evaluation).
     * The server evaluates the reservation without persisting it or locking budget.
     * IMPORTANT: When dryRun is true, this method body will NOT execute.
     * The aspect returns a DryRunResult directly.
     */
    @Cycles(estimate = "#amount",
            dryRun = true,
            actionKind = "llm.completion",
            actionName = "cost-preview")
    public String dryRunEvaluation(int amount) {
        // This line will never execute when dryRun = true.
        // The aspect intercepts and returns the dry-run evaluation result instead.
        LOG.info("This should not appear in logs — dry run skips method execution");
        return "This value is never returned in dry-run mode";
    }
}
