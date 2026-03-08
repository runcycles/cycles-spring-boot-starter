package io.runcycles.client.java.spring.evaluation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CyclesExpressionEvaluator")
class CyclesExpressionEvaluatorTest {

    private CyclesExpressionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new CyclesExpressionEvaluator();
    }

    // Target class with methods for SpEL evaluation
    public static class SampleService {
        public String process(int tokens, String model) { return "done"; }
        public int compute(int a, int b) { return a + b; }
    }

    private Method processMethod() throws NoSuchMethodException {
        return SampleService.class.getMethod("process", int.class, String.class);
    }

    private Method computeMethod() throws NoSuchMethodException {
        return SampleService.class.getMethod("compute", int.class, int.class);
    }

    // ========================================================================
    // Literal expressions
    // ========================================================================

    @Nested
    @DisplayName("Literal expressions")
    class LiteralExpressions {

        @Test
        void shouldEvaluateLiteralNumber() throws Exception {
            long result = evaluator.evaluate("1000", processMethod(),
                    new Object[]{100, "gpt-4"}, null, new SampleService());
            assertThat(result).isEqualTo(1000L);
        }

        @Test
        void shouldEvaluateArithmeticExpression() throws Exception {
            long result = evaluator.evaluate("500 + 300", processMethod(),
                    new Object[]{100, "gpt-4"}, null, new SampleService());
            assertThat(result).isEqualTo(800L);
        }
    }

    // ========================================================================
    // Parameter binding
    // ========================================================================

    @Nested
    @DisplayName("Parameter binding")
    class ParameterBinding {

        @Test
        void shouldBindMethodParameters() throws Exception {
            long result = evaluator.evaluate("#tokens * 10", processMethod(),
                    new Object[]{100, "gpt-4"}, null, new SampleService());
            assertThat(result).isEqualTo(1000L);
        }

        @Test
        void shouldBindMultipleParameters() throws Exception {
            long result = evaluator.evaluate("#a + #b", computeMethod(),
                    new Object[]{30, 70}, null, new SampleService());
            assertThat(result).isEqualTo(100L);
        }
    }

    // ========================================================================
    // #result variable
    // ========================================================================

    @Nested
    @DisplayName("Result variable binding")
    class ResultBinding {

        @Test
        void shouldBindResultVariable() throws Exception {
            long result = evaluator.evaluate("#result.length()", processMethod(),
                    new Object[]{100, "gpt-4"}, "hello", new SampleService());
            assertThat(result).isEqualTo(5L);
        }

        @Test
        void shouldHandleNullResult() throws Exception {
            // Expression that doesn't use #result should still work with null
            long result = evaluator.evaluate("42", processMethod(),
                    new Object[]{100, "gpt-4"}, null, new SampleService());
            assertThat(result).isEqualTo(42L);
        }
    }

    // ========================================================================
    // #args and #target variables
    // ========================================================================

    @Nested
    @DisplayName("Args and target variables")
    class ArgsAndTargetBinding {

        @Test
        void shouldBindArgsVariable() throws Exception {
            long result = evaluator.evaluate("#args[0] * 5", processMethod(),
                    new Object[]{200, "gpt-4"}, null, new SampleService());
            assertThat(result).isEqualTo(1000L);
        }
    }

    // ========================================================================
    // Error cases
    // ========================================================================

    @Nested
    @DisplayName("Error cases")
    class ErrorCases {

        @Test
        void shouldRejectNullResult() throws Exception {
            assertThatThrownBy(() -> evaluator.evaluate("null", processMethod(),
                    new Object[]{100, "gpt-4"}, null, new SampleService()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("evaluated to null");
        }

        @Test
        void shouldRejectNegativeValue() throws Exception {
            assertThatThrownBy(() -> evaluator.evaluate("-1", processMethod(),
                    new Object[]{100, "gpt-4"}, null, new SampleService()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be negative");
        }

        @Test
        void shouldRejectNegativeComputedValue() throws Exception {
            assertThatThrownBy(() -> evaluator.evaluate("#tokens * -1", processMethod(),
                    new Object[]{100, "gpt-4"}, null, new SampleService()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("must not be negative");
        }
    }
}
