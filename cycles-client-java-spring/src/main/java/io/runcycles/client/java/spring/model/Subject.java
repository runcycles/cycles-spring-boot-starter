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

    /**
     * Creates a new {@code Subject} builder.
     *
     * @return a new builder instance
     */
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

        /** Creates a new, empty builder. */
        Builder() {}

        /**
         * Sets the tenant identifier.
         *
         * @param tenant the tenant
         * @return this builder
         */
        public Builder tenant(String tenant) { this.tenant = tenant; return this; }

        /**
         * Sets the workspace identifier.
         *
         * @param workspace the workspace
         * @return this builder
         */
        public Builder workspace(String workspace) { this.workspace = workspace; return this; }

        /**
         * Sets the application identifier.
         *
         * @param app the application
         * @return this builder
         */
        public Builder app(String app) { this.app = app; return this; }

        /**
         * Sets the workflow identifier.
         *
         * @param workflow the workflow
         * @return this builder
         */
        public Builder workflow(String workflow) { this.workflow = workflow; return this; }

        /**
         * Sets the agent identifier.
         *
         * @param agent the agent
         * @return this builder
         */
        public Builder agent(String agent) { this.agent = agent; return this; }

        /**
         * Sets the toolset identifier.
         *
         * @param toolset the toolset
         * @return this builder
         */
        public Builder toolset(String toolset) { this.toolset = toolset; return this; }

        /**
         * Sets the custom dimensions.
         *
         * @param dimensions the dimensions map
         * @return this builder
         */
        public Builder dimensions(Map<String, String> dimensions) { this.dimensions = dimensions; return this; }

        /**
         * Builds a new {@link Subject} from this builder's state.
         *
         * @return the constructed subject
         */
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

    /** Returns the tenant identifier. */
    public String getTenant() { return tenant; }
    /** Returns the workspace identifier. */
    public String getWorkspace() { return workspace; }
    /** Returns the application identifier. */
    public String getApp() { return app; }
    /** Returns the workflow identifier. */
    public String getWorkflow() { return workflow; }
    /** Returns the agent identifier. */
    public String getAgent() { return agent; }
    /** Returns the toolset identifier. */
    public String getToolset() { return toolset; }
    /** Returns the custom dimensions map. */
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
