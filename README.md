# Cycles: The Economic Circuit Breaker for Spring AI

**Stop your autonomous agents from bankrupting you.**

Cycles is a JVM-level governance layer for Spring Boot applications. It enforces **hard economic limits** on AI execution, preventing infinite loops, runaway recursion, and API bill shock *before* they happen.

Rate limits control speed. **Cycles controls spend.**

---

## 🛑 The Problem: "The $5,000 Loop"

You deploy an agent to summarize daily news.
A bug in the prompt causes it to retry endlessly on a `500` error from the provider.
You have a standard Rate Limiter (`100 req/min`).

* **00:00:** Agent starts looping.
* **00:10:** Rate limiter allows 1,000 requests.
* **01:00:** Rate limiter allows 6,000 requests.
* **08:00:** You wake up to a **$4,500 OpenAI bill**.

**Rate limits do not stop bad logic. They just make it slower.**

---

## ⚡ The Solution: "Digital Thermodynamics"

Cycles introduces a new primitive: **The Cycle**.
1 Cycle = 1 Unit of Risk.

A Cycle is a normalized unit of execution risk.
You decide what actions cost more based on blast radius.

Instead of limiting *requests per second*, you limit *total risk budget*. When the budget hits 0, the execution **halts deterministically**.

### The "Magic" Annotation

Add one dependency. Add one annotation. Sleep soundly.

```java
@Service
public class ResearchAgent {

    private final OpenAiClient ai;

    // 🛑 HARD STOP: If this method (and its sub-calls) burns > 50 Cycles ($0.50),
    // execution halts deterministically with a non-recoverable exception.
    @Cycles(limit = 50, action = ExhaustionAction.HALT)
    public Report generateMarketReport(String ticker) {
        
        // Even if this internal method loops forever, 
        // Cycles will cut the power at $0.50.
        return ai.recursiveDeepDive(ticker); 
    }
}

```

---

## 🚀 Features

* **Atomic Accounting:** Uses Lua scripts on Redis for sub-millisecond, race-condition-free budget decrements.
* **Zero-Latency:** Adds <2ms overhead to your API calls.
* **Context Propagation:** The budget travels with the request. If Service A calls Service B, they share the same Risk Budget.
* **"Panic Button":** Update budgets in real-time via the Dashboard without redeploying code.
* **Audit-Ready:** Every burned Cycle is logged with a cryptographic signature (Enterprise Plan).

---

## 📦 Installation

Add the starter to your `pom.xml`:

```xml
<dependency>
    <groupId>io.cycles</groupId>
    <artifactId>cycles-spring-boot-starter</artifactId>
    <version>0.1.0-beta</version>
</dependency>

```

Configure the Cycles ledger (runs on local Redis by default):

```yaml
cycles:
  enabled: true
  storage: redis
  redis:
    host: localhost
    port: 6379
  default-policy:
    limit: 100
    action: THROW_EXCEPTION

```

---

## 🆚 Cycles vs. Rate Limiters

| Feature | Rate Limiter (Resilience4j / Bucket4j) | Cycles (Economic Governance) |
| --- | --- | --- |
| **Metric** | Requests / Second | **Cost / Session** |
| **Goal** | Protect the *Server* from overload | Protect the *Wallet* from bankruptcy |
| **Outcome** | Throttles traffic (Slows down) | **Kills process** (Stops completely) |
| **Context** | Single Service | **Distributed Trace** (A calls B calls C) |
| **Use Case** | High Traffic APIs | **Autonomous Agents & LLMs** |

---

## 🛠️ Architecture

Cycles sits between your code and the execution. It uses a **Check-Then-Act** interceptor pattern.

1. **Intercept:** `@Cycles` pauses the request.
2. **Verify:** Checks the `cycles:wallet:{agent_id}` in Redis.
3. **Burn:** Atomically decrements the cost (e.g., `-5`).
4. **Verdict:**
* ✅ **Solvent:** Proceed.
* ❌ **Insolvent:** Throw `CycleExhaustedException`.

---

## 🔮 Roadmap

* **v0.1:** Local Redis implementation & Spring AOP. (Beta)
* **v0.5:** Dashboard for real-time monitoring.
* **v1.0:** `X-Cycles-Budget` Cycles Context HTTP header for cross-service propagation (experimental).

---

## 🤝 Join the Private Beta

We are currently onboarding **Fintech & Enterprise Java teams** for the private beta.
If you are running Spring AI in production and worry about cost, let's talk.

**[👉 Request Access to the JAR](https://docs.google.com/forms/d/e/1FAIpQLSd4FB1W_NrmHqf873lUUSP2V6_uWEVG2J6OteQ9hM8yWynKNQ/viewform?usp=dialog)**

*(Auditing slots available for Q1 2026)*

---

**License:** Apache 2.0
**Maintained by:** [Albert Mavashev]
