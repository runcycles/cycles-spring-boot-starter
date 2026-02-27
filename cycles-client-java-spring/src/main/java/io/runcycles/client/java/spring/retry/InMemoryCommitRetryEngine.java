package io.runcycles.client.java.spring.retry;

import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.config.CyclesProperties;

import java.time.Duration;
import java.util.concurrent.*;

public class InMemoryCommitRetryEngine implements CommitRetryEngine {

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
                client.commitReservation(id, body);
            } catch (Exception e) {
                if (attempt < props.getMaxAttempts()) {
                    Duration next = delay.multipliedBy((long) props.getMultiplier());
                    if (next.compareTo(props.getMaxDelay()) > 0) {
                        next = props.getMaxDelay();
                    }
                    retry(id, body, attempt + 1, next);
                }
            }
        }, delay.toMillis(), TimeUnit.MILLISECONDS);
    }
}
