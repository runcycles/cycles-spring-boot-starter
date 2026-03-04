package io.runcycles.client.java.spring.autoconfigure;

import io.runcycles.client.java.spring.aspect.CyclesAspect;
import io.runcycles.client.java.spring.config.CyclesProperties;
import io.runcycles.client.java.spring.context.CyclesRequestBuilderService;
import io.runcycles.client.java.spring.evaluation.CyclesExpressionEvaluator;

import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.client.DefaultCyclesClient;
import io.runcycles.client.java.spring.evaluation.CyclesFieldResolver;
import io.runcycles.client.java.spring.evaluation.CyclesValueResolutionService;
import io.runcycles.client.java.spring.retry.CommitRetryEngine;
import io.runcycles.client.java.spring.retry.InMemoryCommitRetryEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

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
    @ConditionalOnMissingBean
    public CyclesValueResolutionService cyclesValueResolutionService(
            Map<String, CyclesFieldResolver> resolvers,
            CyclesProperties properties
    ) {
        return new CyclesValueResolutionService(resolvers, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public CyclesRequestBuilderService cyclesRequestBuilderService(
            CyclesValueResolutionService resolutionService
    ) {
        return new CyclesRequestBuilderService(resolutionService);
    }


    @Bean
    public CommitRetryEngine retryEngine(CyclesClient client, CyclesProperties props) {
        return new InMemoryCommitRetryEngine(client, props);
    }

    @Bean
    public CyclesAspect aspect(CyclesClient client,
                               CommitRetryEngine retryEngine,
                               CyclesRequestBuilderService cyclesRequestBuilderService,
                               CyclesExpressionEvaluator evaluator,
                               CyclesProperties props) {
        return new CyclesAspect(client, retryEngine, cyclesRequestBuilderService,evaluator,props);
    }
}
