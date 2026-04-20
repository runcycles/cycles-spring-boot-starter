# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/).

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

[0.2.0]: https://github.com/runcycles/cycles-spring-boot-starter/releases/tag/v0.2.0
[0.1.1]: https://github.com/runcycles/cycles-spring-boot-starter/releases/tag/v0.1.1
[0.1.0]: https://github.com/runcycles/cycles-spring-boot-starter/releases/tag/v0.1.0
