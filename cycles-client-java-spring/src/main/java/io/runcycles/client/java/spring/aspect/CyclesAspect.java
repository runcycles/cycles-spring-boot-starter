package io.runcycles.client.java.spring.aspect;

import io.runcycles.client.java.spring.annotation.Cycles;
import io.runcycles.client.java.spring.context.CyclesContextHolder;
import io.runcycles.client.java.spring.context.CyclesLifecycleService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Thin AOP adapter that bridges {@code @Cycles}-annotated methods
 * to {@link CyclesLifecycleService} for the reserve/execute/commit lifecycle.
 */
@Aspect
public class CyclesAspect {

    private static final Logger LOG = LoggerFactory.getLogger(CyclesAspect.class);

    private final CyclesLifecycleService lifecycleService;

    /**
     * Creates a new aspect backed by the given lifecycle service.
     *
     * @param lifecycleService the service orchestrating reserve/execute/commit
     */
    public CyclesAspect(CyclesLifecycleService lifecycleService) {
        this.lifecycleService = lifecycleService;
    }

    /**
     * Intercepts methods annotated with {@code @Cycles} and wraps them in the
     * reserve/execute/commit lifecycle.
     *
     * @param pjp    the join point representing the intercepted method
     * @param cycles the annotation instance providing budget parameters
     * @return the return value of the guarded method
     * @throws Throwable if the guarded method or lifecycle operations fail
     */
    @Around("@annotation(cycles)")
    public Object around(ProceedingJoinPoint pjp, Cycles cycles) throws Throwable {
        if (CyclesContextHolder.get() != null) {
            throw new IllegalStateException("Nested @Cycles not supported");
        }

        Method method = ((MethodSignature) pjp.getSignature()).getMethod();

        String actionKind = cycles.actionKind().isBlank()
                ? pjp.getTarget().getClass().getSimpleName()
                : cycles.actionKind();
        String actionName = cycles.actionName().isBlank()
                ? method.getName()
                : cycles.actionName();

        LOG.debug("@Cycles intercepted: actionKind={}, actionName={}", actionKind, actionName);

        return lifecycleService.executeWithReservation(
                pjp::proceed,
                cycles,
                method,
                pjp.getArgs(),
                pjp.getTarget(),
                actionKind,
                actionName
        );
    }
}
