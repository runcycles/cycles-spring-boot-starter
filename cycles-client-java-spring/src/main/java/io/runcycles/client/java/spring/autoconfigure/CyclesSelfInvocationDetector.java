package io.runcycles.client.java.spring.autoconfigure;

import io.runcycles.client.java.spring.annotation.Cycles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Detects beans where only some methods are annotated with {@code @Cycles},
 * which is a pattern susceptible to the Spring AOP self-invocation pitfall.
 *
 * <p>When a bean has both annotated and non-annotated public methods, there is
 * a risk that a non-annotated method calls an annotated method internally
 * ({@code this.method()}), bypassing the proxy. This detector logs a warning
 * at startup to alert developers.
 */
public class CyclesSelfInvocationDetector implements BeanPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(CyclesSelfInvocationDetector.class);

    /** Creates a new detector instance. */
    public CyclesSelfInvocationDetector() {}

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> targetClass;
        try {
            targetClass = AopProxyUtils.ultimateTargetClass(bean);
        } catch (Exception e) {
            // If we can't resolve the target class, skip silently
            return bean;
        }

        // Skip Spring framework classes
        String className = targetClass.getName();
        if (className.startsWith("org.springframework.")) {
            return bean;
        }

        List<String> annotatedMethods = new ArrayList<>();
        int publicMethodCount = 0;

        for (Method method : targetClass.getDeclaredMethods()) {
            if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                publicMethodCount++;
                if (method.isAnnotationPresent(Cycles.class)) {
                    annotatedMethods.add(method.getName());
                }
            }
        }

        if (!annotatedMethods.isEmpty() && publicMethodCount > annotatedMethods.size()) {
            LOG.warn("Bean '{}' ({}) has @Cycles on {} of {} public methods {}. " +
                            "Internal calls between methods in this class will bypass the Spring proxy " +
                            "and @Cycles will not activate. Consider extracting @Cycles methods into a " +
                            "separate bean. See: https://runcycles.io/quickstart/getting-started-with-the-cycles-spring-boot-starter#self-invocation",
                    beanName, targetClass.getSimpleName(),
                    annotatedMethods.size(), publicMethodCount, annotatedMethods);
        }

        return bean;
    }
}
