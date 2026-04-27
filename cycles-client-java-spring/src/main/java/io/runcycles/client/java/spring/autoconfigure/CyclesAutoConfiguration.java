package io.runcycles.client.java.spring.autoconfigure;

import io.netty.channel.ChannelOption;
import io.runcycles.client.java.spring.aspect.CyclesAspect;
import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.client.DefaultCyclesClient;
import io.runcycles.client.java.spring.config.CyclesProperties;
import io.runcycles.client.java.spring.context.CyclesLifecycleService;
import io.runcycles.client.java.spring.context.CyclesRequestBuilderService;
import io.runcycles.client.java.spring.evaluation.CyclesExpressionEvaluator;
import io.runcycles.client.java.spring.evaluation.CyclesFieldResolver;
import io.runcycles.client.java.spring.evaluation.CyclesValueResolutionService;
import io.runcycles.client.java.spring.retry.CommitRetryEngine;
import io.runcycles.client.java.spring.retry.InMemoryCommitRetryEngine;
import io.runcycles.client.java.spring.util.Constants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.Map;

/**
 * Spring Boot auto-configuration for the Cycles client library.
 *
 * <p>Registers all required beans — HTTP client, {@link CyclesClient}, expression
 * evaluator, value resolution, request builder, commit retry engine, lifecycle
 * service, and AOP aspect — unless the application provides its own.
 *
 * <p>Activated automatically via
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}.
 *
 * @see CyclesProperties
 * @see CyclesClient
 * @see CyclesAspect
 */
@AutoConfiguration
@EnableConfigurationProperties(CyclesProperties.class)
public class CyclesAutoConfiguration {

    /** Creates a new auto-configuration instance. */
    public CyclesAutoConfiguration() {}

    /**
     * Configures the WebClient used for Cycles API communication.
     *
     * @param props the Cycles configuration properties
     * @return the configured WebClient
     */
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
                .defaultHeader(Constants.X_CYCLES_API_KEY_HEADER, props.getApiKey())
                .build();
    }

    /**
     * Registers the default {@link CyclesClient} backed by WebClient.
     *
     * @param cyclesWebClient the configured WebClient
     * @return the default Cycles client
     */
    @Bean
    @ConditionalOnMissingBean
    public CyclesClient cyclesClient(@Qualifier("cyclesWebClient") WebClient cyclesWebClient) {
        return new DefaultCyclesClient(cyclesWebClient);
    }

    /**
     * Registers the SpEL expression evaluator for {@code @Cycles} attributes.
     *
     * @return the expression evaluator
     */
    @Bean
    @ConditionalOnMissingBean
    public CyclesExpressionEvaluator evaluator() {
        return new CyclesExpressionEvaluator();
    }

    /**
     * Registers the three-tier value resolution service.
     *
     * @param resolvers  the available field resolver beans
     * @param properties the Cycles configuration properties
     * @return the value resolution service
     */
    @Bean
    @ConditionalOnMissingBean
    public CyclesValueResolutionService cyclesValueResolutionService(
            Map<String, CyclesFieldResolver> resolvers,
            CyclesProperties properties
    ) {
        return new CyclesValueResolutionService(resolvers, properties);
    }

    /**
     * Registers the request payload builder service.
     *
     * @param resolutionService the value resolution service
     * @param evaluator         the SpEL expression evaluator
     * @return the request builder service
     */
    @Bean
    @ConditionalOnMissingBean
    public CyclesRequestBuilderService cyclesRequestBuilderService(
            CyclesValueResolutionService resolutionService,
            CyclesExpressionEvaluator evaluator
    ) {
        return new CyclesRequestBuilderService(resolutionService, evaluator);
    }

    /**
     * Registers the exponential-backoff retry engine for failed commits.
     *
     * @param client the Cycles API client
     * @param props  the Cycles configuration properties
     * @return the commit retry engine
     */
    @Bean
    @ConditionalOnMissingBean
    public CommitRetryEngine retryEngine(CyclesClient client, CyclesProperties props) {
        return new InMemoryCommitRetryEngine(client, props);
    }

    /**
     * Registers the lifecycle service orchestrating reserve/execute/commit.
     *
     * @param client                the Cycles API client
     * @param retryEngine           the commit retry engine
     * @param requestBuilderService the request builder service
     * @param evaluator             the SpEL expression evaluator
     * @return the lifecycle service
     */
    @Bean
    @ConditionalOnMissingBean
    public CyclesLifecycleService cyclesLifecycleService(CyclesClient client,
                                                         CommitRetryEngine retryEngine,
                                                         CyclesRequestBuilderService requestBuilderService,
                                                         CyclesExpressionEvaluator evaluator) {
        return new CyclesLifecycleService(client, retryEngine, requestBuilderService, evaluator);
    }

    /**
     * Registers the AOP aspect that intercepts {@code @Cycles}-annotated methods.
     *
     * @param lifecycleService the lifecycle service
     * @return the Cycles aspect
     */
    @Bean
    @ConditionalOnMissingBean
    public CyclesAspect aspect(CyclesLifecycleService lifecycleService) {
        return new CyclesAspect(lifecycleService);
    }

    /**
     * Registers a bean post-processor that warns about beans susceptible to
     * the Spring AOP self-invocation pitfall with {@code @Cycles}.
     *
     * @return the self-invocation detector
     */
    @Bean
    @ConditionalOnMissingBean
    public static CyclesSelfInvocationDetector cyclesSelfInvocationDetector() {
        return new CyclesSelfInvocationDetector();
    }
}
