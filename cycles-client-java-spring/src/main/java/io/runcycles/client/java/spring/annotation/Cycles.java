package io.runcycles.client.java.spring.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cycles {

    String tenant() default "";
    String workspace() default "";
    String app() default "";
    String workflow() default "";
    String agent () default "";
    String toolset () default "";

    String actionKind();
    String actionName();
    String[] actionTags() default {};

    String estimateExpression();
    String actualExpression() default "";

    boolean useEstimatedIfActualNotProvided() default false;

    String unit() default "USD_MICROCENTS";
    long ttlMs() default 60000;
    long gracePeriodMs() default -1;
    String overagePolicy() default "REJECT";

}
