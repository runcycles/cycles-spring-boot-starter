package io.runcycles.client.java.spring.evaluation;

import io.runcycles.client.java.spring.config.CyclesProperties;
import io.runcycles.client.java.spring.util.Constants;
import io.runcycles.client.java.spring.util.ValidationUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves Cycles subject field values using a three-tier precedence chain:
 *
 * <ol>
 *   <li><strong>Annotation</strong> — value set directly on the {@code @Cycles} attribute</li>
 *   <li><strong>Configuration</strong> — value from {@link CyclesProperties} ({@code cycles.*})</li>
 *   <li><strong>Resolver bean</strong> — a {@link CyclesFieldResolver} looked up by field name</li>
 * </ol>
 *
 * <p>The first non-blank value wins. This allows applications to set global defaults
 * in configuration while overriding per-method via the annotation or dynamically via
 * resolver beans.
 *
 * @see CyclesFieldResolver
 * @see CyclesProperties
 */
public class CyclesValueResolutionService {

    private final CyclesProperties properties;
    private final ConcurrentHashMap<String, CyclesFieldResolver> resolverMap;

    public CyclesValueResolutionService(Map<String, CyclesFieldResolver> resolvers, CyclesProperties properties) {
        resolverMap = new ConcurrentHashMap<>(resolvers);
        this.properties = properties;
    }

    /**
     * Resolves a subject field value using the annotation &rarr; config &rarr; resolver chain.
     *
     * @param fieldName       the subject field name (e.g. {@code "tenant"})
     * @param annotationValue the value from the {@code @Cycles} annotation, may be blank
     * @return the resolved value, or {@code null} if none of the sources provide one
     */
    public String resolve(String fieldName, String annotationValue) {

        // 1. Annotation override (fast path)
        if (ValidationUtils.hasText(annotationValue)) {
            return annotationValue;
        }

        // 2. Configuration (fast switch)
        String configValue = getFromConfig(fieldName);
        if (ValidationUtils.hasText(configValue)) {
            return configValue;
        }

        // 3. Direct O(1) concurrent lookup
        CyclesFieldResolver resolver = resolverMap.get(fieldName);
        if (resolver == null) {
            return null;
        }

        return resolver.resolve(); // fully dynamic
    }

    private String getFromConfig(String fieldName) {
        return switch (fieldName) {
            case Constants.TENANT -> properties.getTenant();
            case Constants.WORKSPACE -> properties.getWorkspace();
            case Constants.APP -> properties.getApp();
            case Constants.WORKFLOW -> properties.getWorkflow();
            case Constants.AGENT -> properties.getAgent();
            case Constants.TOOLSET -> properties.getToolset();
            default -> null;
        };
    }
}
