package io.runcycles.client.java.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration properties for the Cycles Spring Boot starter, bound to the
 * {@code cycles.*} namespace.
 *
 * <p>Includes connection settings ({@code base-url}, {@code api-key}), HTTP timeouts,
 * commit retry policy, and default subject field values used when the corresponding
 * {@link io.runcycles.client.java.spring.annotation.Cycles @Cycles} annotation
 * attribute is left blank.
 *
 * <h4>Minimal configuration</h4>
 * <pre>
 * cycles.base-url=https://api.runcycles.io
 * cycles.api-key=your-api-key
 * </pre>
 */
@ConfigurationProperties(prefix = "cycles")
public class CyclesProperties {

    private String baseUrl;
    private String apiKey;
    // Protocol properties as defaults for @Cycles annotation
    private String tenant;
    private String workspace;
    private String app;
    private String workflow;
    private String agent;
    private String toolset;

    private Http http = new Http();
    private Retry retry = new Retry();

    /** HTTP connection and read timeout settings for the Cycles API client. */
    public static class Http {
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    }

    /** Exponential-backoff retry settings for failed commit operations. */
    public static class Retry {
        private boolean enabled = true;
        private int maxAttempts = 5;
        private Duration initialDelay = Duration.ofMillis(500);
        private double multiplier = 2.0;
        private Duration maxDelay = Duration.ofSeconds(30);

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
        public Duration getInitialDelay() { return initialDelay; }
        public void setInitialDelay(Duration initialDelay) { this.initialDelay = initialDelay; }
        public double getMultiplier() { return multiplier; }
        public void setMultiplier(double multiplier) { this.multiplier = multiplier; }
        public Duration getMaxDelay() { return maxDelay; }
        public void setMaxDelay(Duration maxDelay) { this.maxDelay = maxDelay; }
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public Http getHttp() { return http; }
    public Retry getRetry() { return retry; }

    public String getTenant() { return tenant; }
    public void setTenant(String tenant) { this.tenant = tenant; }
    public String getWorkspace() { return workspace; }
    public void setWorkspace(String workspace) { this.workspace = workspace; }
    public String getApp() { return app; }
    public void setApp(String app) { this.app = app; }
    public String getWorkflow() { return workflow; }
    public void setWorkflow(String workflow) { this.workflow = workflow; }
    public String getAgent() { return agent; }
    public void setAgent(String agent) { this.agent = agent; }
    public String getToolset() { return toolset; }
    public void setToolset(String toolset) { this.toolset = toolset; }
}
