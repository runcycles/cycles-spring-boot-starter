package io.runcycles.client.java.spring.util;

/**
 * Shared constant values used across the Cycles client library.
 *
 * <p>Contains JSON field names for API request/response payloads, query parameter
 * keys, and HTTP header names.
 */
public final class Constants {

    private Constants() {}

    // --- JSON field names ---

    /** JSON field: {@code idempotency_key}. */
    public static final String IDEMPOTENCY_KEY = "idempotency_key";
    /** JSON field: {@code unit}. */
    public static final String UNIT = "unit";
    /** JSON field: {@code amount}. */
    public static final String AMOUNT = "amount";
    /** JSON field: {@code tenant}. */
    public static final String TENANT = "tenant";
    /** JSON field: {@code workspace}. */
    public static final String WORKSPACE = "workspace";
    /** JSON field: {@code app}. */
    public static final String APP = "app";
    /** JSON field: {@code workflow}. */
    public static final String WORKFLOW = "workflow";
    /** JSON field: {@code agent}. */
    public static final String AGENT = "agent";
    /** JSON field: {@code toolset}. */
    public static final String TOOLSET = "toolset";
    /** JSON field: {@code dimensions}. */
    public static final String DIMENSIONS = "dimensions";
    /** JSON field: {@code dry_run}. */
    public static final String DRY_RUN = "dry_run";
    /** JSON field: {@code metrics}. */
    public static final String METRICS = "metrics";
    /** JSON field: {@code metadata}. */
    public static final String METADATA = "metadata";

    // --- Query parameter names ---

    /** Query parameter: {@code include_children}. */
    public static final String INCLUDE_CHILDREN = "include_children";
    /** Query parameter: {@code limit}. */
    public static final String LIMIT = "limit";
    /** Query parameter: {@code cursor}. */
    public static final String CURSOR = "cursor";
    /** Query parameter: {@code status}. */
    public static final String STATUS = "status";

    // --- HTTP header names ---

    /** HTTP header: {@code X-Idempotency-Key}. */
    public static final String X_IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    /** HTTP header: {@code X-Cycles-API-Key}. */
    public static final String X_CYCLES_API_KEY_HEADER = "X-Cycles-API-Key";
}
