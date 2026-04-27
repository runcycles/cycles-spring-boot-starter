# Cycles Protocol v0.1.23 — Client (Spring Boot Starter) Audit

**Date:** 2026-03-08
**Spec:** `cycles-protocol-v0.yaml` (OpenAPI 3.1.0, v0.1.23)
**Client:** `cycles-client-java-spring` (Spring Boot 3.3.5 / Java 21 / WebClient)
**Server audit:** See `cycles-server/AUDIT.md` (all passing)

---

## Summary

| Category | Pass | Issues |
|----------|------|--------|
| Endpoints & HTTP Methods | 9/9 | 0 |
| Request Schemas (field names & JSON keys) | 6/6 | 0 |
| Response Schemas (field names & JSON keys) | 10/10 | 0 |
| Enum Values | 5/5 | 0 |
| Nested Object Serialization | 6/6 | 0 |
| Auth Header (X-Cycles-API-Key) | — | 0 |
| Idempotency (header ↔ body sync) | — | 0 |
| Subject Validation | — | 0 |
| Response Header Capture | — | 1 |
| Client-Side Spec Constraint Validation | — | 1 |
| Default Value Handling (typed DTOs) | — | 1 |

**Overall: Client is protocol-conformant.** All endpoints, schemas, field names, JSON keys, and enum values match the OpenAPI spec and server implementation exactly. Three non-critical gaps identified (response headers, client-side validation, default handling).

---

## Audit Scope

Compared the following across all three artifacts (spec YAML, server Java, client Java):
- All 9 endpoint paths, HTTP methods, and path/query parameters
- All 6 request DTO `toMap()` serializations vs spec schemas
- All 10 response DTO `fromMap()` deserializations vs spec schemas
- All 5 enum types and their values
- Nested object serialization (Subject, Action, Amount, SignedAmount, Caps, StandardMetrics)
- Auth and idempotency header handling
- Subject constraint validation (`anyOf` / at least one standard field)
- Annotation-based request building (`CyclesRequestBuilderService`)
- Lifecycle orchestration (`CyclesLifecycleService`)

---

## PASS — Correctly Implemented

### Endpoints (all 9 match spec and server)

| Spec Endpoint | Client Path | HTTP Method | Match |
|---|---|---|---|
| `/v1/decide` | `/v1/decide` | POST | PASS |
| `/v1/reservations` (create) | `/v1/reservations` | POST | PASS |
| `/v1/reservations` (list) | `/v1/reservations` | GET | PASS |
| `/v1/reservations/{reservation_id}` | `/v1/reservations/{reservation_id}` | GET | PASS |
| `/v1/reservations/{reservation_id}/commit` | `/v1/reservations/{reservation_id}/commit` | POST | PASS |
| `/v1/reservations/{reservation_id}/release` | `/v1/reservations/{reservation_id}/release` | POST | PASS |
| `/v1/reservations/{reservation_id}/extend` | `/v1/reservations/{reservation_id}/extend` | POST | PASS |
| `/v1/balances` | `/v1/balances` | GET | PASS |
| `/v1/events` | `/v1/events` | POST | PASS |

### Request Schemas (all match spec JSON keys)

**ReservationCreateRequest** — spec required: `[idempotency_key, subject, action, estimate]`
- `toMap()` keys: `idempotency_key`, `subject`, `action`, `estimate`, `ttl_ms`, `grace_period_ms`, `overage_policy`, `dry_run`, `metadata` — all snake_case, all match spec

**CommitRequest** — spec required: `[idempotency_key, actual]`
- `toMap()` keys: `idempotency_key`, `actual`, `metrics`, `metadata` — all match spec

**ReleaseRequest** — spec required: `[idempotency_key]`
- `toMap()` keys: `idempotency_key`, `reason` — all match spec

**DecisionRequest** — spec required: `[idempotency_key, subject, action, estimate]`
- `toMap()` keys: `idempotency_key`, `subject`, `action`, `estimate`, `metadata` — all match spec

**EventCreateRequest** — spec required: `[idempotency_key, subject, action, actual]`
- `toMap()` keys: `idempotency_key`, `subject`, `action`, `actual`, `overage_policy`, `metrics`, `client_time_ms`, `metadata` — all match spec

