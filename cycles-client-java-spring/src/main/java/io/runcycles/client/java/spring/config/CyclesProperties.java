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
 * <h2>Minimal configuration</h2>
 * <pre>
 * cycles.base-url=https://api.runcycles.io
 * cycles.api-key=your-api-key
 * </pre>
 */
@ConfigurationProperties(prefix = "cycles")
public class CyclesProperties {

    /** Creates a new properties instance with default values. */
    public CyclesProperties() {}

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

        /** Creates a new HTTP settings instance with default values. */
        Http() {}

        /**
         * Returns the connection timeout.
         *
         * @return the connection timeout duration
         */
        public Duration getConnectTimeout() { return connectTimeout; }

        /**
         * Sets the connection timeout.
         *
         * @param connectTimeout the connection timeout duration
         */
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }

        /**
         * Returns the read timeout.
         *
         * @return the read timeout duration
         */
        public Duration getReadTimeout() { return readTimeout; }

        /**
         * Sets the read timeout.
         *
         * @param readTimeout the read timeout duration
         */
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    }

    /** Exponential-backoff retry settings for failed commit operations. */
    public static class Retry {
        private boolean enabled = true;
        private int maxAttempts = 5;
        private Duration initialDelay = Duration.ofMillis(500);
        private double multiplier = 2.0;
        private Duration maxDelay = Duration.ofSeconds(30);

        /** Creates a new retry settings instance with default values. */
        Retry() {}

        /**
         * Returns whether retry is enabled.
         *
         * @return {@code true} if retry is enabled
         */
        public boolean isEnabled() { return enabled; }

        /**
         * Sets whether retry is enabled.
         *
         * @param enabled {@code true} to enable retry
         */
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        /**
         * Returns the maximum number of retry attempts.
         *
         * @return the maximum attempts
         */
        public int getMaxAttempts() { return maxAttempts; }

        /**
         * Sets the maximum number of retry attempts.
         *
         * @param maxAttempts the maximum attempts
         */
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

        /**
         * Returns the initial delay between retries.
         *
         * @return the initial delay duration
         */
        public Duration getInitialDelay() { return initialDelay; }

        /**
         * Sets the initial delay between retries.
         *
         * @param initialDelay the initial delay duration
         */
        public void setInitialDelay(Duration initialDelay) { this.initialDelay = initialDelay; }

        /**
         * Returns the backoff multiplier.
         *
         * @return the multiplier
         */
        public double getMultiplier() { return multiplier; }

        /**
         * Sets the backoff multiplier.
         *
         * @param multiplier the multiplier
         */
        public void setMultiplier(double multiplier) { this.multiplier = multiplier; }

        /**
         * Returns the maximum delay between retries.
         *
         * @return the maximum delay duration
         */
        public Duration getMaxDelay() { return maxDelay; }

        /**
         * Sets the maximum delay between retries.
         *
         * @param maxDelay the maximum delay duration
         */
        public void setMaxDelay(Duration maxDelay) { this.maxDelay = maxDelay; }
    }

    /**
     * Returns the Cycles API base URL.
     *
     * @return the base URL
     */
    public String getBaseUrl() { return baseUrl; }

    /**
     * Sets the Cycles API base URL.
     *
     * @param baseUrl the base URL
     */
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    /**
     * Returns the Cycles API key.
     *
     * @return the API key
     */
    public String getApiKey() { return apiKey; }

    /**
     * Sets the Cycles API key.
     *
     * @param apiKey the API key
     */
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    /**
     * Returns the HTTP settings.
     *
     * @return the HTTP configuration
     */
    public Http getHttp() { return http; }

    /**
     * Returns the retry settings.
     *
     * @return the retry configuration
     */
    public Retry getRetry() { return retry; }

    /**
     * Returns the default tenant identifier.
     *
     * @return the tenant
     */
    public String getTenant() { return tenant; }

    /**
     * Sets the default tenant identifier.
     *
     * @param tenant the tenant
     */
    public void setTenant(String tenant) { this.tenant = tenant; }

    /**
     * Returns the default workspace identifier.
     *
     * @return the workspace
     */
    public String getWorkspace() { return workspace; }

    /**
     * Sets the default workspace identifier.
     *
     * @param workspace the workspace
     */
    public void setWorkspace(String workspace) { this.workspace = workspace; }

    /**
     * Returns the default application identifier.
     *
     * @return the application
     */
    public String getApp() { return app; }

    /**
     * Sets the default application identifier.
     *
     * @param app the application
     */
    public void setApp(String app) { this.app = app; }

    /**
     * Returns the default workflow identifier.
     *
     * @return the workflow
     */
    public String getWorkflow() { return workflow; }

    /**
     * Sets the default workflow identifier.
     *
     * @param workflow the workflow
     */
    public void setWorkflow(String workflow) { this.workflow = workflow; }

    /**
     * Returns the default agent identifier.
     *
     * @return the agent
     */
    public String getAgent() { return agent; }

    /**
     * Sets the default agent identifier.
     *
     * @param agent the agent
     */
    public void setAgent(String agent) { this.agent = agent; }

    /**
     * Returns the default toolset identifier.
     *
     * @return the toolset
     */
    public String getToolset() { return toolset; }

    /**
     * Sets the default toolset identifier.
     *
     * @param toolset the toolset
     */
    public void setToolset(String toolset) { this.toolset = toolset; }
}
