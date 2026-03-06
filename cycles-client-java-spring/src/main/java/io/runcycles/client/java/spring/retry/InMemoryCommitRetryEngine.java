package io.runcycles.client.java.spring.retry;

import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.config.CyclesProperties;
import io.runcycles.client.java.spring.model.CyclesResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;

public class InMemoryCommitRetryEngine implements CommitRetryEngine {

    private static final Logger LOG = LoggerFactory.getLogger(InMemoryCommitRetryEngine.class);

    private final CyclesClient client;
    private final CyclesProperties.Retry props;
    private final ScheduledExecutorService executor;

    public InMemoryCommitRetryEngine(CyclesClient client, CyclesProperties properties) {
        this.client = client;
        this.props = properties.getRetry();
        this.executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            t.setName("cycles-commit-retry");
            return t;
        });
    }

    @Override
    public void schedule(String reservationId, Object body) {
        if (!props.isEnabled()) return;
        retry(reservationId, body, 1, props.getInitialDelay());
    }

    private void retry(String id, Object body, int attempt, Duration delay) {
        executor.schedule(() -> {
            try {
                CyclesResponse<Map<String, Object>> response = client.commitReservation(id, body);
                if (response.is2xx()) {
                    LOG.info("Commit retry succeeded: reservationId={}, attempt={}", id, attempt);
                } else if (response.isTransportError() || response.is5xx()) {
                    LOG.warn("Commit retry failed with retryable error: reservationId={}, attempt={}, status={}, error={}",
                            id, attempt, response.getStatus(), response.getErrorMessage());
                    scheduleNextAttempt(id, body, attempt, delay);
                } else {
                    LOG.error("Commit retry failed with non-retryable error: reservationId={}, attempt={}, status={}, error={}",
                            id, attempt, response.getStatus(), response.getErrorMessage());
                }
            } catch (Exception e) {
                LOG.error("Commit retry failed with unexpected exception: reservationId={}, attempt={}", id, attempt, e);
                scheduleNextAttempt(id, body, attempt, delay);
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void scheduleNextAttempt(String id, Object body, int attempt, Duration delay) {
        if (attempt < props.getMaxAttempts()) {
            Duration next = delay.multipliedBy((long) props.getMultiplier());
            if (next.compareTo(props.getMaxDelay()) > 0) {
                next = props.getMaxDelay();
            }
            retry(id, body, attempt + 1, next);
        } else {
            LOG.error("Commit retry exhausted all attempts: reservationId={}, maxAttempts={}", id, props.getMaxAttempts());
        }
    }
}