**ReservationExtendRequest** — spec required: `[idempotency_key, extend_by_ms]`
- `toMap()` keys: `idempotency_key`, `extend_by_ms`, `metadata` — all match spec

### Response Schemas (all match spec JSON keys)

| Spec Schema | Client Class | JSON Keys | Match |
|---|---|---|---|
| `ReservationCreateResponse` | `ReservationResult` | `decision`, `reservation_id`, `affected_scopes`, `expires_at_ms`, `scope_path`, `reserved`, `caps`, `reason_code`, `retry_after_ms`, `balances` | PASS |
| `CommitResponse` | `CommitResult` | `status`, `charged`, `released`, `balances` | PASS |
| `ReleaseResponse` | `ReleaseResult` | `status`, `released`, `balances` | PASS |
| `DecisionResponse` | `DecisionResult` | `decision`, `caps`, `reason_code`, `retry_after_ms`, `affected_scopes` | PASS |
| `EventCreateResponse` | `EventResult` | `status`, `event_id`, `balances` | PASS |
| `ReservationExtendResponse` | `ExtendResult` | `status`, `expires_at_ms`, `balances` | PASS |
| `BalanceResponse` | `BalanceQueryResult` | `balances`, `has_more`, `next_cursor` | PASS |
| `ReservationDetail` | `ReservationDetailResult` | `reservation_id`, `status`, `idempotency_key`, `subject`, `action`, `reserved`, `committed`, `created_at_ms`, `expires_at_ms`, `finalized_at_ms`, `scope_path`, `affected_scopes`, `metadata` | PASS |
| `ReservationSummary` | `ReservationSummaryResult` | `reservation_id`, `status`, `idempotency_key`, `subject`, `action`, `reserved`, `created_at_ms`, `expires_at_ms`, `scope_path`, `affected_scopes` | PASS |
| `ReservationListResponse` | `ReservationListResult` | `reservations`, `has_more`, `next_cursor` | PASS |

### Nested Object Schemas (all match)

| Spec Schema | Client Class | JSON Keys | Match |
|---|---|---|---|
| `Subject` | `Subject` | `tenant`, `workspace`, `app`, `workflow`, `agent`, `toolset`, `dimensions` | PASS |
| `Action` | `Action` | `kind`, `name`, `tags` | PASS |
| `Amount` | `Amount` | `unit`, `amount` | PASS |
| `SignedAmount` | `SignedAmount` | `unit`, `amount` | PASS |
| `Caps` | `Caps` | `max_tokens`, `max_steps_remaining`, `tool_allowlist`, `tool_denylist`, `cooldown_ms` | PASS |
| `StandardMetrics` | `CyclesMetrics` | `tokens_input`, `tokens_output`, `latency_ms`, `model_version`, `custom` | PASS |
| `Balance` | `Balance` | `scope`, `scope_path`, `remaining` (SignedAmount), `reserved`, `spent`, `allocated`, `debt`, `overdraft_limit`, `is_over_limit` | PASS |
| `ErrorResponse` | `ErrorResponse` | `error`, `message`, `request_id`, `details` | PASS |

### Enum Values (all match spec)

| Spec Enum | Client Enum | Values | Match |
|---|---|---|---|
| `DecisionEnum` | `Decision` | `ALLOW`, `ALLOW_WITH_CAPS`, `DENY` | PASS |
| `UnitEnum` | `Unit` | `USD_MICROCENTS`, `TOKENS`, `CREDITS`, `RISK_POINTS` | PASS |
| `CommitOveragePolicy` | `CommitOveragePolicy` | `REJECT`, `ALLOW_IF_AVAILABLE`, `ALLOW_WITH_OVERDRAFT` | PASS |
| `ReservationStatus` | `ReservationStatus` | `ACTIVE`, `COMMITTED`, `RELEASED`, `EXPIRED` | PASS |
| `ErrorCode` | `ErrorCode` | All 12 spec values + `UNKNOWN` (client fallback) | PASS |

