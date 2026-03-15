# Cycles Spring Boot Starter

## Maven Builds

In Claude Code remote environments, use `mvn-proxy` instead of `mvn` for all Maven commands.
The session start hook (`.claude/session-start-maven-proxy.sh`) automatically sets this up.

```bash
# Use this:
mvn-proxy -B verify --file cycles-spring-boot-starter/pom.xml

# NOT this (will fail with DNS/proxy errors):
mvn -B verify --file cycles-spring-boot-starter/pom.xml
```

**Why:** The remote environment routes traffic through an egress proxy. Java's `JAVA_TOOL_OPTIONS`
proxy config resolves DNS locally (which fails). `mvn-proxy` uses `MAVEN_OPTS` instead and
forces single-threaded downloads to avoid proxy auth race conditions.
