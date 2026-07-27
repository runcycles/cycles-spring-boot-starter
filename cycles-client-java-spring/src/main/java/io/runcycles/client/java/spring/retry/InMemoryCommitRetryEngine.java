package io.runcycles.client.java.spring.retry;

import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.config.CyclesProperties;

/**
 * In-memory-only {@link CommitRetryEngine}: identical retry semantics to
 * {@link JournaledCommitRetryEngine} but with the on-disk pending-commit journal
 * forced off, so pending commits do not survive a JVM exit.
 *
 * <p>Retry behaviour is controlled by {@link CyclesProperties.Retry}:
 * <ul>
 *   <li>{@code enabled} — master switch (default {@code true})</li>
 *   <li>{@code maxAttempts} — maximum number of retry attempts (default {@code 5})</li>
 *   <li>{@code initialDelay} — delay before the first retry (default {@code 500ms})</li>
 *   <li>{@code multiplier} — backoff multiplier (default {@code 2.0})</li>
 *   <li>{@code maxDelay} — upper bound on delay between retries (default {@code 30s})</li>
 * </ul>
 *
 * @deprecated since 0.3.0 — use {@link JournaledCommitRetryEngine}, which journals
 *             pending commits to disk so spend that already happened survives JVM
 *             restarts and is replayed on the next run.
 */
@Deprecated(since = "0.3.0")
public class InMemoryCommitRetryEngine extends JournaledCommitRetryEngine {

    /**
     * Creates a new in-memory-only retry engine with the given client and configuration.
     *
     * @param client     the Cycles API client for retrying commits
     * @param properties the Cycles configuration properties containing retry settings
     */
    public InMemoryCommitRetryEngine(CyclesClient client, CyclesProperties properties) {
        super(client, properties, false);
    }
}
