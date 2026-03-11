package io.runcycles.client.java.spring.evaluation;

/**
 * Strategy interface for dynamically resolving Cycles subject field values at runtime.
 *
 * <p>Implementations are registered as Spring beans and looked up by field name
 * (e.g. {@code "tenant"}, {@code "workspace"}). They are consulted as a fallback
 * when neither the {@code @Cycles} annotation attribute nor the
 * {@code cycles.*} configuration property provides a value.
 *
 * <p>Example:
 * <pre>{@code
 * @Bean("tenant")
 * public CyclesFieldResolver tenantResolver(TenantContext ctx) {
 *     return ctx::getCurrentTenantId;
 * }
 * }</pre>
 *
 * @see CyclesValueResolutionService
 */
@FunctionalInterface
public interface CyclesFieldResolver {

    /**
     * Resolves the field value for the current request context.
     *
     * @return the resolved value, or {@code null} if unavailable
     */
    String resolve();
}
