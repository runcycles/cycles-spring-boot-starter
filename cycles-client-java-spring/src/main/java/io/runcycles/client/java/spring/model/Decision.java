package io.runcycles.client.java.spring.model;

public enum Decision {
    ALLOW,
    ALLOW_WITH_CAPS,
    DENY;

    public static Decision fromString(String value) {
        if (value == null) return null;
        try {
            return Decision.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
