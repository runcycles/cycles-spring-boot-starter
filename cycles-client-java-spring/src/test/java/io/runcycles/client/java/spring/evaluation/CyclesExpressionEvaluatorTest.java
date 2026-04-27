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
        public String workspaced(String workspaceId, Request req) { return "ok"; }
    }

    public static class Request {
        private final String workspaceId;
        public Request(String workspaceId) { this.workspaceId = workspaceId; }
        public String getWorkspaceId() { return workspaceId; }
    }

    private Method processMethod() throws NoSuchMethodException {
        return SampleService.class.getMethod("process", int.class, String.class);
    }

    private Method computeMethod() throws NoSuchMethodException {
        return SampleService.class.getMethod("compute", int.class, int.class);
    }

    private Method workspacedMethod() throws NoSuchMethodException {
        return SampleService.class.getMethod("workspaced", String.class, Request.class);
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

        @Test
        void shouldThrowOnInvalidSpelExpression() throws Exception {
            assertThatThrownBy(() -> evaluator.evaluate("##invalid[[", processMethod(),
                    new Object[]{100, "gpt-4"}, null, new SampleService()))
                    .isInstanceOf(org.springframework.expression.ParseException.class);
        }
    }

    // ========================================================================
    // #target variable
    // ========================================================================

    @Nested
    @DisplayName("Target variable binding")
    class TargetBinding {

        @Test
        void shouldBindTargetVariable() throws Exception {
            // #target refers to the root object, which is the target instance
            // SampleService has a class name, so we can call toString or use it
            long result = evaluator.evaluate("#target.getClass().getSimpleName().length()", processMethod(),
                    new Object[]{100, "gpt-4"}, null, new SampleService());
            assertThat(result).isEqualTo("SampleService".length());
        }
    }

    // ========================================================================
    // Zero boundary
    // ========================================================================

    @Nested
    @DisplayName("Zero boundary")
    class ZeroBoundary {

        @Test
        void shouldAllowZeroValue() throws Exception {
            long result = evaluator.evaluate("0", processMethod(),
                    new Object[]{100, "gpt-4"}, null, new SampleService());
            assertThat(result).isEqualTo(0L);
        }

        @Test
        void shouldAllowZeroComputedValue() throws Exception {
            long result = evaluator.evaluate("#tokens * 0", processMethod(),
                    new Object[]{100, "gpt-4"}, null, new SampleService());
            assertThat(result).isEqualTo(0L);
        }
    }

    // ========================================================================
    // evaluateString — subject-field SpEL resolution
    // ========================================================================

    @Nested
    @DisplayName("evaluateString")
    class EvaluateString {

        @Test
        void shouldReturnLiteralUnchanged() throws Exception {
            String result = evaluator.evaluateString("production", processMethod(),
                    new Object[]{100, "gpt-4"}, new SampleService());
            assertThat(result).isEqualTo("production");
        }

        @Test
        void shouldReturnLiteralWithEmbeddedHashUnchanged() throws Exception {
            // Leading char is not '#', so this stays a literal even though it contains '#'.
            String result = evaluator.evaluateString("ws#prod", processMethod(),
                    new Object[]{100, "gpt-4"}, new SampleService());
            assertThat(result).isEqualTo("ws#prod");
        }

        @Test
        void shouldReturnNullValueUnchanged() throws Exception {
            String result = evaluator.evaluateString(null, processMethod(),
                    new Object[]{100, "gpt-4"}, new SampleService());
            assertThat(result).isNull();
        }

        @Test
        void shouldReturnEmptyStringUnchanged() throws Exception {
            String result = evaluator.evaluateString("", processMethod(),
                    new Object[]{100, "gpt-4"}, new SampleService());
            assertThat(result).isEmpty();
        }

        @Test
        void shouldReturnBlankStringUnchanged() throws Exception {
            String result = evaluator.evaluateString("   ", processMethod(),
                    new Object[]{100, "gpt-4"}, new SampleService());
            assertThat(result).isEqualTo("   ");
        }

        @Test
        void shouldReturnValueWhenMethodNull() {
            String result = evaluator.evaluateString("#workspaceId", null, null, null);
            assertThat(result).isEqualTo("#workspaceId");
        }

        @Test
        void shouldResolveSimpleParameterReference() throws Exception {
            String result = evaluator.evaluateString("#workspaceId", workspacedMethod(),
                    new Object[]{"ws-42", null}, new SampleService());
            assertThat(result).isEqualTo("ws-42");
        }

        @Test
        void shouldResolveNestedPropertyReference() throws Exception {
            String result = evaluator.evaluateString("#req.workspaceId", workspacedMethod(),
                    new Object[]{null, new Request("ws-99")}, new SampleService());
            assertThat(result).isEqualTo("ws-99");
        }

        @Test
        void shouldReturnNullForSafeNavOnNull() throws Exception {
            String result = evaluator.evaluateString("#req?.workspaceId", workspacedMethod(),
                    new Object[]{null, null}, new SampleService());
            assertThat(result).isNull();
        }

        @Test
        void shouldStringifyNonStringResult() throws Exception {
            // #args[0] is an int — SpEL returns Integer; toString applied.
            String result = evaluator.evaluateString("#tokens", processMethod(),
                    new Object[]{42, "gpt-4"}, new SampleService());
            assertThat(result).isEqualTo("42");
        }

        @Test
        void shouldHonorLeadingWhitespaceBeforeHash() throws Exception {
            String result = evaluator.evaluateString("  #workspaceId", workspacedMethod(),
                    new Object[]{"ws-77", null}, new SampleService());
            assertThat(result).isEqualTo("ws-77");
        }

        @Test
        void shouldReturnNullForUnknownVariableReference() throws Exception {
            // SpEL resolves an unknown #var to null rather than throwing. evaluateString returns
            // null so the resolver fallback chain (config → resolver bean) takes over — this is
            // the documented graceful path for safe-nav expressions like `#req?.workspaceId`.
            String result = evaluator.evaluateString("#nope", processMethod(),
                    new Object[]{100, "gpt-4"}, new SampleService());
            assertThat(result).isNull();
        }

        @Test
        void shouldPropagateEvaluationErrorOnInvalidPropertyAccess() throws Exception {
            // Property access on a non-null value that doesn't have the property fails fast at
            // AOP entry (SpelEvaluationException) instead of producing a malformed server request.
            assertThatThrownBy(() -> evaluator.evaluateString("#tokens.noSuchProperty", processMethod(),
                    new Object[]{100, "gpt-4"}, new SampleService()))
                    .isInstanceOf(org.springframework.expression.spel.SpelEvaluationException.class);
        }

        @Test
        void shouldPropagateParseErrorForMalformedExpression() throws Exception {
            // Malformed SpEL fails fast at AOP entry (ParseException) instead of being sent to
            // the server.
            assertThatThrownBy(() -> evaluator.evaluateString("#((bad", processMethod(),
                    new Object[]{100, "gpt-4"}, new SampleService()))
                    .isInstanceOf(org.springframework.expression.ParseException.class);
        }
    }

    // ========================================================================
    // Expression cache
    // ========================================================================

    @Nested
    @DisplayName("Expression cache")
    class ExpressionCache {

        @Test
        void shouldReturnConsistentResultsAcrossRepeatedEvaluations() throws Exception {
            // Many repeated evaluations of the same expression should all succeed and produce
            // identical output, validating that the cached Expression remains usable across calls.
            for (int i = 0; i < 50; i++) {
                long est = evaluator.evaluate("#tokens * 10", processMethod(),
                        new Object[]{100, "gpt-4"}, null, new SampleService());
                String ws = evaluator.evaluateString("#workspaceId", workspacedMethod(),
                        new Object[]{"ws-cache", null}, new SampleService());
                assertThat(est).isEqualTo(1000L);
                assertThat(ws).isEqualTo("ws-cache");
            }
        }
    }
}
