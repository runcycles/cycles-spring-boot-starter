# Cycles: The Economic Governance Layer for Spring AI

[![Maven Central](https://img.shields.io/maven-central/v/io.cycles/cycles-spring-boot-starter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.cycles%22%20AND%20a:%22cycles-spring-boot-starter%22)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Status](https://img.shields.io/badge/Status-Private%20Beta-orange)]()

**An economic circuit breaker for autonomous agents.**

Cycles is a JVM-level **economic governance layer** for Spring Boot applications.
It enforces **deterministic spend limits** on guarded AI execution—preventing infinite loops, runaway recursion, and API bill shock *before* they happen.

Rate limits control **velocity**.  
**Cycles controls exposure.**

---

## 🛑 The Problem: "The $5,000 Loop"

You deploy an agent to summarize daily news.  
A prompt bug causes it to retry endlessly on a `500` error from the provider.  
You have a standard rate limiter (`100 req/min`).

**What happens:**

- **00:00** — Agent starts looping  
- **00:10** — Rate limiter allows ~1,000 calls  
- **01:00** — ~6,000 calls  
- **08:00** — You wake up to a **$4,500 OpenAI bill**

**Rate limits do not stop bad logic. They only slow it down.**

---

## ⚡ The Solution: Economic Governance

Cycles introduces a new primitive: **The Cycle**.

> **1 Cycle = 1 Unit of Execution Risk** > (e.g., $0.01 USD, 1 Token, or a weighted complexity score)

Instead of limiting *requests per second*, Cycles limits **total risk per execution**.

As the budget tightens, Cycles **degrades behavior according to policy**.  
When the budget is exhausted, execution **halts deterministically**.

---

## 🚀 Quick Start (Zero Config)

By default, Cycles runs in **Local Mode** (in-memory) with a global safety net.
Add the dependency and start coding.

```xml
<dependency>
  <groupId>io.cycles</groupId>
  <artifactId>cycles-spring-boot-starter</artifactId>
  <version>0.1.0-beta</version>
</dependency>

```

Add a global guardrail to `application.yml`:

```yaml
cycles:
  enabled: true
  # Global safety net: No single execution may exceed 100 cycles ($1.00)
  global-budget: 100

```

---

## ✨ Usage: The Annotation

Add one annotation to define an **economic envelope** for an execution.

### 1. Hard Enforcement (Production)

If the budget is exceeded, the thread is interrupted and a `CycleExhaustedException` is thrown.

```java
@Service
public class ResearchAgent {

    private final OpenAiClient ai;

    // 🛑 HARD STOP: If this method (and its sub-calls) burns > 50 Cycles,
    // execution halts deterministically.
    @Cycles(limit = 50)
    public Report generateMarketReport(String ticker) {

        // Even if this call loops indefinitely,
        // execution will stop once the budget is exhausted.
        return ai.recursiveDeepDive(ticker);
    }
}

```

### 2. Shadow Mode (Safe Trial)

Not ready to kill processes yet? Use `REPORT_ONLY` mode.
Cycles tracks spend and emits "Red" alert events, but **does not stop execution**.

```java
// 🛡️ SHADOW MODE: Logs the potential kill, but lets the agent live.
@Cycles(limit = 50, mode = EnforcementMode.REPORT_ONLY)
public void betaTestAgent() {
    // Execution continues even after 50 cycles, budget overruns are logged.
}

```

---

## ⚙️ Advanced Configuration (Enterprise)

For complex agent fleets, Cycles supports **profiles**, **degradation policies**, and **shared team budgets**.

### Example: "Degrade, Then Halt" Policy

This profile warns at 70%, degrades model quality at 90%, and halts at 100%.

```yaml
cycles:
  enabled: true
  storage: redis

  redis:
    host: localhost
    port: 6379

  profiles:
    agent-default:
      description: "Standard agent profile with progressive degradation"

      buckets:
        - name: execution
          scope: EXECUTION
          key: "{executionId}"
          initialLimit: 200

          # 🚦 Warning Thresholds
          thresholds:
            spent:
              yellow: 0.70  # Warn
              orange: 0.90  # Degrade
              red: 1.00     # Halt

        - name: group
          scope: GROUP
          key: "{teamId}"
          initialLimit: 5000 # Daily team budget

      policies:
        green:
          allow:
            actions: ["*"]

        # 🟡 Phase 1: Throttle & Reduce Quality
        yellow:
          degrade:
            modelTier: downgrade      # Switch GPT-4 -> GPT-3.5
            contextWindow: reduce     # Shrink context
          throttle:
            minDelayMs: 250

        # 🟠 Phase 2: Block Writes & Expensive Tools
        orange:
          block:
            actions: [WRITE, EXECUTE_TOOL, DEPLOY]
          throttle:
            minDelayMs: 1000
          retries:
            max: 1

        # 🔴 Phase 3: Kill Switch
        red:
          onExhaust: HALT
          emit:
            mode: PARTIAL_RESULT      # Return what we have so far
          fallback:
            strategy: SUMMARY_ONLY

```

---

## ⚠️ Enforcement Model

Cycles enforces budgets **only on guarded code paths** (via `@Cycles` and Spring AOP interception).

* **Guarded Execution:** Budgets enforced deterministically.
* **Unguarded Execution:** Allowed, but surfaced in the dashboard (Passive Monitoring).

This allows teams to:

1. Start with visibility.
2. Progressively harden enforcement.
3. Avoid breaking legacy systems.

---

## 🏗️ Architecture & Performance

Cycles is designed for **Macro-Governance**, not micro-benchmarking. It uses an **Atomic Authorize-and-Burn** interceptor pattern.

| Component | Responsibility | Typical Cost |
| --- | --- | --- |
| **Interceptor** | Pauses execution to check budget | < 0.1ms |
| **Ledger (Redis)** | Atomic authorize + decrement (Lua) | ~1-3ms (Network RTT) |
| **Enforcer** | Halts guarded execution | 0ms |

**⚠️ Performance Note:**
Do not put `@Cycles` on a tight `for-loop` that runs 1,000 times per second.
Annotate **Service Boundaries** (e.g., `generateReport`, `executeTool`, `callModel`).
The 3ms overhead is negligible compared to the 500ms+ latency of an LLM call.

---

## 🚨 The Panic Button (Actuator)

Agent stuck in a loop *right now*? Don't redeploy.
Cycles exposes a Spring Boot Actuator endpoint to hot-patch budgets live.

```bash
# Emergency: Boost budget by 500 cycles for an active agent
curl -X POST http://localhost:8080/actuator/cycles/wallet/agent-007/topup \
     -d '{"amount": 500}'

```

---

## 🆚 Cycles vs. Rate Limiters

| Feature | Rate Limiter (Resilience4j / Bucket4j) | Cycles (Economic Governance) |
| --- | --- | --- |
| **Metric** | Requests / Second | **Risk / Execution** |
| **Goal** | Protect the *Server* from overload | Protect the *Wallet* from bankruptcy |
| **Outcome** | Throttles traffic (Slows down) | **Kills process** (Stops completely) |
| **Context** | Single Service | **Distributed Call Graph** (A calls B calls C) |
| **Use Case** | High Traffic APIs | **Autonomous Agents & LLMs** |

---

## 🔮 Roadmap

* **v0.1:** Local Redis implementation & Spring AOP. (Beta)
* **v0.5:** Dashboard for real-time monitoring.
* **v1.0:** `X-Cycles-Budget` HTTP Context propagation for cross-service governance.

---

## 🤝 Join the Private Beta

We are currently onboarding **Enterprise Java teams** deploying agentic workflows for the private beta.
If you are running Spring AI in production and worry about cost, let's talk.

**[👉 Request Access to the JAR](https://docs.google.com/forms/d/e/1FAIpQLSd4FB1W_NrmHqf873lUUSP2V6_uWEVG2J6OteQ9hM8yWynKNQ/viewform?usp=dialog)**

*(Auditing slots available for Q1 2026)*

---

**License:** Apache 2.0
**Maintained by:** Albert / RunCycles.io

```