Note: Client `ErrorCode` adds `UNKNOWN` as a fallback for unrecognized server error codes. This is a client-side convenience and does not violate the spec.

### Auth & Idempotency (correct)

- **X-Cycles-API-Key**: Set on all requests via `WebClient` base configuration in `CyclesAutoConfiguration`
- **X-Idempotency-Key**: Extracted from request body `idempotency_key` field and set as header via `DefaultCyclesClient.extractIdempotencyKey()`. Supports both `Map` bodies (direct key lookup) and POJO bodies (reflection-based `getIdempotencyKey()` getter). Header and body values always match (copied from body to header), satisfying the spec rule: "If X-Idempotency-Key header is present and body.idempotency_key is present, they MUST match."

### Subject Validation (correct)

- `CyclesRequestBuilderService.validateMandatory()` requires `Subject.tenant` to be non-blank (spec: tenant MUST be validated against effective tenant)
- `CyclesRequestBuilderService.validateMandatory()` requires at least one standard field (spec: `anyOf` constraint)
- `Subject.hasAtLeastOneStandardField()` checks all 6 standard fields
- Field length validation: enforces `maxLength: 128` for all Subject fields (spec constraint)

### Annotation-Based Request Building (correct)

- `CyclesRequestBuilderService` validates:
  - `ttl_ms` range: 1000–86400000 (matches spec `minimum: 1000, maximum: 86400000`)
  - `grace_period_ms` range: 0–60000 (matches spec `minimum: 0, maximum: 60000`)
  - `extend_by_ms` range: 1–86400000 (matches spec `minimum: 1, maximum: 86400000`)
  - `overage_policy` against spec enum values
  - `unit` against spec enum values
  - `Amount >= 0` (matches spec `minimum: 0`)
  - `Action.kind` maxLength: 64, `Action.name` maxLength: 256

### Lifecycle Orchestration (correct)

- Reserve → Execute → Commit flow with proper cleanup (release on failure)
- Heartbeat-based TTL extension using `extend` endpoint
- Commit retry engine for transient failures (transport errors, 5xx)
- Dry-run handling returns `DryRunResult` without requiring commit/release
- `DENY` decision correctly raises `CyclesProtocolException`
- `ALLOW_WITH_CAPS` correctly propagates `Caps` via `CyclesReservationContext`

### HTTP Status Code Handling (correct)

- `is2xx()` correctly handles both 200 (most endpoints) and 201 (events)
- Error responses parsed via `ErrorResponse.fromMap()` for structured error extraction
- `ErrorCode` mapped from response body `error` field

---

## Issues Found

### Issue 1 (LOW): Response headers not captured

**Spec:** All endpoints define response headers `X-Request-Id` and `X-Cycles-Tenant`. `/v1/decide` additionally returns `X-RateLimit-Remaining` and `X-RateLimit-Reset`.

**Client:** `DefaultCyclesClient.exchangeAndMap()` only extracts the response body and HTTP status code. Response headers are discarded:

```java
// DefaultCyclesClient.java:137-158
private CyclesResponse<Map<String, Object>> exchangeAndMap(WebClient.RequestHeadersSpec<?> request) {
    return request.exchangeToMono(response ->
            response.bodyToMono(MAP_TYPE)
                    .defaultIfEmpty(Map.of())
                    .map(responseBody -> {
                        int status = response.statusCode().value();
                        // ← response headers not captured
                        ...
                    })
    ).block();
}
```

**Impact:** Callers cannot access `X-Request-Id` for log correlation or `X-Cycles-Tenant` for tenant verification. Not a protocol violation (headers are informational), but reduces observability.

**Recommendation:** Add `Map<String, String> responseHeaders` to `CyclesResponse` and populate it from `response.headers()`.

---

### Issue 2 (LOW): Typed DTOs don't validate spec constraints

**Spec:** Defines constraints on request fields:
- `IdempotencyKey`: `minLength: 1, maxLength: 256`
- `Subject` fields: `maxLength: 128`
- `Action.kind`: `maxLength: 64`, `Action.name`: `maxLength: 256`
- `Amount.amount`: `minimum: 0`
- `ttl_ms`: `minimum: 1000, maximum: 86400000`
- `grace_period_ms`: `minimum: 0, maximum: 60000`
- `extend_by_ms`: `minimum: 1, maximum: 86400000`

