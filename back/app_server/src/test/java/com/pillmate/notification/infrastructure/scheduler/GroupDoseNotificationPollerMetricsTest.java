package com.pillmate.notification.infrastructure.scheduler;

import com.pillmate.common.monitoring.SlackNotifier;
import com.pillmate.notification.application.NotifyDueGroupDosesUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroupDoseNotificationPollerMetricsTest {

    private SimpleMeterRegistry registry;
    private NotifyDueGroupDosesUseCase mockUseCase;
    private GroupDoseNotificationPoller poller;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        mockUseCase = mock(NotifyDueGroupDosesUseCase.class);
        poller = new GroupDoseNotificationPoller(mockUseCase, registry, mock(SlackNotifier.class));
    }

    @Test
    @DisplayName("정상 폴링 시 success 카운터가 증가한다")
    void poll_success_incrementsSuccessCounter() {
        when(mockUseCase.notifyDue()).thenReturn(2);

        poller.poll();

        assertThat(successCount()).isEqualTo(1.0);
        assertThat(failureCount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("예외 발생 시 failure 카운터가 증가하고 폴러가 중단되지 않는다")
    void poll_failure_incrementsFailureCounterWithoutThrowing() {
        when(mockUseCase.notifyDue()).thenThrow(new RuntimeException("connection refused"));

        poller.poll(); // must not throw

        assertThat(failureCount()).isEqualTo(1.0);
        assertThat(successCount()).isEqualTo(0.0);
    }

    private double successCount() {
        return registry.counter("pillmate.notification.poller.runs", "result", "success").count();
    }

    private double failureCount() {
        return registry.counter("pillmate.notification.poller.runs", "result", "failure").count();
    }
}
