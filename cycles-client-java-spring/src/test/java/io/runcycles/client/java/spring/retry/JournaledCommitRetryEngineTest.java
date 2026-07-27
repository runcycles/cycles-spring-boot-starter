package io.runcycles.client.java.spring.retry;

import io.runcycles.client.java.spring.client.CyclesClient;
import io.runcycles.client.java.spring.config.CyclesProperties;
import io.runcycles.client.java.spring.journal.CommitJournal;
import io.runcycles.client.java.spring.journal.PendingCommitRecord;
import io.runcycles.client.java.spring.model.CyclesResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("JournaledCommitRetryEngine")
class JournaledCommitRetryEngineTest {

    private static final String BASE_URL = "http://localhost:7878";
    private static final String API_KEY = "test-key";

    private static final Map<String, Object> COMMIT_BODY =
            Map.of("idempotency_key", "com-1", "actual", Map.of("unit", "TOKENS", "amount", 5));
    private static final Map<String, Object> EVENT_FALLBACK =
            Map.of("idempotency_key", "com-1", "subject", Map.of("tenant", "t1"),
                    "action", Map.of("kind", "llm", "name", "complete"),
                    "actual", Map.of("unit", "TOKENS", "amount", 5));

    @TempDir
    Path journalBase;

    private CyclesClient client;
    private CyclesProperties properties;

    @BeforeEach
    void setUp() {
        // IMPORTANT: reset the once-per-JVM replay claims so each test's identity
        // directory can be replayed, and point the journal at a @TempDir — never
        // the real user.home.
        JournaledCommitRetryEngine.resetReplayClaimsForTesting();
        client = mock(CyclesClient.class);
        properties = newProperties(API_KEY, null);
    }

    private CyclesProperties newProperties(String apiKey, String tenant) {
        CyclesProperties props = new CyclesProperties();
        props.setBaseUrl(BASE_URL);
        props.setApiKey(apiKey);
        props.setTenant(tenant);
        props.getJournal().setDir(journalBase.toString());
        CyclesProperties.Retry retry = props.getRetry();
        retry.setEnabled(true);
        retry.setMaxAttempts(3);
        retry.setInitialDelay(Duration.ofMillis(50));
        retry.setMultiplier(2.0);
        retry.setMaxDelay(Duration.ofMillis(500));
        retry.setFlushTimeout(Duration.ofSeconds(2));
        return props;
    }

    private Path identityDir(String apiKey, String tenant) {
        return journalBase.resolve(CommitJournal.authFingerprint(BASE_URL, apiKey, tenant));
    }

    private Path journalFile(String reservationId) {
        return identityDir(API_KEY, null).resolve(reservationId + ".json");
    }

    private static CyclesResponse<Map<String, Object>> expiredResponse() {
        return CyclesResponse.httpError(410, "Expired",
                Map.of("error", "RESERVATION_EXPIRED", "message", "Expired", "request_id", "r1"));
    }

    // ========================================================================
    // Journal on schedule, discard on success
    // ========================================================================

    @Nested
    @DisplayName("Journal on schedule, discard on success")
    class JournalLifecycle {