**Client:** The annotation-based path (`CyclesRequestBuilderService`) validates most of these. However, the typed DTO builders (`ReservationCreateRequest.Builder`, `CommitRequest.Builder`, etc.) perform no validation. A caller using:

```java
ReservationCreateRequest.builder()
    .ttlMs(-1L)          // violates minimum: 1000
    .gracePeriodMs(999999L) // violates maximum: 60000
    .build();
```

...would produce a request that the server rejects with 400.

**Impact:** Late error detection (server-side 400 instead of client-side exception). Not a protocol violation since the server enforces constraints.

**Recommendation:** Add validation in `build()` methods or document that typed DTOs require caller validation.

---

### Issue 3 (LOW): Typed DTO builders always emit spec-default values

**Spec:** Defines defaults for several fields:
- `ttl_ms` default: `60000`
- `grace_period_ms` default: `5000`
- `overage_policy` default: `ALLOW_IF_AVAILABLE`
- `dry_run` default: `false`

**Client:** The `ReservationCreateRequest.Builder` initializes these to spec defaults:

```java
private Long ttlMs = 60000L;
private Long gracePeriodMs = 5000L;
private CommitOveragePolicy overagePolicy = CommitOveragePolicy.ALLOW_IF_AVAILABLE;
private Boolean dryRun = false;
```

Since `toMap()` includes all non-null fields, requests built via the typed DTO always include `ttl_ms`, `grace_period_ms`, `overage_policy`, and `dry_run` — even when they match the server's defaults. The annotation-based path (`CyclesRequestBuilderService.buildReservation()`) correctly omits `overage_policy` when `ALLOW_IF_AVAILABLE` and `dry_run` when `false`.

**Impact:** Slightly verbose request payloads. Not a protocol violation (server accepts explicit defaults).

**Recommendation:** In `toMap()`, skip fields that match spec defaults, or change builder defaults to `null` and let the server apply defaults.

---

## Verdict

The client is **fully protocol-conformant** with the Cycles Protocol v0.1.23 OpenAPI spec. All 9 endpoints, 6 request schemas, 10 response schemas, 5 enum types, and all nested object serializations match the spec exactly. JSON field names use correct snake_case mapping throughout. Auth headers, idempotency handling, and subject validation all follow spec normative rules.

Three low-severity gaps identified (response header capture, typed DTO validation, default value handling) — none are protocol violations; all are quality-of-life improvements for client consumers.

---

## Changelog

### 2026-04-27 — Issue #49: SpEL on `@Cycles` subject fields + BeanPostProcessor warning

- `CyclesExpressionEvaluator.evaluateString` resolves SpEL on `@Cycles` subject fields (`tenant`, `workspace`, `app`, `workflow`, `agent`, `toolset`) when the annotation value's first non-whitespace character is `#`. Literal values (e.g. `workspace = "production"`) bypass the parser and remain unchanged — fully backward-compatible. `#result` is intentionally not exposed because subject fields are resolved before the guarded method runs.
- `CyclesRequestBuilderService` gains a context-aware `buildReservation` overload `(Cycles, long, String, String, Map, Method, Object[], Object)` that the AOP path uses. The existing 5-arg overload delegates with `null` invocation context (literal-only), so programmatic callers (`buildDecision`, `buildEvent`) keep their previous semantics.
- `CyclesAutoConfiguration#cyclesSelfInvocationDetector` is now `static`, eliminating the Spring startup warning *"Bean ... is not eligible for getting processed by all BeanPostProcessors"* and ensuring the configuration class is properly post-processed.
- New tests: `CyclesExpressionEvaluatorTest.EvaluateString` (literal/null/blank/`#var`/nested/safe-nav/non-String/leading-whitespace) and `CyclesRequestBuilderServiceTest.BuildReservationSpel` (end-to-end resolution of `@Cycles(workspace = "#workspaceId")` with fallback to resolver on null).
