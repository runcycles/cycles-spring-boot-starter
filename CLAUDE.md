## Git Rules — STRICT
- ALWAYS use native git for ALL commits and pushes
- NEVER use mcp__github__ tools for committing or pushing
- Use mcp__github__ ONLY for: PRs, Issues, GitHub Actions
- Write commit messages to a temp file, then: `git commit -F <file>`
- NEVER use --no-gpg-sign flag

# Cycles strict rules
- yaml API specs always the authority
- always update AUDIT.md files when making changes to server, admin, client repos
- maintain at least 95% or higher test coverage for all code repos

# Cycles Spring Boot Starter

## Maven Builds

In Claude Code remote environments, use `mvn-proxy` instead of `mvn` for all Maven commands.
The session start hook (`.claude/session-start-maven-proxy.sh`) automatically sets this up.

```bash
# Use this:
mvn-proxy -B verify --file cycles-client-java-spring/pom.xml
mvn-proxy -B verify --file cycles-demo-client-java-spring/pom.xml

# NOT this (will fail with DNS/proxy errors):
mvn -B verify --file cycles-client-java-spring/pom.xml
```

**Why:** The remote environment routes traffic through an egress proxy. Java's `JAVA_TOOL_OPTIONS`
proxy config resolves DNS locally (which fails). `mvn-proxy` uses `MAVEN_OPTS` instead and
forces single-threaded downloads to avoid proxy auth race conditions.
