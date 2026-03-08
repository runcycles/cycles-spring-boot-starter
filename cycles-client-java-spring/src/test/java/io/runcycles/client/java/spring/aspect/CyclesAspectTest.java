package io.runcycles.client.java.spring.aspect;

import io.runcycles.client.java.spring.annotation.Cycles;
import io.runcycles.client.java.spring.context.CyclesContextHolder;
import io.runcycles.client.java.spring.context.CyclesLifecycleService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CyclesAspect")
class CyclesAspectTest {

    private CyclesLifecycleService lifecycleService;
    private CyclesAspect aspect;

    @BeforeEach
    void setUp() {
        lifecycleService = mock(CyclesLifecycleService.class);
        aspect = new CyclesAspect(lifecycleService);
    }

    @AfterEach
    void tearDown() {
        CyclesContextHolder.clear();
    }

    // Dummy target class for method resolution
    public static class DummyService {
        public String doWork(int tokens) { return "result"; }
    }

    private ProceedingJoinPoint mockJoinPoint(String actionKind, String actionName) throws Exception {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        Method method = DummyService.class.getMethod("doWork", int.class);

        when(pjp.getSignature()).thenReturn(sig);
        when(sig.getMethod()).thenReturn(method);
        when(pjp.getTarget()).thenReturn(new DummyService());
        when(pjp.getArgs()).thenReturn(new Object[]{100});

        return pjp;
    }

    private Cycles mockCycles(String actionKind, String actionName) {
        Cycles cycles = mock(Cycles.class);
        when(cycles.actionKind()).thenReturn(actionKind);
        when(cycles.actionName()).thenReturn(actionName);
        return cycles;
    }

    // ========================================================================
    // Action name resolution
    // ========================================================================

    @Nested
    @DisplayName("Action name resolution")
    class ActionNameResolution {

        @Test
        void shouldUseClassNameAsDefaultActionKind() throws Throwable {
            ProceedingJoinPoint pjp = mockJoinPoint("", "");
            Cycles cycles = mockCycles("", "");

            when(lifecycleService.executeWithReservation(any(), any(), any(), any(), any(), anyString(), anyString()))
                    .thenReturn("ok");

            aspect.around(pjp, cycles);

            verify(lifecycleService).executeWithReservation(
                    any(), eq(cycles), any(), any(), any(),
                    eq("DummyService"), eq("doWork")
            );
        }

        @Test
        void shouldUseCustomActionKindAndName() throws Throwable {
            ProceedingJoinPoint pjp = mockJoinPoint("", "");
            Cycles cycles = mockCycles("llm.completion", "gpt-4");

            when(lifecycleService.executeWithReservation(any(), any(), any(), any(), any(), anyString(), anyString()))
                    .thenReturn("ok");

            aspect.around(pjp, cycles);

            verify(lifecycleService).executeWithReservation(
                    any(), eq(cycles), any(), any(), any(),
                    eq("llm.completion"), eq("gpt-4")
            );
        }
    }

    // ========================================================================
    // Nested @Cycles detection
    // ========================================================================

    @Nested
    @DisplayName("Nested @Cycles detection")
    class NestedCyclesDetection {

        @Test
        void shouldThrowOnNestedCycles() throws Throwable {
            ProceedingJoinPoint pjp = mockJoinPoint("", "");
            Cycles cycles = mockCycles("", "");

            // Simulate an active context (nested call)
            CyclesContextHolder.set(new io.runcycles.client.java.spring.context.CyclesReservationContext(
                    "existing-res", 1000, io.runcycles.client.java.spring.model.Decision.ALLOW,
                    null, null, null, null, null, null));

            assertThatThrownBy(() -> aspect.around(pjp, cycles))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Nested @Cycles not supported");
        }
    }

    // ========================================================================
    // Delegation
    // ========================================================================

    @Nested
    @DisplayName("Delegation to lifecycle service")
    class Delegation {

        @Test
        void shouldReturnLifecycleServiceResult() throws Throwable {
            ProceedingJoinPoint pjp = mockJoinPoint("", "");
            Cycles cycles = mockCycles("kind", "name");

            when(lifecycleService.executeWithReservation(any(), any(), any(), any(), any(), anyString(), anyString()))
                    .thenReturn("lifecycle-result");

            Object result = aspect.around(pjp, cycles);

            assertThat(result).isEqualTo("lifecycle-result");
        }

        @Test
        void shouldPropagateLifecycleServiceException() throws Throwable {
            ProceedingJoinPoint pjp = mockJoinPoint("", "");
            Cycles cycles = mockCycles("kind", "name");

            when(lifecycleService.executeWithReservation(any(), any(), any(), any(), any(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("lifecycle failed"));

            assertThatThrownBy(() -> aspect.around(pjp, cycles))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("lifecycle failed");
        }
    }
}
