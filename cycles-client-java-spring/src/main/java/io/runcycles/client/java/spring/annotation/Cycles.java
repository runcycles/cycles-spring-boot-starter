package io.runcycles.client.java.spring.annotation;

import java.lang.annotation.*;

/**
 * Declares a budget-guarded method using the Cycles reserve/execute/commit lifecycle.
 *
 * <p>When placed on a Spring bean method, the {@link io.runcycles.client.java.spring.aspect.CyclesAspect
 * CyclesAspect} will:
 * <ol>
 *   <li>Evaluate the {@linkplain #estimate() estimate} SpEL expression</li>
 *   <li>Create a reservation against the Cycles API</li>
 *   <li>Execute the annotated method if the reservation is approved</li>
 *   <li>Commit the actual usage amount on success, or release the reservation on failure</li>
 * </ol>
 *
 * <h2>Minimal usage</h2>
 * <pre>{@code
 * @Cycles("1000")
 * public String callLlm(String prompt) { ... }
 * }</pre>
 *
 * <h2>With actual cost from return value</h2>
 * <pre>{@code
 * @Cycles(estimate = "#tokens * 10", actual = "#result.totalCost")
 * public LlmResponse callLlm(int tokens) { ... }
 * }</pre>
 *
 * @see io.runcycles.client.java.spring.context.CyclesContextHolder
 * @see io.runcycles.client.java.spring.context.CyclesLifecycleService
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cycles {

    /**
     * SpEL expression for estimated cost.
     * Enables shorthand: {@code @Cycles("#tokens * 10")}
     * <p>
     * Synonym for {@link #estimate()}. Use one or the other, not both.
     *
     * @return the estimate SpEL expression, or empty string if unset
     */
    String value() default "";

    /**
     * SpEL expression for estimated cost (spec-aligned name).
     * <p>
     * Synonym for {@link #value()}. Use one or the other, not both.
     * Example: {@code @Cycles(estimate = "#tokens * 10", actual = "#result.cost")}
     *
     * @return the estimate SpEL expression, or empty string if unset
     */
    String estimate() default "";

    /**
     * Subject field: the tenant identifier. Falls back to config or resolver if blank.
     *
     * @return the tenant identifier, or empty string if unset
     */
    String tenant() default "";
    /**
     * Subject field: the workspace identifier. Falls back to config or resolver if blank.
     *
     * @return the workspace identifier, or empty string if unset
     */
    String workspace() default "";
    /**
     * Subject field: the application identifier. Falls back to config or resolver if blank.
     *
     * @return the application identifier, or empty string if unset
     */
    String app() default "";
    /**
     * Subject field: the workflow identifier. Falls back to config or resolver if blank.
     *
     * @return the workflow identifier, or empty string if unset
     */
    String workflow() default "";
    /**
     * Subject field: the agent identifier. Falls back to config or resolver if blank.
     *
     * @return the agent identifier, or empty string if unset
     */
    String agent() default "";
    /**
     * Subject field: the toolset identifier. Falls back to config or resolver if blank.
     *
     * @return the toolset identifier, or empty string if unset
     */
    String toolset() default "";

    /**
     * Action category. Defaults to declaring class simple name if blank.
     *
     * @return the action kind, or empty string if unset
     */
    String actionKind() default "";
    /**
     * Action identifier. Defaults to method name if blank.
     *
     * @return the action name, or empty string if unset
     */
    String actionName() default "";
    /**
     * Optional tags to attach to the action for filtering and reporting.
     *
     * @return the action tags array
     */
    String[] actionTags() default {};

    /**
     * SpEL expression for actual cost (evaluated after method returns).
     *
     * @return the actual-cost SpEL expression, or empty string if unset
     */
    String actual() default "";

    /**
     * When true and {@code actual} is blank, use the estimate as actual.
     * Defaults to true so that the minimal {@code @Cycles("1000")} works.
     *
     * @return whether to fall back to the estimate for actual cost
     */
    boolean useEstimateIfActualNotProvided() default true;

    /**
     * The unit of measure for amounts. One of: USD_MICROCENTS, TOKENS, CREDITS, RISK_POINTS.
     *
     * @return the unit name
     */
    String unit() default "USD_MICROCENTS";
    /**
     * Reservation time-to-live in milliseconds (1,000 to 86,400,000).
     *
     * @return the TTL in milliseconds
     */
    long ttlMs() default 60000;
    /**
     * Grace period after TTL expiry in milliseconds (0 to 60,000). Use -1 to omit.
     *
     * @return the grace period in milliseconds, or -1 to omit
     */
    long gracePeriodMs() default -1;
    /**
     * Overage policy: REJECT, ALLOW_IF_AVAILABLE, or ALLOW_WITH_OVERDRAFT.
     *
     * @return the overage policy name
     */
    String overagePolicy() default "REJECT";

    /**
     * Shadow-mode evaluation. If true, the server evaluates the reservation
     * without persisting it or locking budget. The guarded method will NOT execute.
     *
     * @return whether dry-run mode is enabled
     */
    boolean dryRun() default false;

    /**
     * Optional custom dimensions for the Subject, as "key=value" pairs.
     * Example: {@code dimensions = {"cost_center=engineering", "project=alpha"}}
     *
     * @return the dimensions array
     */
    String[] dimensions() default {};

}
