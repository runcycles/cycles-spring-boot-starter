package io.runcycles.client.java.spring.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;

/**
 * Evaluates SpEL expressions used in {@code @Cycles} annotations to compute
 * estimated and actual usage amounts.
 *
 * <p>The evaluation context exposes:
 * <ul>
 *   <li>All method parameter names as SpEL variables</li>
 *   <li>{@code #result} — the method return value (only available for {@code actual} expressions)</li>
 *   <li>{@code #args} — the raw method arguments array</li>
 *   <li>{@code #target} — the target bean instance</li>
 * </ul>
 *
 * <p>Expressions must evaluate to a non-null, non-negative {@link Number}.
 */
public class CyclesExpressionEvaluator {
    private static final Logger LOG = LoggerFactory.getLogger(CyclesExpressionEvaluator.class);
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    /**
     * Evaluates a SpEL expression to a non-negative {@code long} value.
     *
     * @param expression the SpEL expression string
     * @param method     the annotated method (used for parameter name discovery)
     * @param args       the method invocation arguments
     * @param result     the method return value, or {@code null} for pre-execution evaluation
     * @param target     the bean instance on which the method was invoked
     * @return the evaluated amount as a non-negative {@code long}
     * @throws IllegalArgumentException if the expression evaluates to null or a negative value
     */
    public long evaluate(String expression,
                         Method method,
                         Object[] args,
                         Object result,
                         Object target) {
        LOG.debug("Evaluating expression: expression={}, method={}, args={}, result={}, target={}", expression, method, args, result, target);
        Expression exp = parser.parseExpression(expression);

        MethodBasedEvaluationContext context =
                new MethodBasedEvaluationContext(
                        target,
                        method,
                        args,
                        discoverer
                );

        // Expose result explicitly
        context.setVariable("result", result);

        // Optional convenience variables
        context.setVariable("args", args);
        context.setVariable("target", target);

        Number value = exp.getValue(context, Number.class);

        if (value == null) {
            throw new IllegalArgumentException("Expression evaluated to null: " + expression);
        }

        long longValue = value.longValue();

        if (longValue < 0) {
            throw new IllegalArgumentException(
                    "Charge amount must not be negative. Expression: "
                            + expression + " evaluated to: " + longValue
            );
        }

        return longValue;
    }
}
