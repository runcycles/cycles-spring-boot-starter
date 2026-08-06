# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/).

## [0.3.3] - 2026-08-06

### Fixed

- `CyclesLifecycleService` no longer releases a reservation after a recognized
  terminal commit rejection. The guarded method has already spent the
  resource, so returning its reserved budget would undercount known spend.
- Release is now limited to exceptions thrown by the guarded method itself.
  Unexpected post-action settlement failures surface without releasing known
  spend.
- Missing required `actual` configuration fails before reservation or method
  execution. If an explicit actual expression fails after the method returns,
  the client commits the estimate and marks `metadata.actual_source=estimate`.

### Tests and docs

- Regression coverage pins preflight actual validation, safe actual-expression
  fallback, and no-release behavior for terminal commit rejection.
- README lifecycle guidance now distinguishes method failure from post-action
  settlement failure.

## [0.3.2] - 2026-07-29

### Added

- Bind all shared durable-recovery and guarantee-boundary scenarios from the
  protocol repository into pull-request and release CI.
- Expose the optional commit `cycles_evidence` reference through
  `CommitResult`.

### Fixed

- Persist known actual usage before the first commit request and accept only
  exact HTTP 200/201 schema-valid commit/event responses as terminal success.
- Use `v2-<sha256(exact UTF-8 reservation id)>.json` journal filenames, safely
  migrate matching legacy records, and preserve collision-free cross-SDK
  replay.
- Retain durable settlement records for contradictory retryable 4xx envelopes,
  and report heartbeat transport failures with their same-key retry or stop
  disposition.
- Quarantine corrupt, semantically invalid, and unsupported-version journal
  records without blocking replay of valid work, and report exact native test
  evidence from the shared recovery-conformance adapter.

## [0.3.1] - 2026-07-27

### Fixed

