package com.pillmate.notification.infrastructure.scheduler;

import com.pillmate.common.monitoring.SlackNotifier;
import com.pillmate.notification.application.NotifyDueDoseRemindersUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;

class DoseReminderPollerTest {

    private SimpleMeterRegistry registry;
    private NotifyDueDoseRemindersUseCase mockUseCase;
    private SlackNotifier slackNotifier;
    private DoseReminderPoller poller;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        mockUseCase = mock(NotifyDueDoseRemindersUseCase.class);
        slackNotifier = mock(SlackNotifier.class);
        poller = new DoseReminderPoller(mockUseCase, registry, slackNotifier);
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
    @DisplayName("예외 발생 시 failure 카운터 증가 + 폴러 중단 없음")
    void poll_failure_incrementsFailureCounterWithoutThrowing() {
        when(mockUseCase.notifyDue()).thenThrow(new RuntimeException("connection refused"));

        poller.poll();

        assertThat(failureCount()).isEqualTo(1.0);
        assertThat(successCount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("3회 연속 실패 시 Slack 알림 1회 (그룹 폴러 동일 임계)")
    void poll_threeConsecutiveFailures_alertsSlackOnce() {
        when(mockUseCase.notifyDue()).thenThrow(new RuntimeException("down"));

        poller.poll();
        poller.poll();
        verify(slackNotifier, never()).send(anyString());
        poller.poll();

        verify(slackNotifier).send(anyString());
    }

    private double successCount() {
        return registry.counter("pillmate.notification.reminder.poller.runs", "result", "success").count();
    }

    private double failureCount() {
        return registry.counter("pillmate.notification.reminder.poller.runs", "result", "failure").count();
    }
}
