# Maintainers

This document lists the people responsible for triaging issues, reviewing PRs, and cutting releases for `cycles-spring-boot-starter` (the `runcycles-spring-boot-starter` artifact on Maven Central).

## Project lead

- **Albert Mavashev** — [@amavashev](https://github.com/amavashev) — primary committer, release manager

## Contributing organizations

The project receives contributions from:

- [Runcycles](https://github.com/runcycles)
- [K2NIO](https://github.com/K2NIO)
- Singleton Labs

## Responsibilities

| Area | Owner | SLA |
|---|---|---|
| Issue triage | Project lead | 5 business days for first response |
| PR review | Project lead | 3 business days for initial review |
| Security disclosures | See [SECURITY.md](https://github.com/runcycles/.github/blob/main/SECURITY.md) | 48h acknowledgment, 5 business days assessment |
| Release cuts | Project lead | Maven Central publish via `publish.yaml` workflow on tag |
| Dependency updates | Dependabot + project lead | Auto-merge on patch updates passing CI; manual review on minor/major |
| Spring Boot version bumps | Project lead | Periodic patch bumps within Spring Boot 3.x line; major (4.x) bumps require explicit scope review |

## Becoming a maintainer

We're a small team and add maintainers cautiously. Path is usually: sustained contribution (≥3 substantive PRs) → triage assistance → invitation to commit access. Open a discussion if you're interested.

## How to reach maintainers

- **General questions / bug reports**: [GitHub Issues](https://github.com/runcycles/cycles-spring-boot-starter/issues)
- **Security disclosures**: see [SECURITY.md](https://github.com/runcycles/.github/blob/main/SECURITY.md) (do **not** open a public issue)
- **Conduct concerns**: see the org-wide [Code of Conduct](https://github.com/runcycles/.github/blob/main/CODE_OF_CONDUCT.md)
