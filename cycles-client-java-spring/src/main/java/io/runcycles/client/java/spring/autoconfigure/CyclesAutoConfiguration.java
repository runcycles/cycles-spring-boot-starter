package io.runcycles.client.java.spring.autoconfigure;

import io.runcycles.client.java.spring.aspect.CyclesAspect;
import io.runcycles.client.java.spring.config.CyclesProperties;
import io.runcycles.client.java.spring.evaluation.CyclesExpressionEvaluator;

import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.client.DefaultCyclesClient;
import io.runcycles.client.java.spring.retry.CommitRetryEngine;
import io.runcycles.client.java.spring.retry.InMemoryCommitRetryEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

@AutoConfiguration
@EnableConfigurationProperties(CyclesProperties.class)
public class CyclesAutoConfiguration {

    @Bean
    public WebClient cyclesWebClient(CyclesProperties props) {
        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .defaultHeader("X-Cycles-API-Key", props.getApiKey())
                .build();
    }

    @Bean
    public CyclesClient cyclesClient(WebClient webClient) {
        return new DefaultCyclesClient(webClient);
    }

    @Bean
    public CyclesExpressionEvaluator evaluator() {
        return new CyclesExpressionEvaluator();
    }

    @Bean
    public CommitRetryEngine retryEngine(CyclesClient client, CyclesProperties props) {
        return new InMemoryCommitRetryEngine(client, props);
    }

    @Bean
    public CyclesAspect aspect(CyclesClient client,
                               CommitRetryEngine retryEngine,
                               CyclesExpressionEvaluator evaluator,
                               CyclesProperties props) {
        return new CyclesAspect(client, retryEngine, evaluator,props);
    }
}
