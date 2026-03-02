package io.runcycles.client.java.spring.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cycles {

    String tenant();
    String workspace() default "";
    String app() default "";

    String actionKind();
    String actionName();

    String estimateExpression();
    String actualExpression() default "";

    boolean useEstimatedIfActualNotProvided() default false;

    String unit() default "USD_MICROCENTS";
    long ttlMs() default 3600000;
    String overagePolicy() default "ALLOW_WITH_OVERDRAFT";
}
