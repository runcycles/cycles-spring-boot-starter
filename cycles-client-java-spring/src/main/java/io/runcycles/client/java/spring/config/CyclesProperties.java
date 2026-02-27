package io.runcycles.client.java.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "cycles")
public class CyclesProperties {

    private String baseUrl;
    private String apiKey;

    private Http http = new Http();
    private Retry retry = new Retry();

    public static class Http {
        private Duration connectTimeout = Duration.ofSeconds(2);
        private Duration readTimeout = Duration.ofSeconds(5);
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getReadTimeout() { return readTimeout; }
        public void setReadTimeout(Duration readTimeout) { this.readTimeout = readTimeout; }
    }

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
}
