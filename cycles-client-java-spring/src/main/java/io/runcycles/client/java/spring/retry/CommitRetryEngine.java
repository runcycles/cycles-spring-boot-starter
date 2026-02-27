package io.runcycles.client.java.spring.retry;

public interface CommitRetryEngine {
    void schedule(String reservationId, Object body);
}
