package io.runcycles.client.java.spring.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;

public class CyclesExpressionEvaluator {
    private static final Logger LOG = LoggerFactory.getLogger(CyclesExpressionEvaluator.class);
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

    public long evaluate(String expression,
                         Method method,
                         Object[] args,
                         Object result,
                         Object target) {
        LOG.info("Evaluating expression: expression={},method={},args={}, result={}, target={}",expression,method,args,result,target);
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

        if (longValue <= 0) {
            throw new IllegalArgumentException(
                    "Charge amount must be positive. Expression: "
                            + expression + " evaluated to: " + longValue
            );
        }

        return longValue;
    }
}
