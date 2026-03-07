package io.runcycles.client.java.spring.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cycles {

    /**
     * SpEL expression for estimated cost.
     * Enables shorthand: {@code @Cycles("#tokens * 10")}
     * <p>
     * Synonym for {@link #estimate()}. Use one or the other, not both.
     */
    String value() default "";

    /**
     * SpEL expression for estimated cost (spec-aligned name).
     * <p>
     * Synonym for {@link #value()}. Use one or the other, not both.
     * Example: {@code @Cycles(estimate = "#tokens * 10", actual = "#result.cost")}
     */
    String estimate() default "";

    String tenant() default "";
    String workspace() default "";
    String app() default "";
    String workflow() default "";
    String agent() default "";
    String toolset() default "";

    /** Action category. Defaults to declaring class simple name if blank. */
    String actionKind() default "";
    /** Action identifier. Defaults to method name if blank. */
    String actionName() default "";
    String[] actionTags() default {};

    /** SpEL expression for actual cost (evaluated after method returns). */
    String actual() default "";

    /**
     * When true and {@code actual} is blank, use the estimate as actual.
     * Defaults to true so that the minimal {@code @Cycles("1000")} works.
     */
    boolean useEstimateIfActualNotProvided() default true;

    String unit() default "USD_MICROCENTS";
    long ttlMs() default 60000;
    long gracePeriodMs() default -1;
    String overagePolicy() default "REJECT";

    /**
     * Shadow-mode evaluation. If true, the server evaluates the reservation
     * without persisting it or locking budget. The guarded method will NOT execute.
     */
    boolean dryRun() default false;

    /**
     * Optional custom dimensions for the Subject, as "key=value" pairs.
     * Example: {@code dimensions = {"cost_center=engineering", "project=alpha"}}
     */
    String[] dimensions() default {};

}
