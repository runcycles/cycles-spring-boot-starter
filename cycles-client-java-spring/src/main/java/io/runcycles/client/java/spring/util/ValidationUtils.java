package io.runcycles.client.java.spring.util;

import io.runcycles.client.java.spring.model.CyclesProtocolException;

import java.util.Map;

public class ValidationUtils {
    // -------------------------
    // Helpers
    // -------------------------
    public static void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    public static void requireNotBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new CyclesProtocolException(message);
        }
    }
    public static String resolve(String annotationValue, String configValue) {
        if (annotationValue != null && !annotationValue.isBlank()) {
            return annotationValue;
        }

        if (configValue != null && !configValue.isBlank()) {
            return configValue;
        }
        return null;
    }
    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