- **Final lease-response and timing conformance (supersedes the older any-2xx wording below).** Both server-authoritative and fieldless fallback scheduling count only a complete, schema-valid HTTP 200 create/extend response as success; malformed or non-200 2xx responses remain ambiguous and are recovered with the same idempotency key. Create performs one same-key recovery attempt, the enforced timeout covers the whole attempt, post-receipt setup time is deducted from the first delay, and reliable RTT samples gathered before a rolling-upgrade server starts sending `remaining_ttl_ms` remain part of the safety budget.
- **Heartbeat extend drift (P1 liveness).** Per the protocol spec, `extend_by_ms` is relative to the *current* `expires_at_ms` and the server caps `extension_count` (`max_extensions`, default 10). The heartbeat fired every `ttl/2` and extended by `ttl` on every tick, so expiry drifted ahead of the client by `+ttl/2` per beat — on process death the reservation could outlive the client by many minutes, locking up budget as a zombie — and burned the capped extension allowance twice as fast as needed. `CyclesLifecycleService` now uses a conservative **lead lower bound**: each beat computes `leadMinMs = grantsSum − elapsedMs`, where `grantsSum` is the sum of *observed* grants (differences of successive returned `expires_at_ms` values — same server frame, so skew-free) and `elapsedMs` is a difference of two client-monotonic (`System.nanoTime`) readings; no client wall clock is ever compared with the server's. The bound starts at 0 (nothing proven yet), so the first beat always extends, and it only ever *understates* the true lead, so skipping on it can never cause a lapse: a beat skips only when `leadMinMs ≥ 1.5 × lastGrant`, and otherwise extends by the **requested** `ttlMs` (the server clamps to what policy allows; the returned `expires_at_ms` reveals the actual grant). Beats are **one-shot and self-rescheduling** (`schedule`, not `scheduleAtFixedRate`) so the cadence adapts to the observed grant — after a success the next delay is `clamp(grant/2, 500, requestedTtl/2)` (lead-clamped grants excepted — see the fallback entry below; when the response carries `remaining_ttl_ms` the server-authoritative schedule in the next entry replaces all of this) — and the fixed-rate catch-up hazard disappears by construction (no queue of missed ticks to fire back-to-back). A failed extend (non-2xx or exception) **keeps its idempotency key** and replays it on the next beat at the current delay, so a lost response cannot double-extend; the key is regenerated only after a schema-valid success. Malformed or non-200 2xx responses remain ambiguous and retain the same key. **Permanent failures now stop the heartbeat for good** instead of retrying forever: HTTP 410 (even bodyless) or `RESERVATION_EXPIRED` / `RESERVATION_FINALIZED` / `MAX_EXTENSIONS_EXCEEDED` / `TENANT_CLOSED` / `NOT_FOUND` — all irreversible — cancel the scheduled task. The 1000ms interval floor is removed — spec `ttl_ms` minimum is 1000, so the steady-state delay is at least 500ms; the old floor guaranteed a lapse window for TTLs in (1000, 2000). Net effect: no drift, fewer extensions in the steady state, and when the server clamps grants the client tightens its cadence and keeps extending to fight for liveness. The context's `expiresAtMs` is still refreshed from each extend response.
- **Server-authoritative heartbeat scheduling via `remaining_ttl_ms` (spec PR #148).** Round-5 spec review concluded that regime detection from (grant, elapsed) alone is **formally undecidable** — sticky counterexample: ttl=24s with genuine +10s per-extend grants induces a held cadence of 12s, so every post-skip grant/elapsed ratio is 10/12 ≈ 0.833, inside [0.75, 1.25] forever, while the lease erodes to a lapse — so the protocol adds `remaining_ttl_ms` (integer int64 ≥ 0) to both `ReservationCreateResponse` and `ReservationExtendResponse`: the remaining reservation lifetime in ms at response evaluation, same clock snapshot as `expires_at_ms`, present on successful live-reservation responses (absent on dry-run/DENY; older servers omit it). The client parses it on create (`ReservationResult.getRemainingTtlMs()`) and extend (`ExtendResult.getRemainingTtlMs()`) and implements the spec's **primary algorithm** (settled at PR #148 head dd60c27 — the HEARTBEAT GUIDANCE block in `cycles-protocol-v0.yaml` is the authority), normative whenever the field is present. *Success predicate:* only a **schema-valid HTTP 200** `ReservationExtendResponse` (`status=ACTIVE` + `expires_at_ms`) — or the initial `ReservationCreateResponse` — counts as an observed success in field mode; any other or malformed 2xx is **ambiguous** and is treated as a transient failure with same-key recovery, never as applied. The fieldless fallback uses the same strict success predicate; only its heartbeat scheduling heuristic differs. *Scheduling:* per-attempt monotonic `rtt` (rounded up; max observed tracked per heartbeat), `leadFloor = max(0, remaining_ttl_ms − rtt)`; `attemptBudget = max(requestTimeoutBudget, 1s, 2×maxRtt)` where `requestTimeoutBudget` is the client's **enforced** finite per-attempt timeout — the autoconfiguration wires `cycles.http.connect-timeout + cycles.http.read-timeout`, the bound Reactor Netty actually enforces; unknown/unbounded makes the budget +∞ and every field-mode delay 0; `safetyMargin = max(1s, 2×maxRtt)`; `retryReserve = 2×attemptBudget + safetyMargin`; `nextDelay = max(0, leadFloor − retryReserve)` — recomputed from *every* schema-valid response, scheduled from response receipt, with overflow-safe ms arithmetic (positive overflow saturates, never wraps) and rounding that can never consume the margin (budgets/margins up, leads/delays down). Expiry differences are never accumulated in this mode, and the `leadMin` skip check is **bypassed** (scheduling is exact; a heuristic skip could overshoot the real lease). The first beat derives from the CREATE response's field the same way — no 0ms prime, no wasted primed extension under max-lead clamping, no lead-clamp WARN; CREATE same-key replays are safe to schedule from because the server recomputes `remaining_ttl_ms` at replay-response construction, and rtt is always the individual attempt's own timing. *Zero-delay guard:* a schema-valid success producing `nextDelay = 0` permits **one** immediate fresh attempt (new idempotency key); if that success also yields 0 the heartbeat stops and surfaces that the lease is shorter than the retry-safety budget — unknown timing or timeout must not be treated as zero elapsed time and must not downgrade the client to the fieldless fallback. *Recovery* (timeout/connection/5xx/429/ambiguous-2xx): `currentLeadEstimate = max(0, lastLeadFloor − elapsed since the schema-valid response that established it)` and `retryWindow = currentLeadEstimate − attemptBudget − safetyMargin`, deliberately **unclamped**; `retryWindow < 0` proves no complete retry plus margin fits — stop and surface, never retry; otherwise a non-429 failure retries the **same key** after `min(30s, currentLeadEstimate/4, retryWindow)`, and a 429 retries at exactly `1000 × Retry-After` (delta-seconds, overflow-safe) only when it fits the window — missing/invalid/exceeding stops rather than inventing an earlier retry that violates throttling. Recovery may repeat: lead and window are recomputed from the same last schema-valid response after **every** failed attempt; `retryWindow = 0` permits one immediate retry then stops, and a progress guard stops the loop when neither elapsed nor window moves between consecutive failures. Any **other 4xx** stops and surfaces the request/authorization failure without rotating the idempotency key. The grants/`leadMin` bookkeeping keeps running underneath, so the moment a schema-valid response lacks the field (downgraded server, stripping proxy) the heuristic below takes over on that very success. The band heuristic (next entry) is thereby demoted to a **best-effort fallback** for servers that do not emit the field, valid only against per-extend-delta clamping; the sticky window `grant ∈ [0.75×min(ttl/2, 30s), 0.9×ttl)` is exactly why the server-authoritative field exists.
- **Heartbeat honors tenant-capped TTLs (immediate first beat) and detects lead-clamped expiry (v2.3 — fallback path when `remaining_ttl_ms` is absent).** Tenant policy `max_reservation_ttl_ms` silently *caps* the granted TTL at reserve time (governance default 1 hour) and the create response carries no effective-TTL field — spec review round 4 confirmed that **any bounded first-beat delay can outlive a small capped lease** (even a 30s delay lapses a 10s cap before the first extend). The first one-shot beat now fires **immediately (~0ms)**: one cheap extend that primes the grant observation which drives all later cadence. This supersedes v2.2's Date-derived first-beat hint, which is removed from the heartbeat path — it was cross-clock arithmetic on a best-effort header, and delay 0 dominates it anyway (`DefaultCyclesClient` still captures the `Date` header and `CyclesResponse.getDateMs()` remains as a general accessor). A transiently failed first extend retries at the held cadence `min(requestedTtl/2, 30s)` — never at 0, which would hot-loop against a down or erroring server — replaying the same idempotency key. Round 4 also identified the **maximum-LEAD clamp regime**: when a depleting budget holds expiry at ≈ `now + L`, successive `expires_at_ms` differences measure *elapsed time*, not granted lease — feeding them into `grant/2` would collapse the cadence to the 500ms floor and burn the capped `max_extensions` in seconds. Grant-derived cadence is only valid for real per-extend grants, so each success compares the observed grant with the elapsed time since the previous success (first: since heartbeat start): a grant that is ≤ 0, or both < 0.9× the requested ttl *and* inside the elapsed **band** (0.75×elapsed ≤ grant ≤ 1.25×elapsed), marks the lead-clamp regime — the cadence **holds** at `min(requestedTtl/2, 30s)`, never tightens, and a single WARN per heartbeat flags the likely budget depletion; only `max(grant, 0)` is credited to `grantsSum`/`lastGrant`, keeping the lead bound conservative. The band needs *both* bounds (a sticky misclassification caught by the Rust port and fixed across the SDK fleet): after a `leadMin` skip the next grant arrives across a doubled gap, so a genuine grant-clamped server (cadence is grant/2) shows grant ≈ elapsed exactly once — an upper-bound-only test would lock it into the hold, where a clamped lease banks less than elapsed per cycle and decays to a lapse. With the band, a post-skip real grant lands in the hold at most once; at the held cadence its grant/elapsed ratio falls to ~0.5, exits the band, and the cadence re-tightens, while a genuine maximum-lead clamp tracks *any* gap (ratio ≈ 1) and stays held. Genuine policy-capped grants (real lease per extend, e.g. 4s granted on a 20s request) still tighten the cadence normally. Correctness — never lapsing while skipping beats — continues to come entirely from the `leadMin` lower bound above.
- **Estimate committed as actual is now marked.** When `@Cycles(actual = ...)` is not set and `useEstimateIfActualNotProvided` is `true` (the default), the commit silently recorded the *estimate* as measured spend. That fallback branch now merges `"actual_source": "estimate"` into the commit metadata (creating the map when absent) and logs the substitution at DEBUG, so server-side records can distinguish measured actuals from estimate-as-actual commits. The marker also flows through to the `POST /v1/events` recovery body, which copies commit metadata. Defaults are unchanged; commits with an explicit `actual` expression carry no marker.

## [0.3.0] - 2026-07-27

### Added

- **Durable commit retries (journal + `/v1/events` fallback).** Ports the design shipped in cycles-client-python v0.5.0 ([runcycles/cycles-client-python#89](https://github.com/runcycles/cycles-client-python/pull/89)) and cycles-client-typescript v0.4.0 (#172), with a byte-compatible on-disk journal format. A commit records spend that already happened, so failed commits are now journaled to disk (`~/.runcycles/commit-journal/<identity-fingerprint>/`, one JSON file per pending commit, atomic temp-file writes) *before* background retries start, and the entry is removed only on a terminal outcome. On the next JVM start the new `JournaledCommitRetryEngine` (autoconfigured; `InMemoryCommitRetryEngine` is deprecated) replays surviving entries once per identity — commit first (idempotent), falling back to `POST /v1/events` with `recovered_reservation_id`/`recovery_reason` metadata when the reservation expired (`RESERVATION_EXPIRED`). Journal identity is a truncated PBKDF2-HMAC-SHA256 fingerprint of (base-url, tenant-or-api-key), so clients sharing a directory but using different servers or credentials never replay each other's records, and API-key rotation survives when a tenant is configured.
- Per-attempt outcome classification (identical to the Python/TypeScript engines): 429/`LIMIT_EXCEEDED` is transient — the server's `Retry-After` becomes the floor for the next delay and is persisted as `not_before_ms` so a restart mid-wait honors it (all `Retry-After` values are parsed as longs, negatives rejected, and clamped to 1 hour so a hostile or mangled header cannot park a retry for days); 401/403 is terminal for the run but the journal entry is retained (fix credentials and restart to replay); a 4xx with a *recognized* non-`UNKNOWN` `ErrorCode` is a genuine rejection and discards the entry, while a codeless or `UNKNOWN`-code 4xx is terminal for the run but retains the entry (this client version cannot prove it is a genuine rejection); `RESERVATION_EXPIRED` — or any bare 410, even bodyless — flips to the `/v1/events` fallback; transport/5xx keeps retrying and retains the entry on exhaustion. On shutdown, `destroy()` also releases the engine's once-per-JVM replay claim so a same-JVM context restart can resume replay, and a flush timeout in memory-only mode (journal disabled) is logged at ERROR since the in-flight retries are dropped for good.
- `CyclesLifecycleService` first-attempt commit handling now mirrors the same rules: 429 (including bodyless) schedules a retry carrying the `Retry-After`; 401/403 schedules for replay and **never releases**; `RESERVATION_EXPIRED` recovers the spend via `POST /v1/events` instead of dropping it; genuine 4xx still releases.
- New configuration: `cycles.journal.enabled` (default `true`), `cycles.journal.dir` (default `~/.runcycles/commit-journal`), and `cycles.retry.flush-timeout` (default `10s`) — the bounded shutdown wait (`DisposableBean`) for in-flight retries; unfinished work stays journaled.
- `CommitRetryEngine` extended with `schedule(reservationId, commitBody, eventFallbackBody, retryAfterMs)`, `scheduleEvent(reservationId, eventBody)`, and `flush(timeout)`. **Breaking for implementors:** external classes that implement `CommitRetryEngine` must implement these three new abstract methods (a source- and binary-incompatible change for them); only *callers* are unaffected — the old two-arg `schedule` remains as a default method for caller backward compatibility, and it now converts non-`Map` (POJO) bodies to a map via Jackson so they are journaled and retried intact, passing `null` only as a last resort when conversion fails.
- `CyclesResponse.getRetryAfterMs()`: `DefaultCyclesClient` now captures the `Retry-After` header (integer seconds per spec, converted to milliseconds) on non-2xx responses; parsing rejects negatives and clamps the result to 1 hour.
- `CommitJournal` atomic writes fall back to a plain `REPLACE_EXISTING` move only on `AtomicMoveNotSupportedException`; any other move failure propagates to the journal's existing best-effort failure handling (temp-file cleanup + warning) instead of being retried non-atomically.
- Add two additive `ErrorCode` enum constants to `cycles-client-java-spring`: `LIMIT_EXCEEDED` (HTTP 429 server-side throttling with `Retry-After`, added to the runtime `ErrorCode` enum in `cycles-protocol-v0.yaml` revision v0.1.25.12) and `TENANT_CLOSED` (HTTP 409 permanent denial when the owning tenant is CLOSED — also surfaced as `reason_code` on dry-run/decide DENYs — added in revision v0.1.25.13). Enum order mirrors the spec: `… MAX_EXTENSIONS_EXCEEDED, LIMIT_EXCEEDED, TENANT_CLOSED, INTERNAL_ERROR`.

### Fixed

- Failed commits no longer vanish on JVM exit or silently drop when retries are disabled — with retries disabled the pending commit is still journaled for replay on the next run; only when the journal is *also* disabled is the old drop-with-warning behavior kept.
- Retry semantics for `TENANT_CLOSED`: `ErrorCode.isRetryable()` now classifies `LIMIT_EXCEEDED` as retryable (transient 429 throttling) and `TENANT_CLOSED` as non-retryable (permanent 409). Previously both codes were unrecognized and fell through to `UNKNOWN`, which `isRetryable()` treats as retryable — so `TENANT_CLOSED` was incorrectly retryable. This corrects that classification. `LIMIT_EXCEEDED` retains the retryable default, so its behavior is unchanged (ergonomics/typing only).

## [0.2.5] - 2026-06-18

### Added

- Add declarative commit metadata binding via `@Cycles(metadata = "...")`. The SpEL expression is evaluated at commit time against the guarded method invocation, must produce a `Map<String,Object>`, and is merged with `CyclesContextHolder` commit metadata, with programmatic metadata taking precedence on duplicate keys. Implements [#88](https://github.com/runcycles/cycles-spring-boot-starter/issues/88).

### Build

- Adopt Maven [CI-friendly versions](https://maven.apache.org/maven-ci-friendly.html). Both poms (`cycles-client-java-spring` and `cycles-demo-client-java-spring`) now declare `<version>${revision}</version>`, and the demo's dependency on the starter uses `<version>${revision}</version>` as well. The single source of truth lives at `.mvn/maven.config` at the repo root (currently `-Drevision=0.2.5`). Cutting a release becomes a one-line edit. Also fixes a drift bug: pre-refactor the demo's pom was at version `0.2.1` and its dep on the starter pinned `0.2.1` while the starter shipped `0.2.2` — they could never have been bumped together without two manual edits.
- `flatten-maven-plugin` (`resolveCiFriendliesOnly` mode) wired on both poms so install/deploy emit a `.flattened-pom.xml` with `${revision}` substituted to a literal version. Sonatype Central requires a literal in the published `<version>` field; non-CI-friendly properties like `${spring.boot.version}` remain unresolved in the published pom and interpolate against the pom's own `<properties>` block at consumer-resolve time (standard Maven behavior, unchanged).

## [0.2.4] - 2026-05-22

Wire-passthrough verification for `expires_from`/`expires_to` and `finalized_from`/`finalized_to` query params on `listReservations`. Implements `cycles-protocol-v0.yaml` revision 2026-05-22 ([runcycles/cycles-protocol#98](https://github.com/runcycles/cycles-protocol/pull/98)) on the client side; runcycles/cycles-server#163 ships the server impl. Closes the Spring Boot starter side of runcycles/cycles-server#162.

### Added

- Regression test on `DefaultCyclesClient.listReservations` confirming the four new ISO-8601 window params land on the wire under their spec-mandated names. The existing `Map<String, String>` signature already accepted them — the test pins the contract.

### Notes

- Spring's `WebClient` leaves colons unencoded in the query component (RFC 3986 §3.4-valid), so the wire form is `expires_from=2026-05-22T00:00:00Z` rather than the percent-escaped variant. Same behavior as v0.2.3.
- No protocol or wire-format change. Servers older than v0.1.25.21 silently ignore the new params per the additive-parameter guarantee in `cycles-protocol-v0.yaml`.
- 434 tests pass; JaCoCo coverage gate met (≥95% per `CLAUDE.md`).
- Version bumped on both `cycles-client-java-spring` and `cycles-demo-client-java-spring` poms via the single `.mvn/maven.config` source of truth.

## [0.2.3] - 2026-05-21

Wire-passthrough verification for the new `from` / `to` query params on `listReservations`. Implements `cycles-protocol-v0.yaml` revision 2026-05-21 ([runcycles/cycles-protocol#97](https://github.com/runcycles/cycles-protocol/pull/97)) on the client side; runcycles/cycles-server#160 ships the server impl. Closes the Spring Boot starter side of runcycles/cycles-server#159.

### Added

- Regression test on `DefaultCyclesClient.listReservations` confirming that `from` / `to` ISO-8601 date-time values are forwarded to the URL query string. The client's `Map<String, String>` signature already accepted these — the test pins the contract so future tightening cannot silently drop them.

### Notes

- Spring's `WebClient` leaves colons unencoded in the query component (RFC 3986 §3.4 permits this), so the wire form is `from=2026-05-21T00:00:00Z` rather than `from=2026-05-21T00%3A00%3A00Z`. Both forms are valid and accepted by cycles-server.
- No protocol or wire-format change. Servers older than v0.1.25.20 silently ignore the new params per the additive-parameter guarantee in `cycles-protocol-v0.yaml`.
- 433 tests pass; JaCoCo coverage gate met (≥95% per `CLAUDE.md`).
- Version bumped on both `cycles-client-java-spring` and `cycles-demo-client-java-spring` poms via the single `.mvn/maven.config` source of truth.

## [0.2.2] - 2026-05-07

Maven Central metadata refresh for category-search discovery. **No code changes** — wire format, public API, and Spring AOP integration are identical to 0.2.1.

### Changed

- `pom.xml` (`cycles-client-java-spring`): rewrote `<description>` to lead with the cost / action / audit pillars and explicit AI-agent / Spring AI positioning. New: *"Spring Boot starter for AI agent runtime control with Cycles. Enforce LLM cost limits, tool call caps, action permissions, and audit trails on Spring AI / Spring Boot agents before execution. Reactive WebFlux client with @Cycles annotation, SpEL-based subject routing, and per-tenant budget enforcement."* Updated `<name>` to *"Cycles Client Java Spring — AI agent runtime control for Spring Boot"*.

Maven Central uses the pom `<description>` as the primary search/snippet field (no keyword field exists in Maven coordinates). The previous one-liner *"Spring-based Java client for the Cycles protocol."* offered no category-search surface.

## [0.2.1] - 2026-04-27

### Fixed

- Evaluate SpEL on `@Cycles` subject fields (`tenant`, `workspace`, `app`, `workflow`, `agent`, `toolset`) when the value's first non-whitespace character is `#`. Previously the literal expression string was sent to the server, producing a 400 `INVALID_REQUEST`. Literal values are unchanged. ([#49](https://github.com/runcycles/cycles-spring-boot-starter/issues/49))
- Make `CyclesAutoConfiguration#cyclesSelfInvocationDetector` a `static` `@Bean` factory method, eliminating the Spring startup warning *"Bean ... is not eligible for getting processed by all BeanPostProcessors"*. ([#49](https://github.com/runcycles/cycles-spring-boot-starter/issues/49))

### Changed

- **Behavior change:** A SpEL expression on a `@Cycles` subject field that fails to **parse** (e.g. `#((bad`) or fails to **evaluate against actual values** (e.g. invalid property access) now surfaces at AOP entry as `ParseException` / `SpelEvaluationException` instead of producing a malformed reservation request. References to undefined variables still resolve to `null` and fall through to the config / resolver bean chain, matching `#req?.workspaceId` semantics.
- `CyclesExpressionEvaluator` caches parsed `Expression` instances per raw expression string, removing the per-invocation parse cost on hot `@Cycles` paths.

## [0.2.0] - 2026-03-24

Bug fixes, support 0.1.24 protocol spec.

### Added

- Add comprehensive test coverage for model and service classes ([#24](https://github.com/runcycles/cycles-spring-boot-starter/pull/24))
- Add API key creation instructions to README ([#25](https://github.com/runcycles/cycles-spring-boot-starter/pull/25))
- Add CI badge and standardize License badge ([#26](https://github.com/runcycles/cycles-spring-boot-starter/pull/26))
- Document and demo per-annotation budget scope targeting ([#27](https://github.com/runcycles/cycles-spring-boot-starter/pull/27))
- Add documentation links to README ([#28](https://github.com/runcycles/cycles-spring-boot-starter/pull/28))
- Add self-invocation detection and documentation for `@Cycles` ([#30](https://github.com/runcycles/cycles-spring-boot-starter/pull/30))
- Document nested `@Cycles` limitation across services in README ([#31](https://github.com/runcycles/cycles-spring-boot-starter/pull/31))
- Claude/analyze spring issue 29 v biy9 ([#32](https://github.com/runcycles/cycles-spring-boot-starter/pull/32))
- Add budget state and extension limit error codes; include charged amount in `EventResult` ([#34](https://github.com/runcycles/cycles-spring-boot-starter/pull/34))

### Changed

- Change default overage policy from `REJECT` to `ALLOW_IF_AVAILABLE` ([#33](https://github.com/runcycles/cycles-spring-boot-starter/pull/33))
- Bump version to 0.2.0 for protocol v0.1.24 ([#35](https://github.com/runcycles/cycles-spring-boot-starter/pull/35))

## [0.1.1] - 2026-03-15

Minor bug fixes, test coverage.

### Added

- Add comprehensive demo application showcasing Cycles Spring Boot Starter ([#17](https://github.com/runcycles/cycles-spring-boot-starter/pull/17))
- Add CI workflow for automated testing ([#20](https://github.com/runcycles/cycles-spring-boot-starter/pull/20))
- Add demo module compilation to CI gate ([#22](https://github.com/runcycles/cycles-spring-boot-starter/pull/22))

### Changed

- Bump cycles-client-java-spring version to 0.1.1 ([#16](https://github.com/runcycles/cycles-spring-boot-starter/pull/16))
- Update error handling for Cycles protocol HTTP status codes ([#18](https://github.com/runcycles/cycles-spring-boot-starter/pull/18))
- Update documentation with correct module names and add demo client ([#23](https://github.com/runcycles/cycles-spring-boot-starter/pull/23))

### Fixed

- Fix cycles-client-java-spring version to 0.1.0 ([#19](https://github.com/runcycles/cycles-spring-boot-starter/pull/19))
- Fix CI to use `mvn verify` so JaCoCo coverage check executes ([#21](https://github.com/runcycles/cycles-spring-boot-starter/pull/21))

## [0.1.0] - 2026-03-11

Initial public release of cycles-client-java-spring.

### Added

- Add retry-after and reserved fields to reservation context ([#4](https://github.com/runcycles/cycles-spring-boot-starter/pull/4))
- Add typed DTOs for Cycles API requests and responses ([#6](https://github.com/runcycles/cycles-spring-boot-starter/pull/6))
- Claude/add client dtos t hu5r ([#7](https://github.com/runcycles/cycles-spring-boot-starter/pull/7))
- Add comprehensive audit report for Cycles Protocol v0.1.23 client ([#8](https://github.com/runcycles/cycles-spring-boot-starter/pull/8))
- Add comprehensive client unit tests for protocol conformance ([#9](https://github.com/runcycles/cycles-spring-boot-starter/pull/9))
- Add comprehensive test suite for Cycles Spring client ([#10](https://github.com/runcycles/cycles-spring-boot-starter/pull/10))
- Add heartbeat scheduling and cancellation tests ([#11](https://github.com/runcycles/cycles-spring-boot-starter/pull/11))
- Add comprehensive test coverage for core Cycles components ([#12](https://github.com/runcycles/cycles-spring-boot-starter/pull/12))
- Add comprehensive JavaDoc documentation to all public APIs ([#15](https://github.com/runcycles/cycles-spring-boot-starter/pull/15))

### Changed

- Refactor HTTP client and improve error handling and logging ([#1](https://github.com/runcycles/cycles-spring-boot-starter/pull/1))
- Claude/review cycles server bugs j0 ha q ([#2](https://github.com/runcycles/cycles-spring-boot-starter/pull/2))
- Claude/review cycles server bugs j0 ha q ([#3](https://github.com/runcycles/cycles-spring-boot-starter/pull/3))
- Claude/validate client server 7 rm0e ([#5](https://github.com/runcycles/cycles-spring-boot-starter/pull/5))
- Make `Amount` and `SignedAmount` nullable to support optional values ([#13](https://github.com/runcycles/cycles-spring-boot-starter/pull/13))

### Fixed

- Fix `UNAUTHORIZED` HTTP status code and enhance dry-run documentation ([#14](https://github.com/runcycles/cycles-spring-boot-starter/pull/14))

[0.2.1]: https://github.com/runcycles/cycles-spring-boot-starter/releases/tag/v0.2.1
[0.2.0]: https://github.com/runcycles/cycles-spring-boot-starter/releases/tag/v0.2.0
[0.1.1]: https://github.com/runcycles/cycles-spring-boot-starter/releases/tag/v0.1.1
[0.1.0]: https://github.com/runcycles/cycles-spring-boot-starter/releases/tag/v0.1.0
