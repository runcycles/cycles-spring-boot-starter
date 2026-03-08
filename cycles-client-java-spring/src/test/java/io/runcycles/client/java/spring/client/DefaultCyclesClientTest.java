package io.runcycles.client.java.spring.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.runcycles.client.java.spring.model.CyclesResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DefaultCyclesClient")
class DefaultCyclesClientTest {

    private MockWebServer server;
    private DefaultCyclesClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        WebClient webClient = WebClient.builder()
                .baseUrl(server.url("/").toString())
                .defaultHeader("X-Cycles-API-Key", "test-key")
                .build();
        client = new DefaultCyclesClient(webClient);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private void enqueueJson(int status, Map<String, Object> body) throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(mapper.writeValueAsString(body)));
    }

    // ========================================================================
    // POST endpoints
    // ========================================================================

    @Nested
    @DisplayName("createReservation")
    class CreateReservation {

        @Test
        void shouldPostToCorrectPath() throws Exception {
            enqueueJson(200, Map.of("decision", "ALLOW", "reservation_id", "res-1"));

            Map<String, Object> body = new HashMap<>();
            body.put("idempotency_key", "idem-1");
            body.put("subject", Map.of("tenant", "t1"));
            body.put("action", Map.of("kind", "test", "name", "test"));
            body.put("estimate", Map.of("unit", "TOKENS", "amount", 100));

            CyclesResponse<Map<String, Object>> resp = client.createReservation(body);

            assertThat(resp.is2xx()).isTrue();
            assertThat(resp.getBody().get("reservation_id")).isEqualTo("res-1");

            RecordedRequest req = server.takeRequest();
            assertThat(req.getMethod()).isEqualTo("POST");
            assertThat(req.getPath()).isEqualTo("/v1/reservations");
        }

        @Test
        void shouldSetIdempotencyHeader() throws Exception {
            enqueueJson(200, Map.of("decision", "ALLOW", "reservation_id", "res-1"));

            Map<String, Object> body = new HashMap<>();
            body.put("idempotency_key", "my-idem-key");

            client.createReservation(body);

            RecordedRequest req = server.takeRequest();
            assertThat(req.getHeader("X-Idempotency-Key")).isEqualTo("my-idem-key");
        }

        @Test
        void shouldHandleHttpError() throws Exception {
            enqueueJson(409, Map.of("error", "BUDGET_EXCEEDED",
                    "message", "Insufficient budget", "request_id", "req-1"));

            CyclesResponse<Map<String, Object>> resp = client.createReservation(Map.of(
                    "idempotency_key", "idem-1"));

            assertThat(resp.is2xx()).isFalse();
            assertThat(resp.getStatus()).isEqualTo(409);
            assertThat(resp.getErrorMessage()).isEqualTo("Insufficient budget");
        }
    }

    @Nested
    @DisplayName("commitReservation")
    class CommitReservation {

        @Test
        void shouldPostToCorrectPath() throws Exception {
            enqueueJson(200, Map.of("status", "COMMITTED",
                    "charged", Map.of("unit", "TOKENS", "amount", 800)));

            CyclesResponse<Map<String, Object>> resp = client.commitReservation("res-123",
                    Map.of("idempotency_key", "com-1",
                            "actual", Map.of("unit", "TOKENS", "amount", 800)));

            assertThat(resp.is2xx()).isTrue();

            RecordedRequest req = server.takeRequest();
            assertThat(req.getPath()).isEqualTo("/v1/reservations/res-123/commit");
        }
    }

    @Nested
    @DisplayName("releaseReservation")
    class ReleaseReservation {

        @Test
        void shouldPostToCorrectPath() throws Exception {
            enqueueJson(200, Map.of("status", "RELEASED",
                    "released", Map.of("unit", "TOKENS", "amount", 1000)));

            CyclesResponse<Map<String, Object>> resp = client.releaseReservation("res-123",
                    Map.of("idempotency_key", "rel-1"));

            assertThat(resp.is2xx()).isTrue();

            RecordedRequest req = server.takeRequest();
            assertThat(req.getPath()).isEqualTo("/v1/reservations/res-123/release");
        }
    }

    @Nested
    @DisplayName("extendReservation")
    class ExtendReservation {

        @Test
        void shouldPostToCorrectPath() throws Exception {
            enqueueJson(200, Map.of("status", "ACTIVE", "expires_at_ms", 1700000090000L));

            CyclesResponse<Map<String, Object>> resp = client.extendReservation("res-123",
                    Map.of("idempotency_key", "ext-1", "extend_by_ms", 30000));

            assertThat(resp.is2xx()).isTrue();

            RecordedRequest req = server.takeRequest();
            assertThat(req.getPath()).isEqualTo("/v1/reservations/res-123/extend");
        }
    }

    @Nested
    @DisplayName("decide")
    class Decide {

        @Test
        void shouldPostToCorrectPath() throws Exception {
            enqueueJson(200, Map.of("decision", "ALLOW"));

            CyclesResponse<Map<String, Object>> resp = client.decide(
                    Map.of("idempotency_key", "dec-1",
                            "subject", Map.of("tenant", "t1"),
                            "action", Map.of("kind", "test", "name", "test"),
                            "estimate", Map.of("unit", "TOKENS", "amount", 100)));

            assertThat(resp.is2xx()).isTrue();
            assertThat(resp.getBody().get("decision")).isEqualTo("ALLOW");

            RecordedRequest req = server.takeRequest();
            assertThat(req.getPath()).isEqualTo("/v1/decide");
        }
    }

    @Nested
    @DisplayName("createEvent")
    class CreateEvent {

        @Test
        void shouldPostToCorrectPath() throws Exception {
            enqueueJson(201, Map.of("status", "APPLIED", "event_id", "evt-1"));

            CyclesResponse<Map<String, Object>> resp = client.createEvent(
                    Map.of("idempotency_key", "evt-1",
                            "subject", Map.of("tenant", "t1"),
                            "action", Map.of("kind", "test", "name", "test"),
                            "actual", Map.of("unit", "TOKENS", "amount", 500)));

            assertThat(resp.is2xx()).isTrue();
            assertThat(resp.getBody().get("event_id")).isEqualTo("evt-1");

            RecordedRequest req = server.takeRequest();
            assertThat(req.getPath()).isEqualTo("/v1/events");
        }
    }

    // ========================================================================
    // GET endpoints
    // ========================================================================

    @Nested
    @DisplayName("getReservation")
    class GetReservation {

        @Test
        void shouldGetToCorrectPath() throws Exception {
            enqueueJson(200, Map.of("reservation_id", "res-123", "status", "ACTIVE"));

            CyclesResponse<Map<String, Object>> resp = client.getReservation("res-123");

            assertThat(resp.is2xx()).isTrue();
            assertThat(resp.getBody().get("reservation_id")).isEqualTo("res-123");

            RecordedRequest req = server.takeRequest();
            assertThat(req.getMethod()).isEqualTo("GET");
            assertThat(req.getPath()).isEqualTo("/v1/reservations/res-123");
        }
    }

    @Nested
    @DisplayName("listReservations")
    class ListReservations {

        @Test
        void shouldIncludeQueryParams() throws Exception {
            enqueueJson(200, Map.of("reservations", java.util.List.of()));

            client.listReservations(Map.of("status", "ACTIVE", "tenant", "t1"));

            RecordedRequest req = server.takeRequest();
            assertThat(req.getMethod()).isEqualTo("GET");
            String path = req.getPath();
            assertThat(path).contains("status=ACTIVE");
            assertThat(path).contains("tenant=t1");
        }
    }

    @Nested
    @DisplayName("getBalances")
    class GetBalances {

        @Test
        void shouldIncludeQueryParams() throws Exception {
            enqueueJson(200, Map.of("balances", java.util.List.of()));

            client.getBalances(Map.of("tenant", "t1", "include_children", "true"));

            RecordedRequest req = server.takeRequest();
            assertThat(req.getMethod()).isEqualTo("GET");
            String path = req.getPath();
            assertThat(path).contains("tenant=t1");
            assertThat(path).contains("include_children=true");
        }
    }

    // ========================================================================
    // Error handling
    // ========================================================================

    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {

        @Test
        void shouldParseStructuredError() throws Exception {
            enqueueJson(409, Map.of(
                    "error", "BUDGET_EXCEEDED",
                    "message", "Insufficient budget for scope tenant:t1",
                    "request_id", "req-abc"
            ));

            CyclesResponse<Map<String, Object>> resp = client.createReservation(
                    Map.of("idempotency_key", "x"));

            assertThat(resp.is2xx()).isFalse();
            assertThat(resp.getStatus()).isEqualTo(409);
            assertThat(resp.getErrorMessage()).isEqualTo("Insufficient budget for scope tenant:t1");
        }

        @Test
        void shouldHandleEmptyBody() throws Exception {
            server.enqueue(new MockResponse()
                    .setResponseCode(204)
                    .setHeader("Content-Type", "application/json"));

            CyclesResponse<Map<String, Object>> resp = client.getReservation("res-1");

            // 204 is 2xx, body should be empty map
            assertThat(resp.is2xx()).isTrue();
            assertThat(resp.getBody()).isEmpty();
        }

        @Test
        void shouldHandleTransportError() {
            // Shut down server to simulate transport error
            try { server.shutdown(); } catch (Exception e) { /* ignore */ }

            CyclesResponse<Map<String, Object>> resp = client.createReservation(
                    Map.of("idempotency_key", "x"));

            assertThat(resp.isTransportError()).isTrue();
        }
    }

    // ========================================================================
    // Idempotency key extraction
    // ========================================================================

    @Nested
    @DisplayName("Idempotency Key Extraction")
    class IdempotencyKeyExtraction {

        @Test
        void shouldExtractFromMap() throws Exception {
            enqueueJson(200, Map.of("decision", "ALLOW"));

            client.createReservation(Map.of("idempotency_key", "my-key-123"));

            RecordedRequest req = server.takeRequest();
            assertThat(req.getHeader("X-Idempotency-Key")).isEqualTo("my-key-123");
        }

        @Test
        void shouldNotSetHeaderWhenKeyAbsent() throws Exception {
            enqueueJson(200, Map.of("decision", "ALLOW"));

            client.createReservation(Map.of("other_field", "value"));

            RecordedRequest req = server.takeRequest();
            assertThat(req.getHeader("X-Idempotency-Key")).isNull();
        }
    }
}
