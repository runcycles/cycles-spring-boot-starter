package io.runcycles.client.java.spring.autoconfigure;

import io.netty.channel.ChannelOption;
import io.runcycles.client.java.spring.aspect.CyclesAspect;
import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.client.DefaultCyclesClient;
import io.runcycles.client.java.spring.config.CyclesProperties;
import io.runcycles.client.java.spring.context.CyclesRequestBuilderService;
import io.runcycles.client.java.spring.evaluation.CyclesExpressionEvaluator;
import io.runcycles.client.java.spring.evaluation.CyclesFieldResolver;
import io.runcycles.client.java.spring.evaluation.CyclesValueResolutionService;
import io.runcycles.client.java.spring.retry.CommitRetryEngine;
import io.runcycles.client.java.spring.retry.InMemoryCommitRetryEngine;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.Map;

@AutoConfiguration
@EnableConfigurationProperties(CyclesProperties.class)
public class CyclesAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "cyclesWebClient")
    public WebClient cyclesWebClient(CyclesProperties props) {
        if (props.getBaseUrl() == null || props.getBaseUrl().isBlank()) {
            throw new IllegalStateException("cycles.base-url must be configured");
        }
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new IllegalStateException("cycles.api-key must be configured");
        }
        CyclesProperties.Http httpProps = props.getHttp();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) httpProps.getConnectTimeout().toMillis())
                .responseTimeout(httpProps.getReadTimeout());
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(props.getBaseUrl())
                .defaultHeader("X-Cycles-API-Key", props.getApiKey())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public CyclesClient cyclesClient(@Qualifier("cyclesWebClient") WebClient cyclesWebClient) {
        return new DefaultCyclesClient(cyclesWebClient);
    }

    @Bean
    @ConditionalOnMissingBean
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
    @ConditionalOnMissingBean
    public CommitRetryEngine retryEngine(CyclesClient client, CyclesProperties props) {
        return new InMemoryCommitRetryEngine(client, props);
    }

    @Bean
    public CyclesAspect aspect(CyclesClient client,
                               CommitRetryEngine retryEngine,
                               CyclesRequestBuilderService cyclesRequestBuilderService,
                               CyclesExpressionEvaluator evaluator,
                               CyclesProperties props) {
        return new CyclesAspect(client, retryEngine, cyclesRequestBuilderService, evaluator, props);
    }
}
