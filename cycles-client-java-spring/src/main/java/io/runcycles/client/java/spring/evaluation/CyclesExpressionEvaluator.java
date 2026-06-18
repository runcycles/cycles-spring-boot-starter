package io.runcycles.client.java.spring.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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

    /** Creates a new expression evaluator. */
    public CyclesExpressionEvaluator() {}

    private static final Logger LOG = LoggerFactory.getLogger(CyclesExpressionEvaluator.class);
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
    private final ConcurrentMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

    private Expression parseCached(String expression) {
        return expressionCache.computeIfAbsent(expression, parser::parseExpression);
    }

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
        Expression exp = parseCached(expression);

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

    /**
     * Resolves a string-valued attribute that may contain a SpEL expression.
     *
     * <p>If {@code value} starts with {@code #} (after leading whitespace), it is parsed
     * and evaluated as SpEL against a {@link MethodBasedEvaluationContext} built from the
     * given method invocation. Otherwise the value is returned verbatim — any literal that
     * does not begin with {@code #} bypasses the parser entirely so existing literal
     * configurations remain unaffected.
     *
     * <p>The evaluation context exposes the same {@code #args} and {@code #target}
     * variables as {@link #evaluate}, plus all named method parameters. {@code #result}
     * is intentionally not exposed: subject fields are evaluated before the guarded
     * method runs.
     *
     * @param value  the raw annotation attribute, possibly a SpEL expression
     * @param method the annotated method (for parameter name discovery); if {@code null},
     *               {@code value} is returned as-is
     * @param args   the method invocation arguments
     * @param target the bean instance on which the method was invoked
     * @return the resolved string, or {@code null} if the expression evaluates to {@code null}
     */
    public String evaluateString(String value, Method method, Object[] args, Object target) {
        if (value == null || method == null) {
            return value;
        }
        String trimmed = value.stripLeading();
        if (trimmed.isEmpty() || !trimmed.startsWith("#")) {
            return value;
        }
        Expression exp = parseCached(value);
        MethodBasedEvaluationContext ctx = new MethodBasedEvaluationContext(target, method, args, discoverer);
        ctx.setVariable("args", args);
        ctx.setVariable("target", target);
        Object result = exp.getValue(ctx);
        return result == null ? null : result.toString();
    }

    /**
     * Evaluates a SpEL expression to commit metadata.
     *
     * <p>The expression must evaluate to a {@code Map} with {@code String} keys.
     * The evaluation context exposes named method parameters, {@code #args},
     * {@code #target}, {@code #result}, and a root object with {@code target},
     * {@code args}, {@code result}, and {@code method} properties.
     *
     * @param expression the SpEL expression string
     * @param method     the annotated method
     * @param args       the method invocation arguments
     * @param result     the method return value
     * @param target     the bean instance on which the method was invoked
     * @return the evaluated metadata map, or an empty map when the expression evaluates to {@code null}
     * @throws IllegalArgumentException if the expression does not evaluate to a map with string keys
     */
    public Map<String, Object> evaluateMap(String expression,
                                           Method method,
                                           Object[] args,
                                           Object result,
                                           Object target) {
        if (expression == null || expression.isBlank()) {
            return Map.of();
        }
        if (method == null) {
            throw new IllegalArgumentException("Method is required for metadata expression evaluation");
        }

        Expression exp = parseCached(expression);
        MethodInvocationRoot root = new MethodInvocationRoot(target, args, result, method);
        MethodBasedEvaluationContext ctx = new MethodBasedEvaluationContext(root, method, args, discoverer);
        ctx.setVariable("args", args);
        ctx.setVariable("target", target);
        ctx.setVariable("result", result);
        ctx.setVariable("method", method);

        Object value = exp.getValue(ctx);
        if (value == null) {
            return Map.of();
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            throw new IllegalArgumentException("Metadata expression must evaluate to Map<String, Object>: " + expression);
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw new IllegalArgumentException("Metadata expression produced a non-string key: " + entry.getKey());
            }
            metadata.put(key, entry.getValue());
        }
        return metadata;
    }

    /**
     * Root object for method-invocation metadata expressions.
     */
    public static final class MethodInvocationRoot {
        private final Object target;
        private final Object[] args;
        private final Object result;
        private final Method method;

        MethodInvocationRoot(Object target, Object[] args, Object result, Method method) {
            this.target = target;
            this.args = args;
            this.result = result;
            this.method = method;
        }

        /**
         * Returns the bean instance on which the annotated method was invoked.
         *
         * @return the target bean instance
         */
        public Object getTarget() {
            return target;
        }

        /**
         * Returns the annotated method invocation arguments.
         *
         * @return the method invocation arguments
         */
        public Object[] getArgs() {
            return args;
        }

        /**
         * Returns the annotated method return value.
         *
         * @return the method return value
         */
        public Object getResult() {
            return result;
        }

        /**
         * Returns the annotated method.
         *
         * @return the annotated method
         */
        public Method getMethod() {
            return method;
        }
    }
}
