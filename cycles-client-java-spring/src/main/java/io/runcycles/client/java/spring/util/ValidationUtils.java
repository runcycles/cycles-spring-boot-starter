package io.runcycles.client.java.spring.util;

import io.runcycles.client.java.spring.model.CyclesProtocolException;

import java.util.Map;

/**
 * Utility methods for validating and conditionally populating Cycles request payloads.
 */
public class ValidationUtils {

    /**
     * Puts the key-value pair into the map only if the value is non-null and non-blank.
     *
     * @param map   the target map
     * @param key   the map key
     * @param value the value to insert, or {@code null}/blank to skip
     */
    public static void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    /**
     * Throws {@link CyclesProtocolException} if the value is {@code null} or blank.
     *
     * @param value   the value to check
     * @param message the exception message if validation fails
     * @throws CyclesProtocolException if the value is null or blank
     */
    public static void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CyclesProtocolException(message);
        }
    }

    /**
     * Returns the first non-blank value between annotation and config sources.
     *
     * @param annotationValue the value from the annotation
     * @param configValue     the value from configuration
     * @return the first non-blank value, or {@code null} if both are blank
     */
    public static String resolve(String annotationValue, String configValue) {
        if (annotationValue != null && !annotationValue.isBlank()) {
            return annotationValue;
        }
        if (configValue != null && !configValue.isBlank()) {
            return configValue;
        }
        return null;
    }

    /**
     * Returns {@code true} if the value is non-null and contains at least one non-whitespace character.
     *
     * @param value the string to test
     * @return {@code true} if the value has text content
     */
    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
