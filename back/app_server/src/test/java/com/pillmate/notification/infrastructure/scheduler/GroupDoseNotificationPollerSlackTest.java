package com.pillmate.notification.infrastructure.scheduler;

import com.pillmate.common.monitoring.SlackNotifier;
import com.pillmate.notification.application.NotifyDueGroupDosesUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GroupDoseNotificationPollerSlackTest {

    private NotifyDueGroupDosesUseCase mockUseCase;
    private SlackNotifier mockSlack;
    private GroupDoseNotificationPoller poller;

    @BeforeEach
    void setUp() {
        mockUseCase = mock(NotifyDueGroupDosesUseCase.class);
        mockSlack = mock(SlackNotifier.class);
        poller = new GroupDoseNotificationPoller(mockUseCase, new SimpleMeterRegistry(), mockSlack);
    }

    @Test
    @DisplayName("연속 실패 임계값(3) 미만 → Slack 미전송")
    void poll_belowFailureThreshold_doesNotSendSlack() {
        when(mockUseCase.notifyDue()).thenThrow(new RuntimeException("conn refused"));

        poller.poll();
        poller.poll();

        verify(mockSlack, never()).send(any());
    }

    @Test
    @DisplayName("연속 실패 3회 도달 → Slack 1회 전송")
    void poll_threeConsecutiveFailures_sendsOneSlackAlert() {
        when(mockUseCase.notifyDue()).thenThrow(new RuntimeException("conn refused"));

        poller.poll();
        poller.poll();
        poller.poll();

        verify(mockSlack, times(1)).send(any());
    }

    @Test
    @DisplayName("실패 후 성공 시 연속 카운터 리셋 → 임계값 재도달 전까지 Slack 없음")
    void poll_failThenSucceed_resetsConsecutiveCount() {
        when(mockUseCase.notifyDue())
                .thenThrow(new RuntimeException("err"))
                .thenThrow(new RuntimeException("err"))
                .thenReturn(0)          // 성공으로 리셋
                .thenThrow(new RuntimeException("err"))
                .thenThrow(new RuntimeException("err"));

        poller.poll(); // fail 1
        poller.poll(); // fail 2
        poller.poll(); // success → reset
        poller.poll(); // fail 1 (new sequence)
        poller.poll(); // fail 2 (still below threshold)

        verify(mockSlack, never()).send(any());
    }

    @Test
    @DisplayName("정상 폴링 → Slack 없음")
    void poll_success_doesNotSendSlack() {
        when(mockUseCase.notifyDue()).thenReturn(0);

        poller.poll();

        verify(mockSlack, never()).send(any());
    }
}
