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

Requires Java 21+ and Spring Boot 3.3+.

### 2. Configure the connection

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

    @Cycles(
            actionKind = "llm.completion",
            actionName = "gpt-4",
            estimateExpression = "#p1 * 10",
            actualExpression = "#result.length() * 5"
    )
    public String generateText(String prompt, int tokens) {
        // Your LLM call here
        return callProvider(prompt, tokens);
    }
}
```

That's it. The aspect will automatically:
1. **Reserve** budget before the method runs (using the estimated amount)
2. **Execute** your method if the reservation is allowed
3. **Commit** the actual usage after the method completes
4. **Release** the reservation if the method throws an exception

## How It Works

### The Reserve / Commit / Release Lifecycle

```
┌─────────────────────────────────────────────────────────┐
│  @Cycles method invocation                              │
│                                                         │
│  1. Evaluate estimateExpression                         │
│  2. POST /v1/reservations  ──→  ALLOW / DENY            │
│     ├─ DENY  → throw CyclesProtocolException            │
│     └─ ALLOW → continue                                 │
│  3. Execute the guarded method                          │
│     ├─ Success → evaluate actualExpression              │
│     │            POST /v1/reservations/{id}/commit       │
│     └─ Failure → POST /v1/reservations/{id}/release     │
│  4. Heartbeat extends TTL for long-running methods      │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### Decisions

The Cycles server returns one of three decisions on reservation:

| Decision | Meaning |
|---|---|
| `ALLOW` | Budget available, reservation created |
| `ALLOW_WITH_CAPS` | Budget available with constraints (e.g., reduced token limits) |
| `DENY` | Insufficient budget, method will not execute |

When `ALLOW_WITH_CAPS` is returned, the `Caps` object is available via `CyclesContextHolder` inside your method (see [Accessing Caps](#accessing-caps-in-your-method)).

## Configuration Reference

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
| `actionKind` | Yes | — | Action category (e.g., `llm.completion`, `tool.search`) |
| `actionName` | Yes | — | Action identifier (e.g., `gpt-4`, `web.search`) |
| `estimateExpression` | Yes | — | SpEL expression for estimated cost |
| `actualExpression` | No | `""` | SpEL expression for actual cost (evaluated after method returns) |
| `actionTags` | No | `{}` | Policy tags (e.g., `{"prod", "customer-facing"}`) |
| `useEstimatedIfActualNotProvided` | No | `false` | Use estimate as actual when `actualExpression` is blank |
| `unit` | No | `USD_MICROCENTS` | Cost unit: `USD_MICROCENTS`, `TOKENS`, `CREDITS`, `RISK_POINTS` |
| `ttlMs` | No | `60000` | Reservation TTL in milliseconds (1,000–86,400,000) |
| `gracePeriodMs` | No | `-1` (server default) | Grace period for late commits (0–60,000 ms, server default: 5,000) |
| `overagePolicy` | No | `REJECT` | `REJECT`, `ALLOW_IF_AVAILABLE`, or `ALLOW_WITH_OVERDRAFT` |
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
| `#result` | Method return value (only available in `actualExpression`) |
| `#args` | All method arguments as an array |
| `#target` | The target object (the bean instance) |

Examples:
```java
// Estimate based on requested token count (2nd parameter)
estimateExpression = "#p1 * 10"

// Actual based on response length
actualExpression = "#result.length() * 5"

// Fixed estimate
estimateExpression = "1000"

// Use estimated as actual (no actualExpression needed)
useEstimatedIfActualNotProvided = true
```

## Accessing Caps in Your Method

When the server returns `ALLOW_WITH_CAPS`, you can read the constraints inside your guarded method:

```java
@Cycles(
        actionKind = "llm.completion",
        actionName = "gpt-4",
        estimateExpression = "#tokens * 10",
        actualExpression = "#result.length() * 5"
)
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
| `BUDGET_EXCEEDED` | 409 | Insufficient budget |
| `OVERDRAFT_LIMIT_EXCEEDED` | 409 | Debt exceeds overdraft limit |
| `DEBT_OUTSTANDING` | 409 | Outstanding debt blocks new reservations |
| `RESERVATION_EXPIRED` | 410 | Reservation TTL + grace period elapsed |
| `RESERVATION_FINALIZED` | 409 | Already committed or released |
| `UNIT_MISMATCH` | 400 | Commit unit differs from reservation unit |
| `IDEMPOTENCY_MISMATCH` | 409 | Same idempotency key, different payload |
| `UNAUTHORIZED` | 401 | Invalid or missing API key |
| `FORBIDDEN` | 403 | Tenant mismatch |
| `NOT_FOUND` | 404 | Reservation does not exist |
| `INTERNAL_ERROR` | 500 | Server error |

## Subject Field Resolution

Subject fields (`tenant`, `workspace`, `app`, `workflow`, `agent`, `toolset`) are resolved in order:

1. **Annotation value** — `@Cycles(tenant = "my-tenant")`
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

For long-running methods, the starter automatically extends the reservation TTL via the `/v1/reservations/{id}/extend` endpoint. The heartbeat fires at `ttlMs / 2` intervals to prevent the reservation from expiring while the method is still executing.

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
```

## Protocol Spec

This starter implements the [Cycles Protocol v0](https://github.com/runcycles/cycles-protocol/blob/main/cycles-protocol-v0.yaml) (v0.1.23).

## License

Apache 2.0
