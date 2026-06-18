# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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