        @Test
        void shouldJournalBeforeRetryAndDiscardOnSuccess() {
            when(client.commitReservation(eq("res-1"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "COMMITTED")));

            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            engine.schedule("res-1", COMMIT_BODY, EVENT_FALLBACK, null);

            // Journaled synchronously, before the first background attempt
            assertThat(Files.exists(journalFile("res-1"))).isTrue();

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                verify(client).commitReservation(eq("res-1"), any(Object.class));
                assertThat(Files.exists(journalFile("res-1"))).isFalse();
            });
        }

        @Test
        void shouldDiscardOnGenuine4xxRejection() {
            when(client.commitReservation(eq("res-400"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(400, "Bad request",
                            Map.of("error", "INVALID_REQUEST", "message", "Bad request", "request_id", "r1")));

            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            engine.schedule("res-400", COMMIT_BODY, EVENT_FALLBACK, null);

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                verify(client, times(1)).commitReservation(eq("res-400"), any(Object.class));
                assertThat(Files.exists(journalFile("res-400"))).isFalse();
            });
            // No further attempts
            await().during(300, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(1)).commitReservation(eq("res-400"), any(Object.class)));
        }

        @Test
        void shouldRetainJournalOnExhaustion() {
            when(client.commitReservation(eq("res-ex"), any(Object.class)))
                    .thenReturn(CyclesResponse.transportError(new RuntimeException("always down")));

            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            engine.schedule("res-ex", COMMIT_BODY, EVENT_FALLBACK, null);

            await().atMost(5, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(3)).commitReservation(eq("res-ex"), any(Object.class)));
            await().during(300, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(3)).commitReservation(eq("res-ex"), any(Object.class)));
            assertThat(Files.exists(journalFile("res-ex"))).isTrue();
        }

        @Test
        void shouldRetainJournalOnAuthFailure() {
            when(client.commitReservation(eq("res-401"), any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(401, "Unauthorized",
                            Map.of("error", "UNAUTHORIZED", "message", "Unauthorized", "request_id", "r1")));

            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            engine.schedule("res-401", COMMIT_BODY, EVENT_FALLBACK, null);

            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(1)).commitReservation(eq("res-401"), any(Object.class)));
            // Terminal for this run — no more attempts, but the journal entry survives
            await().during(300, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(1)).commitReservation(eq("res-401"), any(Object.class)));
            assertThat(Files.exists(journalFile("res-401"))).isTrue();
            verify(client, never()).createEvent(any(Object.class));
        }
    }

    // ========================================================================
    // Expired -> event fallback
    // ========================================================================

    @Nested
    @DisplayName("Expired reservation -> /v1/events fallback")
    class ExpiredFallback {

        @Test
        void shouldDeliverEventFallbackImmediatelyWhenExpired() {
            when(client.commitReservation(eq("res-exp"), any(Object.class)))
                    .thenReturn(expiredResponse());
            when(client.createEvent(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "RECORDED", "event_id", "ev-1")));

            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            engine.schedule("res-exp", COMMIT_BODY, EVENT_FALLBACK, null);

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                verify(client).commitReservation(eq("res-exp"), any(Object.class));
                verify(client).createEvent(eq(EVENT_FALLBACK));
                assertThat(Files.exists(journalFile("res-exp"))).isFalse();
            });
        }

        @Test
        void shouldRetainJournalWhenExpiredWithoutFallback() {
            when(client.commitReservation(eq("res-nofb"), any(Object.class)))
                    .thenReturn(expiredResponse());

            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            engine.schedule("res-nofb", COMMIT_BODY, null, null);

            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(1)).commitReservation(eq("res-nofb"), any(Object.class)));
            await().during(300, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(1)).commitReservation(eq("res-nofb"), any(Object.class)));
            verify(client, never()).createEvent(any(Object.class));
            assertThat(Files.exists(journalFile("res-nofb"))).isTrue();
        }

        @Test
        void shouldScheduleEventDirectlyAndDiscardOnSuccess() {
            when(client.createEvent(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "RECORDED", "event_id", "ev-2")));

            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            engine.scheduleEvent("res-ev", EVENT_FALLBACK);

            assertThat(Files.exists(journalFile("res-ev"))).isTrue();
            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                verify(client).createEvent(eq(EVENT_FALLBACK));
                verify(client, never()).commitReservation(anyString(), any(Object.class));
                assertThat(Files.exists(journalFile("res-ev"))).isFalse();
            });
        }

        @Test
        void shouldDiscardWhenEventFallbackRejectedWith4xx() {
            when(client.createEvent(any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(400, "Bad event",
                            Map.of("error", "INVALID_REQUEST", "message", "Bad event", "request_id", "r1")));

            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            engine.scheduleEvent("res-ev4", EVENT_FALLBACK);

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                verify(client, times(1)).createEvent(any(Object.class));
                assertThat(Files.exists(journalFile("res-ev4"))).isFalse();
            });
        }

        @Test
        void shouldRetainJournalWhenEventFallbackHitsAuthFailure() {
            when(client.createEvent(any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(403, "Forbidden",
                            Map.of("error", "FORBIDDEN", "message", "Forbidden", "request_id", "r1")));

            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            engine.scheduleEvent("res-ev403", EVENT_FALLBACK);

            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(1)).createEvent(any(Object.class)));
            await().during(300, TimeUnit.MILLISECONDS).atMost(2, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(1)).createEvent(any(Object.class)));
            assertThat(Files.exists(journalFile("res-ev403"))).isTrue();
        }

        @Test
        void shouldKeepRetryingEventFallbackOn5xx() {
            when(client.createEvent(any(Object.class)))
                    .thenReturn(CyclesResponse.httpError(500, "Server error", Map.of()))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "RECORDED")));

            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            engine.scheduleEvent("res-ev5", EVENT_FALLBACK);

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                verify(client, times(2)).createEvent(any(Object.class));
                assertThat(Files.exists(journalFile("res-ev5"))).isFalse();
            });
        }
    }

    // ========================================================================
    // Rate limiting (429 / LIMIT_EXCEEDED)
    // ========================================================================

    @Nested
    @DisplayName("Rate limiting")
    class RateLimiting {

        @Test
        void shouldTreat429AsTransientHonorRetryAfterFloorAndPersistNotBefore() throws Exception {
            List<Long> callTimes = new CopyOnWriteArrayList<>();
            when(client.commitReservation(eq("res-429"), any(Object.class)))
                    .thenAnswer(inv -> {
                        callTimes.add(System.currentTimeMillis());
                        return callTimes.size() == 1
                                ? CyclesResponse.httpError(429, "Rate limited",
                                        Map.of("error", "LIMIT_EXCEEDED", "message", "Rate limited",
                                                "request_id", "r1"), 600)
                                : CyclesResponse.success(200, Map.of("status", "COMMITTED"));
                    });

            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            engine.schedule("res-429", COMMIT_BODY, EVENT_FALLBACK, null);

            // After the rate-limited attempt the floor is persisted as not_before_ms
            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(callTimes).hasSize(1));
            PendingCommitRecord persisted = PendingCommitRecord.fromJson(
                    Files.readString(journalFile("res-429")));
            assertThat(persisted.getNotBeforeMs()).isNotNull()
                    .isGreaterThan(callTimes.get(0));

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
                assertThat(callTimes).hasSize(2);
                assertThat(Files.exists(journalFile("res-429"))).isFalse();
            });
            // The Retry-After floor (600ms) exceeds the backoff (100ms) and must be honored
            assertThat(callTimes.get(1) - callTimes.get(0)).isGreaterThanOrEqualTo(500L);
        }

        @Test
        void shouldPassScheduledRetryAfterToFirstBackgroundAttempt() {
            List<Long> callTimes = new CopyOnWriteArrayList<>();
            when(client.commitReservation(eq("res-ra"), any(Object.class)))
                    .thenAnswer(inv -> {
                        callTimes.add(System.currentTimeMillis());
                        return CyclesResponse.success(200, Map.of("status", "COMMITTED"));
                    });

            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            long scheduledAt = System.currentTimeMillis();
            engine.schedule("res-ra", COMMIT_BODY, EVENT_FALLBACK, 600);

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(callTimes).hasSize(1));
            // First attempt honors the 600ms Retry-After floor instead of the 50ms backoff
            assertThat(callTimes.get(0) - scheduledAt).isGreaterThanOrEqualTo(500L);
        }
    }

    // ========================================================================
    // Disabled retry / journal
    // ========================================================================

    @Nested
    @DisplayName("Disabled retry and journal")
    class Disabled {

        @Test
        void shouldStillJournalWhenRetryDisabled() throws Exception {
            properties.getRetry().setEnabled(false);
            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);

            engine.schedule("res-dis", COMMIT_BODY, EVENT_FALLBACK, null);

            assertThat(Files.exists(journalFile("res-dis"))).isTrue();
            Thread.sleep(200);
            verify(client, never()).commitReservation(anyString(), any(Object.class));
        }

        @Test
        void shouldDropWhenRetryAndJournalBothDisabled() throws Exception {
            properties.getRetry().setEnabled(false);
            properties.getJournal().setEnabled(false);
            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);

            engine.schedule("res-drop", COMMIT_BODY, EVENT_FALLBACK, null);

            assertThat(Files.exists(journalFile("res-drop"))).isFalse();
            Thread.sleep(200);
            verify(client, never()).commitReservation(anyString(), any(Object.class));
        }

        @Test
        void shouldDisableJournalWhenIdentityIncomplete() throws Exception {
            properties.setApiKey(null);
            properties.setTenant(null);
            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            when(client.commitReservation(eq("res-noid"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "COMMITTED")));

            engine.schedule("res-noid", COMMIT_BODY, EVENT_FALLBACK, null);

            // No identity -> no journal directory created anywhere under the base
            try (var stream = Files.list(journalBase)) {
                assertThat(stream).isEmpty();
            }
            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client).commitReservation(eq("res-noid"), any(Object.class)));
        }
    }

    // ========================================================================
    // Replay
    // ========================================================================

    @Nested
    @DisplayName("Replay on startup")
    class Replay {

        private void writeRecord(String apiKey, String tenant, PendingCommitRecord record) {
            new CommitJournal(identityDir(apiKey, tenant)).record(record);
        }

        @Test
        void shouldReplayJournaledCommitOnConstruction() {
            // A previous run journaled a pending commit but never settled it
            CyclesProperties disabled = newProperties(API_KEY, null);
            disabled.getRetry().setEnabled(false);
            new JournaledCommitRetryEngine(client, disabled)
                    .schedule("res-replay", COMMIT_BODY, EVENT_FALLBACK, null);
            assertThat(Files.exists(journalFile("res-replay"))).isTrue();

            when(client.commitReservation(eq("res-replay"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "COMMITTED")));

            JournaledCommitRetryEngine.resetReplayClaimsForTesting();
            new JournaledCommitRetryEngine(client, properties);

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                verify(client).commitReservation(eq("res-replay"), eq(COMMIT_BODY));
                assertThat(Files.exists(journalFile("res-replay"))).isFalse();
            });
        }

        @Test
        void shouldReplayEventModeRecord() {
            writeRecord(API_KEY, null, new PendingCommitRecord("res-ev-replay", BASE_URL,
                    PendingCommitRecord.MODE_EVENT, null, EVENT_FALLBACK,
                    System.currentTimeMillis(), null));
            when(client.createEvent(any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "RECORDED")));

            new JournaledCommitRetryEngine(client, properties);

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                verify(client).createEvent(eq(EVENT_FALLBACK));
                verify(client, never()).commitReservation(anyString(), any(Object.class));
                assertThat(Files.exists(journalFile("res-ev-replay"))).isFalse();
            });
        }

        @Test
        void shouldReplayOnlyOncePerIdentityPerJvm() throws Exception {
            writeRecord(API_KEY, null, new PendingCommitRecord("res-once", BASE_URL,
                    PendingCommitRecord.MODE_COMMIT, COMMIT_BODY, EVENT_FALLBACK,
                    System.currentTimeMillis(), null));
            when(client.commitReservation(eq("res-once"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "COMMITTED")));

            new JournaledCommitRetryEngine(client, properties);
            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(1)).commitReservation(eq("res-once"), any(Object.class)));

            // Re-journal the same record and build a second engine for the same
            // identity: the claim is already held, so nothing replays again.
            writeRecord(API_KEY, null, new PendingCommitRecord("res-once", BASE_URL,
                    PendingCommitRecord.MODE_COMMIT, COMMIT_BODY, EVENT_FALLBACK,
                    System.currentTimeMillis(), null));
            new JournaledCommitRetryEngine(client, properties);
            Thread.sleep(300);
            verify(client, times(1)).commitReservation(eq("res-once"), any(Object.class));

            // After a claims reset (fresh JVM), the surviving record replays
            JournaledCommitRetryEngine.resetReplayClaimsForTesting();
            new JournaledCommitRetryEngine(client, properties);
            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client, times(2)).commitReservation(eq("res-once"), any(Object.class)));
        }

        @Test
        void shouldNotReplayAnotherCredentialsRecords() throws Exception {
            writeRecord("other-key", null, new PendingCommitRecord("res-foreign", BASE_URL,
                    PendingCommitRecord.MODE_COMMIT, COMMIT_BODY, EVENT_FALLBACK,
                    System.currentTimeMillis(), null));

            new JournaledCommitRetryEngine(client, properties);
            Thread.sleep(300);

            verify(client, never()).commitReservation(anyString(), any(Object.class));
            assertThat(Files.exists(identityDir("other-key", null).resolve("res-foreign.json"))).isTrue();
        }

        @Test
        void shouldSurviveApiKeyRotationWhenTenantScoped() {
            // Recorded under (tenant acme, old key); replayed under (tenant acme, new key)
            writeRecord("old-key", "acme", new PendingCommitRecord("res-rot", BASE_URL,
                    PendingCommitRecord.MODE_COMMIT, COMMIT_BODY, EVENT_FALLBACK,
                    System.currentTimeMillis(), null));
            when(client.commitReservation(eq("res-rot"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "COMMITTED")));

            new JournaledCommitRetryEngine(client, newProperties("new-key", "acme"));

            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client).commitReservation(eq("res-rot"), any(Object.class)));
        }

        @Test
        void shouldNotReplayRecordsForAnotherBaseUrl() throws Exception {
            new CommitJournal(identityDir(API_KEY, null)).record(new PendingCommitRecord(
                    "res-other-server", "http://other:9999", PendingCommitRecord.MODE_COMMIT,
                    COMMIT_BODY, EVENT_FALLBACK, System.currentTimeMillis(), null));

            new JournaledCommitRetryEngine(client, properties);
            Thread.sleep(300);

            verify(client, never()).commitReservation(anyString(), any(Object.class));
            assertThat(Files.exists(journalFile("res-other-server"))).isTrue();
        }

        @Test
        void shouldHonorFutureNotBeforeFloorOnReplay() {
            List<Long> callTimes = new CopyOnWriteArrayList<>();
            long floor = System.currentTimeMillis() + 600;
            writeRecord(API_KEY, null, new PendingCommitRecord("res-floor", BASE_URL,
                    PendingCommitRecord.MODE_COMMIT, COMMIT_BODY, EVENT_FALLBACK,
                    System.currentTimeMillis(), floor));
            when(client.commitReservation(eq("res-floor"), any(Object.class)))
                    .thenAnswer(inv -> {
                        callTimes.add(System.currentTimeMillis());
                        return CyclesResponse.success(200, Map.of("status", "COMMITTED"));
                    });

            new JournaledCommitRetryEngine(client, properties);

            await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(callTimes).hasSize(1));
            assertThat(callTimes.get(0)).isGreaterThanOrEqualTo(floor - 100);
        }

        @Test
        void shouldFallBackToNormalBackoffWhenNotBeforeInThePast() {
            List<Long> callTimes = new CopyOnWriteArrayList<>();
            long replayStart = System.currentTimeMillis();
            writeRecord(API_KEY, null, new PendingCommitRecord("res-past", BASE_URL,
                    PendingCommitRecord.MODE_COMMIT, COMMIT_BODY, EVENT_FALLBACK,
                    System.currentTimeMillis(), System.currentTimeMillis() - 60_000));
            when(client.commitReservation(eq("res-past"), any(Object.class)))
                    .thenAnswer(inv -> {
                        callTimes.add(System.currentTimeMillis());
                        return CyclesResponse.success(200, Map.of("status", "COMMITTED"));
                    });

            new JournaledCommitRetryEngine(client, properties);

            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> assertThat(callTimes).hasSize(1));
            // Normal 50ms backoff — nowhere near a 60s wait
            assertThat(callTimes.get(0) - replayStart).isLessThan(2_000L);
        }
    }

    // ========================================================================
    // Flush
    // ========================================================================

    @Nested
    @DisplayName("Flush")
    class Flush {

        @Test
        void shouldBoundFlushByTimeoutAndRetainJournal() {
            properties.getRetry().setInitialDelay(Duration.ofSeconds(30));
            properties.getRetry().setMaxDelay(Duration.ofSeconds(30));
            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            engine.schedule("res-flush", COMMIT_BODY, EVENT_FALLBACK, null);

            long start = System.currentTimeMillis();
            engine.flush(Duration.ofMillis(200));
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isLessThan(5_000L);
            verify(client, never()).commitReservation(anyString(), any(Object.class));
            assertThat(Files.exists(journalFile("res-flush"))).isTrue();
        }

        @Test
        void shouldUseConfiguredFlushTimeoutOnDestroy() {
            properties.getRetry().setInitialDelay(Duration.ofSeconds(30));
            properties.getRetry().setMaxDelay(Duration.ofSeconds(30));
            properties.getRetry().setFlushTimeout(Duration.ofMillis(100));
            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            engine.schedule("res-destroy", COMMIT_BODY, EVENT_FALLBACK, null);

            long start = System.currentTimeMillis();
            engine.destroy();
            long elapsed = System.currentTimeMillis() - start;

            assertThat(elapsed).isLessThan(5_000L);
            assertThat(Files.exists(journalFile("res-destroy"))).isTrue();
        }

        @Test
        void shouldFlushWithNullTimeoutUsingConfiguredDefault() {
            properties.getRetry().setFlushTimeout(Duration.ofMillis(100));
            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);

            long start = System.currentTimeMillis();
            engine.flush(null);
            assertThat(System.currentTimeMillis() - start).isLessThan(5_000L);
        }

        @Test
        void shouldRetainJournalWhenScheduledAfterShutdown() {
            JournaledCommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            engine.flush(Duration.ofMillis(100));

            // Executor is shut down; the schedule is rejected but the journal survives
            engine.schedule("res-late", COMMIT_BODY, EVENT_FALLBACK, null);
            assertThat(Files.exists(journalFile("res-late"))).isTrue();
            verify(client, never()).commitReservation(anyString(), any(Object.class));
        }
    }

    // ========================================================================
    // Backward-compatible default schedule
    // ========================================================================

    @Nested
    @DisplayName("Deprecated two-arg schedule")
    class LegacySchedule {

        @Test
        void shouldDelegateWithoutFallback() {
            when(client.commitReservation(eq("res-legacy"), any(Object.class)))
                    .thenReturn(CyclesResponse.success(200, Map.of("status", "COMMITTED")));

            CommitRetryEngine engine = new JournaledCommitRetryEngine(client, properties);
            engine.schedule("res-legacy", (Object) COMMIT_BODY);

            await().atMost(3, TimeUnit.SECONDS)
                    .untilAsserted(() -> verify(client).commitReservation(eq("res-legacy"), eq(COMMIT_BODY)));
        }
    }
}
