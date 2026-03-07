package io.runcycles.client.java.spring.evaluation;

import io.runcycles.client.java.spring.config.CyclesProperties;
import io.runcycles.client.java.spring.util.Constants;
import io.runcycles.client.java.spring.util.ValidationUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CyclesValueResolutionService {

    private final CyclesProperties properties;
    private final ConcurrentHashMap<String, CyclesFieldResolver> resolverMap;

    public CyclesValueResolutionService (Map<String, CyclesFieldResolver> resolvers,CyclesProperties properties){
        resolverMap = new ConcurrentHashMap<>(resolvers);
        this.properties = properties;
    }
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
            case  Constants.TOOLSET -> properties.getToolset();
            default -> null;
        };
    }
}
