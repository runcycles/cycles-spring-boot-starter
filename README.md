# Cycles: The Economic Governance Layer for Spring AI

[![Maven Central](https://img.shields.io/maven-central/v/io.cycles/cycles-spring-boot-starter.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.cycles%22%20AND%20a:%22cycles-spring-boot-starter%22)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Status](https://img.shields.io/badge/Status-Private%20Beta-orange)]()

**The Economic Circuit Breaker for Autonomous Agents**

Cycles is a JVM-level **economic governance layer** for Spring Boot applications.
It enforces **deterministic spend limits** on guarded AI execution—preventing infinite loops, runaway recursion, and API bill shock *before* they happen.

Rate limits control **velocity**.  
**Cycles controls exposure.**

---

## 🛑 The Problem: “The $5,000 Loop”

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

Cycles introduces a new primitive: **the Cycle**.

> **1 Cycle = 1 unit of execution risk**  
> (e.g., a tokenized cost, a priced tool call, or a complexity-weighted action)

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
````

Add a global guardrail to `application.yml`:

```yaml
cycles:
  enabled: true
  # Global safety net: no single execution may exceed 100 cycles
  global-budget: 100
```

---

## ✨ The Annotation

Add one annotation to define an **economic envelope** for an execution.

### 1. Hard Enforcement (Production)

```java
@Service
public class ResearchAgent {

    private final OpenAiClient ai;

    // Economic envelope for this execution.
    // As the budget tightens, Cycles may degrade behavior.
    // If the budget is exhausted, guarded execution halts deterministically.
    @Cycles(limit = 50)
    public Report generateMarketReport(String ticker) {

        // Even if this call loops indefinitely,
        // execution will stop once the budget is exhausted.
        return ai.recursiveDeepDive(ticker);
    }
}
```

---

### 2. Shadow Mode (Safe Trial)

Not ready to halt execution yet? Use **REPORT_ONLY** mode.

Cycles tracks spend and emits “red” events, but **does not stop execution**.

```java
@Cycles(limit = 50, mode = EnforcementMode.REPORT_ONLY)
public void betaTestAgent() {
    // Execution continues, budget overruns are logged
}
```

---

## ⚙️ Advanced Configuration (Enterprise)

For larger agent fleets, Cycles supports **profiles**, **degradation policies**, and **shared budgets**.

### Example: “Degrade, Then Halt” Policy

This profile warns at 70%, degrades at 90%, and halts at 100%.

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

          thresholds:
            spent:
              yellow: 0.70  # Warn
              orange: 0.90  # Degrade
              red: 1.00     # Halt

        - name: group
          scope: GROUP
          key: "{teamId}"
          initialLimit: 5000

      policies:
        green:
          allow:
            actions: ["*"]

        yellow:
          degrade:
            modelTier: downgrade
            contextWindow: reduce
          throttle:
            minDelayMs: 250

        orange:
          block:
            actions: [WRITE, EXECUTE_TOOL, DEPLOY]
          throttle:
            minDelayMs: 1000
          retries:
            max: 1

        red:
          onExhaust: HALT
          emit:
            mode: PARTIAL_RESULT
          fallback:
            strategy: SUMMARY_ONLY
```

---

## ⚠️ Enforcement Model

Cycles enforces budgets **only on guarded code paths**
(via `@Cycles` and Spring AOP interception).

* **Guarded execution** → deterministic enforcement
* **Unguarded execution** → allowed, but surfaced for visibility

This enables teams to:

1. Start with observability
2. Gradually harden enforcement
3. Avoid breaking legacy systems

---

## 🏗️ Architecture & Performance

Cycles is designed for **macro-governance**, not micro-benchmarking.
It uses an **atomic authorize-and-burn** interceptor pattern.

| Component          | Responsibility                     | Typical Cost |
| ------------------ | ---------------------------------- | ------------ |
| **Interceptor**    | Pauses execution                   | < 0.1ms      |
| **Ledger (Redis)** | Atomic authorize + decrement (Lua) | ~1–3ms RTT   |
| **Enforcer**       | Halts guarded execution            | ~0ms         |

**Performance Note:**
Do not annotate tight inner loops.
Annotate **service boundaries** (e.g., `generateReport`, `executeTool`, `callModel`).
A few milliseconds of overhead is negligible compared to a 500ms+ LLM call.

---

## 🚨 The Panic Button (Actuator)

Agent stuck in a loop *right now*? No redeploy required.

Cycles exposes a Spring Boot Actuator endpoint for live intervention.

```bash
# Emergency: top up budget for an active agent
curl -X POST http://localhost:8080/actuator/cycles/wallet/agent-007/topup \
     -d '{"amount": 500}'
```

---

## 🆚 Cycles vs. Rate Limiters

| Feature  | Rate Limiters     | Cycles                        |
| -------- | ----------------- | ----------------------------- |
| Metric   | Requests / second | **Risk / execution**          |
| Protects | Servers           | **Wallets**                   |
| Response | Throttle          | **Degrade → Restrict → Halt** |
| Scope    | Single service    | **Distributed call graph**    |
| Use case | Traffic spikes    | **Autonomous agents & LLMs**  |

---

## 🔮 Roadmap

* **v0.1** — Spring AOP + Redis (Beta)
* **v0.5** — Monitoring dashboard
* **v1.0** — `X-Cycles-Budget` HTTP context propagation

---

## 🤝 Join the Private Beta

We are onboarding **Fintech and Enterprise Java teams** running Spring AI in production.

If runaway AI spend is a real risk for you, let’s talk.

👉 **[Request Access](https://docs.google.com/forms/d/e/1FAIpQLSd4FB1W_NrmHqf873lUUSP2V6_uWEVG2J6OteQ9hM8yWynKNQ/viewform?usp=dialog)**

*(Auditing slots available for Q1 2026)*

---

**License:** Apache 2.0
**Maintained by:** Albert / RunCycles.io

```
