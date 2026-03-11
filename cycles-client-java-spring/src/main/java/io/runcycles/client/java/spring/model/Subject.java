package io.runcycles.client.java.spring.model;

import java.util.Map;

/**
 * Identifies the entity performing a budgeted action.
 * Mirrors the server's {@code Subject} DTO.
 * <p>
 * At least one standard field (tenant, workspace, app, workflow, agent, toolset) must be non-blank.
 */
public class Subject {
    private final String tenant;
    private final String workspace;
    private final String app;
    private final String workflow;
    private final String agent;
    private final String toolset;
    private final Map<String, String> dimensions;

    private Subject(String tenant, String workspace, String app,
                    String workflow, String agent, String toolset,
                    Map<String, String> dimensions) {
        this.tenant = tenant;
        this.workspace = workspace;
        this.app = app;
        this.workflow = workflow;
        this.agent = agent;
        this.toolset = toolset;
        this.dimensions = dimensions;
    }

    /** Creates a new {@code Subject} builder. */
    public static Builder builder() { return new Builder(); }

    /** Builder for constructing {@link Subject} instances. */
    public static class Builder {
        private String tenant;
        private String workspace;
        private String app;
        private String workflow;
        private String agent;
        private String toolset;
        private Map<String, String> dimensions;

        public Builder tenant(String tenant) { this.tenant = tenant; return this; }
        public Builder workspace(String workspace) { this.workspace = workspace; return this; }
        public Builder app(String app) { this.app = app; return this; }
        public Builder workflow(String workflow) { this.workflow = workflow; return this; }
        public Builder agent(String agent) { this.agent = agent; return this; }
        public Builder toolset(String toolset) { this.toolset = toolset; return this; }
        public Builder dimensions(Map<String, String> dimensions) { this.dimensions = dimensions; return this; }
        public Subject build() { return new Subject(tenant, workspace, app, workflow, agent, toolset, dimensions); }
    }

    /**
     * Deserializes a {@code Subject} from a raw API response map.
     *
     * @param map the subject section of the response, or {@code null}
     * @return the parsed {@code Subject}, or {@code null} if the input is {@code null}
     */
    @SuppressWarnings("unchecked")
    public static Subject fromMap(Map<String, Object> map) {
        if (map == null) return null;
        return new Subject(
                map.get("tenant") instanceof String s ? s : null,
                map.get("workspace") instanceof String s ? s : null,
                map.get("app") instanceof String s ? s : null,
                map.get("workflow") instanceof String s ? s : null,
                map.get("agent") instanceof String s ? s : null,
                map.get("toolset") instanceof String s ? s : null,
                map.get("dimensions") instanceof Map<?, ?> m ? (Map<String, String>) m : null
        );
    }

    public String getTenant() { return tenant; }
    public String getWorkspace() { return workspace; }
    public String getApp() { return app; }
    public String getWorkflow() { return workflow; }
    public String getAgent() { return agent; }
    public String getToolset() { return toolset; }
    public Map<String, String> getDimensions() { return dimensions; }

    /**
     * Serializes this subject to a map suitable for the API request body.
     *
     * @return a mutable map of non-null fields
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        if (tenant != null) map.put("tenant", tenant);
        if (workspace != null) map.put("workspace", workspace);
        if (app != null) map.put("app", app);
        if (workflow != null) map.put("workflow", workflow);
        if (agent != null) map.put("agent", agent);
        if (toolset != null) map.put("toolset", toolset);
        if (dimensions != null && !dimensions.isEmpty()) map.put("dimensions", dimensions);
        return map;
    }

    /**
     * Returns {@code true} if at least one standard subject field is non-blank.
     *
     * @return whether any of tenant, workspace, app, workflow, agent, or toolset is set
     */
    public boolean hasAtLeastOneStandardField() {
        return isNonBlank(tenant) || isNonBlank(workspace) || isNonBlank(app)
                || isNonBlank(workflow) || isNonBlank(agent) || isNonBlank(toolset);
    }

    private static boolean isNonBlank(String s) {
        return s != null && !s.isBlank();
    }

    @Override
    public String toString() {
        return "Subject{tenant='" + tenant + '\'' +
                ", workspace='" + workspace + '\'' +
                ", app='" + app + '\'' +
                ", workflow='" + workflow + '\'' +
                ", agent='" + agent + '\'' +
                ", toolset='" + toolset + '\'' +
                ", dimensions=" + dimensions + '}';
    }
}
