package io.runcycles.client.java.spring.util;

/**
 * Shared constant values used across the Cycles client library.
 *
 * <p>Contains JSON field names for API request/response payloads, query parameter
 * keys, and HTTP header names.
 */
public class Constants {
    public static final String IDEMPOTENCY_KEY = "idempotency_key";
    public static final String UNIT = "unit";
    public static final String AMOUNT = "amount";
    public static final String TENANT = "tenant";
    public static final String WORKSPACE = "workspace";
    public static final String APP = "app";
    public static final String WORKFLOW = "workflow";
    public static final String AGENT = "agent";
    public static final String TOOLSET = "toolset";
    public static final String DIMENSIONS = "dimensions";
    public static final String DRY_RUN = "dry_run";
    public static final String METRICS = "metrics";
    public static final String METADATA = "metadata";

    public static final String INCLUDE_CHILDREN = "include_children";
    public static final String LIMIT = "limit";
    public static final String CURSOR = "cursor";
    public static final String STATUS = "status";

    public static final String X_IDEMPOTENCY_KEY_HEADER = "X-Idempotency-Key";
    public static final String X_CYCLES_API_KEY_HEADER = "X-Cycles-API-Key";
}
