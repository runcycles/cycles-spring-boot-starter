package io.runcycles.client.java.spring.client;

import io.runcycles.client.java.spring.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("CyclesClient default methods")
class CyclesClientDefaultMethodsTest {

    @SuppressWarnings("unchecked")
    private CyclesClient createMockClient() {
        CyclesClient client = mock(CyclesClient.class);
        CyclesResponse<Map<String, Object>> response = CyclesResponse.success(200, Map.of("decision", "ALLOW"));

        // Stub the raw Object-accepting methods
        when(client.createReservation(any(Object.class))).thenReturn(response);
        when(client.commitReservation(anyString(), any(Object.class))).thenReturn(response);
        when(client.releaseReservation(anyString(), any(Object.class))).thenReturn(response);
        when(client.extendReservation(anyString(), any(Object.class))).thenReturn(response);
        when(client.decide(any(Object.class))).thenReturn(response);
        when(client.createEvent(any(Object.class))).thenReturn(response);

        // Call real default methods
        when(client.createReservation(any(ReservationCreateRequest.class))).thenCallRealMethod();
        when(client.commitReservation(anyString(), any(CommitRequest.class))).thenCallRealMethod();
        when(client.releaseReservation(anyString(), any(ReleaseRequest.class))).thenCallRealMethod();
        when(client.extendReservation(anyString(), any(ReservationExtendRequest.class))).thenCallRealMethod();
        when(client.decide(any(DecisionRequest.class))).thenCallRealMethod();
        when(client.createEvent(any(EventCreateRequest.class))).thenCallRealMethod();

        return client;
    }

    @Test
    void createReservationTyped() {
        CyclesClient client = createMockClient();
        var req = ReservationCreateRequest.builder()
                .subject(Subject.builder().tenant("t").build())
                .action(new Action("k", "n", null))
                .estimate(new Amount(Unit.TOKENS, 100L))
                .build();

        CyclesResponse<Map<String, Object>> resp = client.createReservation(req);
        assertThat(resp.is2xx()).isTrue();
        verify(client).createReservation(any(Object.class));
    }

    @Test
    void commitReservationTyped() {
        CyclesClient client = createMockClient();
        var req = CommitRequest.builder()
                .actual(new Amount(Unit.TOKENS, 100L))
                .build();

        CyclesResponse<Map<String, Object>> resp = client.commitReservation("res-1", req);
        assertThat(resp.is2xx()).isTrue();
        verify(client).commitReservation(eq("res-1"), any(Object.class));
    }

    @Test
    void releaseReservationTyped() {
        CyclesClient client = createMockClient();
        var req = ReleaseRequest.builder().reason("done").build();

        CyclesResponse<Map<String, Object>> resp = client.releaseReservation("res-1", req);
        assertThat(resp.is2xx()).isTrue();
        verify(client).releaseReservation(eq("res-1"), any(Object.class));
    }

    @Test
    void extendReservationTyped() {
        CyclesClient client = createMockClient();
        var req = ReservationExtendRequest.builder().extendByMs(5000L).build();

        CyclesResponse<Map<String, Object>> resp = client.extendReservation("res-1", req);
        assertThat(resp.is2xx()).isTrue();
        verify(client).extendReservation(eq("res-1"), any(Object.class));
    }

    @Test
    void decideTyped() {
        CyclesClient client = createMockClient();
        var req = DecisionRequest.builder()
                .subject(Subject.builder().tenant("t").build())
                .action(new Action("k", "n", null))
                .estimate(new Amount(Unit.TOKENS, 100L))
                .build();

        CyclesResponse<Map<String, Object>> resp = client.decide(req);
        assertThat(resp.is2xx()).isTrue();
        verify(client).decide(any(Object.class));
    }

    @Test
    void createEventTyped() {
        CyclesClient client = createMockClient();
        var req = EventCreateRequest.builder()
                .subject(Subject.builder().tenant("t").build())
                .action(new Action("k", "n", null))
                .actual(new Amount(Unit.TOKENS, 100L))
                .build();

        CyclesResponse<Map<String, Object>> resp = client.createEvent(req);
        assertThat(resp.is2xx()).isTrue();
        verify(client).createEvent(any(Object.class));
    }
}
