package io.runcycles.client.java.spring.autoconfigure;

import io.runcycles.client.java.spring.annotation.Cycles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CyclesSelfInvocationDetector")
@ExtendWith(MockitoExtension.class)
class CyclesSelfInvocationDetectorTest {

    private final CyclesSelfInvocationDetector detector = new CyclesSelfInvocationDetector();

    // --- Test fixtures ---

    /** Bean with mixed annotated/non-annotated public methods — should warn. */
    public static class MixedService {
        public String publicHelper() { return "helper"; }

        @Cycles("1000")
        public String guardedMethod() { return "guarded"; }
    }

    /** Bean where ALL public methods have @Cycles — no warning needed. */
    public static class FullyAnnotatedService {
        @Cycles("1000")
        public String method1() { return "a"; }

        @Cycles("2000")
        public String method2() { return "b"; }
    }

    /** Bean with no @Cycles at all — no warning. */
    public static class PlainService {
        public String doWork() { return "work"; }
    }

    /** Bean with a single @Cycles method and no other public methods — no warning. */
    public static class SingleAnnotatedService {
        @Cycles("500")
        public String onlyMethod() { return "only"; }
    }

    // --- Helpers ---

    private ListAppender<ILoggingEvent> captureDetectorLogs() {
        Logger logger = (Logger) LoggerFactory.getLogger(CyclesSelfInvocationDetector.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    // --- Tests ---

    @Nested
    @DisplayName("Warning detection")
    class WarningDetection {

        @Test
        void shouldWarnForMixedAnnotatedAndNonAnnotatedMethods() {
            ListAppender<ILoggingEvent> appender = captureDetectorLogs();

            detector.postProcessAfterInitialization(new MixedService(), "mixedService");

            assertThat(appender.list)
                    .anyMatch(event -> event.getLevel() == Level.WARN
                            && event.getFormattedMessage().contains("MixedService")
                            && event.getFormattedMessage().contains("@Cycles")
                            && event.getFormattedMessage().contains("self-invocation"));
        }

        @Test
        void shouldNotWarnWhenAllPublicMethodsAnnotated() {
            ListAppender<ILoggingEvent> appender = captureDetectorLogs();

            detector.postProcessAfterInitialization(new FullyAnnotatedService(), "fullyAnnotated");

            assertThat(appender.list)
                    .noneMatch(event -> event.getLevel() == Level.WARN);
        }

        @Test
        void shouldNotWarnWhenNoMethodsAnnotated() {
            ListAppender<ILoggingEvent> appender = captureDetectorLogs();

            detector.postProcessAfterInitialization(new PlainService(), "plainService");

            assertThat(appender.list)
                    .noneMatch(event -> event.getLevel() == Level.WARN);
        }

        @Test
        void shouldNotWarnForSingleAnnotatedMethod() {
            ListAppender<ILoggingEvent> appender = captureDetectorLogs();

            detector.postProcessAfterInitialization(new SingleAnnotatedService(), "singleAnnotated");

            assertThat(appender.list)
                    .noneMatch(event -> event.getLevel() == Level.WARN);
        }
    }

    @Nested
    @DisplayName("Framework class filtering")
    class FrameworkFiltering {

        @Test
        void shouldReturnBeanUnmodified() {
            MixedService bean = new MixedService();
            Object result = detector.postProcessAfterInitialization(bean, "test");
            assertThat(result).isSameAs(bean);
        }
    }
}
