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
