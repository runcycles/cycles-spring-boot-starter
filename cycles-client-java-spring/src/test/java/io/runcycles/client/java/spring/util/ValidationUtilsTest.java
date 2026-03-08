package io.runcycles.client.java.spring.util;

import io.runcycles.client.java.spring.model.CyclesProtocolException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ValidationUtils")
class ValidationUtilsTest {

    // ========================================================================
    // putIfNotBlank
    // ========================================================================

    @Nested
    @DisplayName("putIfNotBlank")
    class PutIfNotBlank {

        @Test
        void shouldPutNonBlankValue() {
            Map<String, Object> map = new HashMap<>();
            ValidationUtils.putIfNotBlank(map, "key", "value");
            assertThat(map).containsEntry("key", "value");
        }

        @Test
        void shouldNotPutNullValue() {
            Map<String, Object> map = new HashMap<>();
            ValidationUtils.putIfNotBlank(map, "key", null);
            assertThat(map).isEmpty();
        }

        @Test
        void shouldNotPutBlankValue() {
            Map<String, Object> map = new HashMap<>();
            ValidationUtils.putIfNotBlank(map, "key", "   ");
            assertThat(map).isEmpty();
        }

        @Test
        void shouldNotPutEmptyValue() {
            Map<String, Object> map = new HashMap<>();
            ValidationUtils.putIfNotBlank(map, "key", "");
            assertThat(map).isEmpty();
        }
    }

    // ========================================================================
    // requireNotBlank
    // ========================================================================

    @Nested
    @DisplayName("requireNotBlank")
    class RequireNotBlank {

        @Test
        void shouldPassForNonBlankValue() {
            ValidationUtils.requireNotBlank("valid", "should not throw");
            // No exception
        }

        @Test
        void shouldThrowForNull() {
            assertThatThrownBy(() -> ValidationUtils.requireNotBlank(null, "field is required"))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("field is required");
        }

        @Test
        void shouldThrowForBlank() {
            assertThatThrownBy(() -> ValidationUtils.requireNotBlank("  ", "field is required"))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("field is required");
        }

        @Test
        void shouldThrowForEmpty() {
            assertThatThrownBy(() -> ValidationUtils.requireNotBlank("", "field is required"))
                    .isInstanceOf(CyclesProtocolException.class)
                    .hasMessageContaining("field is required");
        }
    }

    // ========================================================================
    // resolve
    // ========================================================================

    @Nested
    @DisplayName("resolve")
    class Resolve {

        @Test
        void shouldReturnAnnotationValue() {
            assertThat(ValidationUtils.resolve("annotation", "config")).isEqualTo("annotation");
        }

        @Test
        void shouldFallBackToConfig() {
            assertThat(ValidationUtils.resolve("", "config")).isEqualTo("config");
            assertThat(ValidationUtils.resolve(null, "config")).isEqualTo("config");
            assertThat(ValidationUtils.resolve("  ", "config")).isEqualTo("config");
        }

        @Test
        void shouldReturnNullWhenBothEmpty() {
            assertThat(ValidationUtils.resolve("", "")).isNull();
            assertThat(ValidationUtils.resolve(null, null)).isNull();
            assertThat(ValidationUtils.resolve("  ", "  ")).isNull();
        }
    }

    // ========================================================================
    // hasText
    // ========================================================================

    @Nested
    @DisplayName("hasText")
    class HasText {

        @Test
        void shouldReturnTrueForNonBlank() {
            assertThat(ValidationUtils.hasText("hello")).isTrue();
        }

        @Test
        void shouldReturnFalseForNull() {
            assertThat(ValidationUtils.hasText(null)).isFalse();
        }

        @Test
        void shouldReturnFalseForEmpty() {
            assertThat(ValidationUtils.hasText("")).isFalse();
        }

        @Test
        void shouldReturnFalseForBlank() {
            assertThat(ValidationUtils.hasText("   ")).isFalse();
        }
    }
}
