# Cycles Spring Boot Starter

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

A Spring Boot starter that integrates with the [Cycles Budget Authority](https://github.com/runcycles/cycles-protocol) — a deterministic spend governance protocol for agent runtimes.

Cycles enforces budget reservations around guarded method executions using a **reserve / execute / commit** lifecycle. If the budget is exceeded, execution is denied before the guarded code runs.

## Quick Start

### 1. Add the dependency

```xml
<dependency>
    <groupId>io.runcycles</groupId>
    <artifactId>cycles-client-java-spring</artifactId>
    <version>0.1.0</version>
</dependency>
```

Requires Java 21+ and Spring Boot 3.3+. Designed for blocking Spring MVC workloads — not compatible with reactive WebFlux pipelines (see [Threading Model](#threading-model)).

### 2. Configure the connection

Add the following to your project's `application.yml`:

```yaml
cycles:
  api-key: your-api-key
  base-url: http://localhost:7878
  tenant: my-tenant
  workspace: development
  app: my-app
```

### 3. Annotate your method

```java
@Service
public class LlmService {

    // Minimal — just the estimate expression
    @Cycles("#tokens * 10")
    public String generateText(String prompt, int tokens) {
        return callProvider(prompt, tokens);
    }
}
```

That's it. The aspect will automatically:
1. **Reserve** budget before the method runs (using the estimated amount)
2. **Execute** your method if the reservation is allowed
3. **Commit** the actual usage after the method completes (defaults to estimated amount)
4. **Release** the reservation if the method throws an exception

Action is auto-derived: `actionKind` = class name (`LlmService`), `actionName` = method name (`generateText`). Override when you need explicit control:

```java
@Cycles(value = "#tokens * 10",
        actionKind = "llm.completion",
        actionName = "gpt-4",
        actual = "#result.length() * 5")
public String generateText(String prompt, int tokens) { ... }
```

## How It Works

### The Reserve / Commit / Release Lifecycle

```
┌─────────────────────────────────────────────────────────────────┐
│  @Cycles method invocation                                      │
│                                                                 │
│  1. Evaluate estimate expression → Amount(unit, amount)         │
│  2. POST /v1/reservations                                       │
│     ├─ 409 (BUDGET_EXCEEDED / OVERDRAFT / DEBT)                 │
│     │   → throw CyclesProtocolException (method never runs)     │
│     ├─ 200 ALLOW → reservation created, continue                │
│     └─ 200 ALLOW_WITH_CAPS → Caps available via context         │
│  3. Start heartbeat (POST .../extend at ttlMs/2 intervals)      │
│  4. Execute the guarded method                                  │
│     ├─ Success → evaluate actual expression                     │
│     │    POST /v1/reservations/{reservation_id}/commit          │
│     │            (retries on transient failure)                 │
│     └─ Failure → POST /v1/reservations/{reservation_id}/release │
│  5. Cancel heartbeat, clear CyclesContextHolder                 │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Decisions

The Cycles server returns one of three decisions on reservation:

| Decision | HTTP | Meaning |
|---|---|---|
| `ALLOW` | 200 | Budget available, reservation created |
| `ALLOW_WITH_CAPS` | 200 | Budget available with soft constraints (e.g., reduced token limits) |
| `DENY` | 409 | Insufficient budget — expressed as an HTTP 409 error with an `ErrorCode` |

Per the spec, budget denials for non-dry-run reservations are expressed as HTTP 409 responses (not a 200 with `decision=DENY`). The starter throws a `CyclesProtocolException` for all non-2xx responses.

When `ALLOW_WITH_CAPS` is returned, the `Caps` object is available via `CyclesContextHolder` inside your method (see [Accessing Caps](#accessing-caps-in-your-method)).

## Configuration Reference

All properties are configured in your project's `application.yml` (or `application.properties`).

### Connection Properties

```yaml
cycles:
  api-key: ""              # X-Cycles-API-Key header (required)
  base-url: ""             # Cycles server URL (required)

  # Default subject fields (can be overridden per-annotation)
  tenant: ""
  workspace: ""
  app: ""
  workflow: ""
  agent: ""
  toolset: ""

  # HTTP client settings
  http:
    connect-timeout: 2s
    read-timeout: 5s

  # Commit retry settings (exponential backoff)
  retry:
    enabled: true
    max-attempts: 5
    initial-delay: 500ms
    multiplier: 2.0
    max-delay: 30s
```

### `@Cycles` Annotation

| Parameter | Required | Default | Description |
|---|---|---|---|
| `value` / `estimate` | Yes (one) | — | SpEL expression for estimated cost. Synonyms — use either one. `value` enables `@Cycles("1000")` shorthand; `estimate` reads naturally with `actual`. |
| `actionKind` | No | class name | Action category (e.g., `llm.completion`, `tool.search`) |
| `actionName` | No | method name | Action identifier (e.g., `gpt-4`, `web.search`) |
| `actual` | No | `""` | SpEL expression for actual cost (evaluated after method returns) |
| `actionTags` | No | `{}` | Policy tags (e.g., `{"prod", "customer-facing"}`) |
| `useEstimateIfActualNotProvided` | No | `true` | Use estimate as actual when `actual` is blank |
| `unit` | No | `USD_MICROCENTS` | Cost unit: `USD_MICROCENTS`, `TOKENS`, `CREDITS`, `RISK_POINTS` |
| `ttlMs` | No | `60000` | Reservation TTL in milliseconds (1,000–86,400,000) |
| `gracePeriodMs` | No | `-1` (server default) | Grace period for late commits (0–60,000 ms, server default: 5,000) |
| `overagePolicy` | No | `REJECT` | `REJECT`, `ALLOW_IF_AVAILABLE`, or `ALLOW_WITH_OVERDRAFT` |
| `dryRun` | No | `false` | Shadow-mode: server evaluates without persisting; method does NOT execute |
| `dimensions` | No | `{}` | Custom Subject dimensions as `"key=value"` pairs |
| `tenant` | No | `""` | Override tenant (falls back to config, then resolver) |
| `workspace` | No | `""` | Override workspace |
| `app` | No | `""` | Override app |
| `workflow` | No | `""` | Override workflow |
| `agent` | No | `""` | Override agent |
| `toolset` | No | `""` | Override toolset |

### SpEL Expressions

Estimate and actual expressions are evaluated as SpEL with these variables available:

| Variable | Description |
|---|---|
| `#p0`, `#p1`, ... | Method parameters by index |
| `#paramName` | Method parameters by name (requires `-parameters` compiler flag) |
| `#result` | Method return value (only available in `actual`) |
| `#args` | All method arguments as an array |
| `#target` | The target object (the bean instance) |

Examples:
```java
// Minimal — fixed estimate, estimate used as actual
@Cycles("1000")

// Estimate from parameter
@Cycles("#tokens * 10")

// With explicit actual
@Cycles(value = "#p1 * 10", actual = "#result.length() * 5")
```

## Accessing Caps in Your Method

When the server returns `ALLOW_WITH_CAPS`, you can read the constraints inside your guarded method:

```java
@Cycles(value = "#tokens * 10",
        actual = "#result.length() * 5",
        actionKind = "llm.completion",
        actionName = "gpt-4")
public String generate(String prompt, int tokens) {
    CyclesReservationContext ctx = CyclesContextHolder.get();

    if (ctx.hasCaps()) {
        Caps caps = ctx.getCaps();

        // Reduce tokens if capped
        if (caps.getMaxTokens() != null) {
            tokens = Math.min(tokens, caps.getMaxTokens());
        }

        // Check if a tool is allowed
        if (!caps.isToolAllowed("web_search")) {
            // skip web search
        }
    }

    return callLlm(prompt, tokens);
}
```

Available `Caps` fields: `maxTokens`, `maxStepsRemaining`, `toolAllowlist`, `toolDenylist`, `cooldownMs`.

## Error Handling

When a reservation is denied or a protocol error occurs, a `CyclesProtocolException` is thrown:

```java
try {
    llmService.generateText(prompt, tokens);
} catch (CyclesProtocolException e) {
    ErrorCode code = e.getErrorCode();

    if (e.isBudgetExceeded()) {
        // Budget exhausted — show user a friendly message
    } else if (e.isOverdraftLimitExceeded()) {
        // Debt limit reached
    }

    // Also available:
    // e.getHttpStatus()
    // e.getReasonCode()
    // e.getMessage()
}
```

Error codes from the protocol:

| ErrorCode | HTTP | Meaning |
|---|---|---|
| `INVALID_REQUEST` | 400 | Malformed request (missing required fields, invalid values) |
| `UNAUTHORIZED` | 401 | Invalid or missing API key |
| `FORBIDDEN` | 403 | Tenant mismatch (subject.tenant vs effective tenant) |
| `NOT_FOUND` | 404 | Reservation does not exist |
| `BUDGET_EXCEEDED` | 409 | Insufficient budget for reservation or commit |
| `OVERDRAFT_LIMIT_EXCEEDED` | 409 | Debt exceeds overdraft limit, or scope is over-limit |
| `DEBT_OUTSTANDING` | 409 | Outstanding debt blocks new reservations |
| `RESERVATION_FINALIZED` | 409 | Reservation already committed or released |
| `IDEMPOTENCY_MISMATCH` | 409 | Same idempotency key with different payload |
| `UNIT_MISMATCH` | 400 | Commit unit differs from reservation unit |
| `RESERVATION_EXPIRED` | 410 | Reservation TTL + grace period elapsed |
| `INTERNAL_ERROR` | 500 | Server error |

## Subject Field Resolution

Subject fields (`tenant`, `workspace`, `app`, `workflow`, `agent`, `toolset`) are resolved in order:

1. **Annotation value** — `@Cycles(value = "1000", tenant = "my-tenant")`
2. **Configuration** — `cycles.tenant=my-tenant` in `application.yml`
3. **Dynamic resolver** — a Spring bean implementing `CyclesFieldResolver`

### Custom Resolver Example

Register a bean named after the field to resolve it dynamically at runtime:

```java
@Component("tenant")
public class TenantResolver implements CyclesFieldResolver {

    @Autowired
    private TenantService tenantService;

    @Override
    public String resolve() {
        return tenantService.getCurrentTenant();
    }
}
```

## Heartbeat (Automatic TTL Extension)

For long-running methods, the starter automatically extends the reservation TTL via the `/v1/reservations/{reservation_id}/extend` endpoint. The heartbeat fires at `ttlMs / 2` intervals to prevent the reservation from expiring while the method is still executing.

No configuration needed — it activates automatically when the server returns an `expires_at_ms` in the reservation response.

## Commit Retry

If a commit fails due to a transient error (network failure or 5xx), the starter automatically retries with exponential backoff using a background thread. Configure via:

```yaml
cycles:
  retry:
    enabled: true           # default: true
    max-attempts: 5          # default: 5
    initial-delay: 500ms     # default: 500ms
    multiplier: 2.0          # default: 2.0
    max-delay: 30s           # default: 30s
```

## Dry Run (Shadow Mode)

Use `dryRun = true` to evaluate a reservation without persisting it or locking budget. The guarded method will **not** execute — the aspect returns a `DryRunResult` immediately after the server responds.

```java
@Cycles(value = "#tokens * 10", dryRun = true)
public String checkBudget(String prompt, int tokens) {
    // This method body never executes in dry_run mode.
    // The aspect returns a DryRunResult after the server evaluates.
    return callLlm(prompt, tokens);
}
```

If the server returns `decision=DENY`, a `CyclesProtocolException` is thrown (consistent with non-dry-run behavior), allowing callers to use dry-run as a programmatic budget availability check. If the decision is `ALLOW` or `ALLOW_WITH_CAPS`, the aspect returns a `DryRunResult` and the method does not execute.

The `DryRunResult` contains the full server evaluation:

```java
Object result = myService.checkBudget(prompt, tokens);
if (result instanceof DryRunResult dryRun) {
    Decision decision = dryRun.getDecision();       // ALLOW or ALLOW_WITH_CAPS
    Caps caps = dryRun.getCaps();                    // soft constraints (if any)
    List<String> scopes = dryRun.getAffectedScopes();
    String scopePath = dryRun.getScopePath();
    Amount reserved = dryRun.getReserved();
    List<Balance> balances = dryRun.getBalances();   // current balances (if returned)
}
```

## Metrics on Commit

The starter automatically includes `latency_ms` (method execution time) in every commit. You can also set additional metrics inside your guarded method:

```java
@Cycles(value = "#tokens * 10",
        actual = "#result.length() * 5",
        actionKind = "llm.completion",
        actionName = "gpt-4")
public String generate(String prompt, int tokens) {
    CyclesReservationContext ctx = CyclesContextHolder.get();

    LlmResponse response = callLlm(prompt, tokens);

    // Report token counts and model version
    CyclesMetrics metrics = new CyclesMetrics();
    metrics.setTokensInput(response.getInputTokens());
    metrics.setTokensOutput(response.getOutputTokens());
    metrics.setModelVersion(response.getModelVersion());
    metrics.putCustom("cache_hit", response.isCacheHit());
    ctx.setMetrics(metrics);

    return response.getText();
}
```

## Metadata on Commit

Attach arbitrary key-value metadata to the commit request for audit/debugging:

```java
@Cycles(...)
public String process(String input) {
    CyclesReservationContext ctx = CyclesContextHolder.get();
    ctx.setCommitMetadata(Map.of("source", "batch-job", "batch_id", batchId));
    return doWork(input);
}
```

## Custom Dimensions

Attach custom dimensions to the Subject for enterprise taxonomies:

```java
@Cycles(value = "1000",
        dimensions = {"cost_center=engineering", "project=alpha"})
public String generate(String prompt) { ... }
```

## Optional Endpoints (Programmatic Use)

The `CyclesClient` interface exposes all optional protocol endpoints for programmatic use:

```java
@Autowired
private CyclesClient cyclesClient;

// Preflight decision check (no reservation created)
CyclesResponse<Map<String,Object>> decision = cyclesClient.decide(decideBody);

// List reservations with filters
CyclesResponse<Map<String,Object>> list = cyclesClient.listReservations(
        Map.of("status", "ACTIVE", "app", "my-app"));

// Get reservation detail
CyclesResponse<Map<String,Object>> detail = cyclesClient.getReservation(reservationId);

// Query balances
CyclesResponse<Map<String,Object>> balances = cyclesClient.getBalances(
        Map.of("tenant", "my-tenant", "workspace", "production"));

// Post-only accounting event (no reservation)
CyclesResponse<Map<String,Object>> event = cyclesClient.createEvent(eventBody);
```

Use `CyclesRequestBuilderService` to build request bodies for `decide` and `event` endpoints.

## Customization

All beans are created with `@ConditionalOnMissingBean`, so you can override any component:

```java
@Bean
public CyclesClient cyclesClient() {
    // Custom HTTP client implementation
    return new MyCyclesClient();
}

@Bean(name = "cyclesWebClient")
public WebClient cyclesWebClient() {
    // Custom WebClient with additional headers, interceptors, etc.
    return WebClient.builder()
            .baseUrl("https://cycles.example.com")
            .defaultHeader("X-Cycles-API-Key", "my-key")
            .build();
}

@Bean
public CommitRetryEngine retryEngine() {
    // Custom retry strategy (e.g., persistent queue)
    return new MyPersistentRetryEngine();
}
```

## Project Structure

```
cycles-spring-boot-starter/
├── cycles-client-java-spring/         # The starter library
│   └── src/main/java/io/runcycles/client/java/spring/
│       ├── annotation/                # @Cycles annotation
│       ├── aspect/                    # CyclesAspect (AOP interceptor)
│       ├── autoconfigure/             # Spring Boot auto-configuration
│       ├── client/                    # CyclesClient interface & HTTP impl
│       ├── config/                    # CyclesProperties
│       ├── context/                   # CyclesContextHolder, request builders
│       ├── evaluation/                # SpEL evaluator, field resolvers
│       ├── model/                     # Decision, Caps, ErrorCode, exceptions
│       ├── retry/                     # CommitRetryEngine
│       └── util/                      # Constants, validation
└── cycles-demo-client-java-spring/    # Demo application
    └── src/main/java/io/runcycles/demo/client/spring/
        ├── controller/
        │   ├── LlmController.java           # LLM endpoints with error handling
        │   └── DemoController.java          # Central demo REST API (/api/demo/*)
        ├── service/
        │   ├── LlmService.java              # @Cycles with context, metrics, metadata
        │   ├── AnnotationShowcaseService.java # Annotation variations (units, TTL, etc.)
        │   ├── ProgrammaticClientService.java # Direct CyclesClient usage
        │   └── EventService.java            # Standalone events (direct debit)
        ├── resolvers/
        │   └── CyclesTenantResolver.java    # Dynamic tenant via CyclesFieldResolver
        ├── error/
        │   └── CyclesExceptionHandler.java  # Global error handler
        └── resources/
            └── application.yml              # Demo configuration
```

## Demo Application

The demo app at `cycles-demo-client-java-spring/` showcases every major feature of the starter.

**Prerequisites:** You need a running Cycles stack with a tenant, API key, and budget. Follow the [deployment guide](https://docs.runcycles.io/quickstart/deploying-the-full-cycles-stack) to set up the `acme-corp` tenant used by the demo. Then:

```bash
cd cycles-demo-client-java-spring
export CYCLES_API_KEY=cyc_live_...   # paste the key from the deployment guide
mvn spring-boot:run
```

Start with the simplest endpoint:
```bash
curl -X POST http://localhost:7955/api/demo/annotation/minimal?input=hello
```

Hit `GET http://localhost:7955/api/demo/index` for a full listing of all endpoints with copy-paste curl commands.

Key demo scenarios:
- **`/api/llm/*`** — `@Cycles` annotation with `CyclesContextHolder`, `CyclesMetrics`, and `commitMetadata`
- **`/api/demo/annotation/*`** — Annotation variations: `unit=TOKENS`, `unit=CREDITS`, `overagePolicy`, `ttlMs`/`gracePeriodMs`, `dryRun`, `dimensions`, `workflow`/`agent`
- **`/api/demo/client/*`** — Programmatic `CyclesClient` usage: reserve/commit, reserve/release, preflight `decide()`, `getBalances()`, `listReservations()`
- **`/api/demo/events/*`** — Standalone events via `createEvent()` (direct debit without reservation)

## Protocol Spec Coverage

This starter implements the [Cycles Protocol v0](https://github.com/runcycles/cycles-protocol/blob/main/cycles-protocol-v0.yaml) (v0.1.23).

| Feature | Status | Notes |
|---|---|---|
| `POST /v1/reservations` (create) | Implemented | Core — via `@Cycles` annotation and `CyclesClient` |
| `POST /v1/reservations/{reservation_id}/commit` | Implemented | Core — automatic after guarded method returns |
| `POST /v1/reservations/{reservation_id}/release` | Implemented | Core — automatic on method failure |
| `POST /v1/reservations/{reservation_id}/extend` | Implemented | Core — automatic heartbeat |
| `POST /v1/decide` | Implemented | Programmatic via `CyclesClient.decide()` |
| `GET /v1/reservations` | Implemented | Programmatic via `CyclesClient.listReservations()` |
| `GET /v1/reservations/{reservation_id}` | Implemented | Programmatic via `CyclesClient.getReservation()` |
| `GET /v1/balances` | Implemented | Programmatic via `CyclesClient.getBalances()` |
| `POST /v1/events` | Implemented | Programmatic via `CyclesClient.createEvent()` |
| `dry_run` on reservation | Implemented | Via `@Cycles(dryRun = true)` — method does not execute |
| `metrics` on commit | Implemented | Auto `latency_ms`; user sets via `CyclesContextHolder` |
| `metadata` on requests | Implemented | User sets commit metadata via `CyclesContextHolder` |
| `X-Idempotency-Key` header | Implemented | Sent automatically on all POST requests |
| `Subject.dimensions` | Implemented | Via `@Cycles(dimensions = {"key=value"})` |

## Threading Model

`CyclesContextHolder` uses `ThreadLocal` to propagate reservation context to the guarded method. This works correctly with blocking Spring MVC but **does not work with reactive WebFlux pipelines**. Context will not propagate across reactive operator boundaries (e.g. `Mono.flatMap`, `Flux.map`), and calling `CyclesContextHolder.get()` from a scheduler thread will return `null`.

If you are using WebFlux, do not rely on `CyclesContextHolder` inside reactive chains. This is a known design constraint for v0 — the library targets Spring MVC and blocking Spring AI workloads.

## License

Apache 2.0
