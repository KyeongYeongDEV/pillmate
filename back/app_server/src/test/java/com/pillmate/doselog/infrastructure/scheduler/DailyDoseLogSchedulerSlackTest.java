package com.pillmate.doselog.infrastructure.scheduler;

import com.pillmate.common.monitoring.SlackNotifier;
import com.pillmate.doselog.application.GenerateDailyDoseLogsUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DailyDoseLogSchedulerSlackTest {

    private GenerateDailyDoseLogsUseCase mockUseCase;
    private SlackNotifier mockSlack;
    private DailyDoseLogScheduler scheduler;

    @BeforeEach
    void setUp() {
        mockUseCase = mock(GenerateDailyDoseLogsUseCase.class);
        mockSlack = mock(SlackNotifier.class);
        scheduler = new DailyDoseLogScheduler(
                mockUseCase, Clock.systemUTC(), new SimpleMeterRegistry(), mockSlack);
    }

    @Test
    @DisplayName("0건 생성 시 Slack 경고 전송 (dose_log 생성 0건 감지)")
    void generateAtMidnight_zeroCreated_sendsSlackAlert() {
        when(mockUseCase.generate(any())).thenReturn(0);

        scheduler.generateAtMidnight();

        verify(mockSlack).send(argThat(msg -> msg.contains("0건")));
    }

    @Test
    @DisplayName("스케줄러 실패 시 Slack 긴급 알림 전송")
    void generateAtMidnight_failure_sendsSlackAlert() {
        when(mockUseCase.generate(any())).thenThrow(new RuntimeException("db timeout"));

        scheduler.generateAtMidnight();

        verify(mockSlack).send(any());
    }

    @Test
    @DisplayName("정상 생성(> 0) 시 Slack 호출 없음")
    void generateAtMidnight_normalCreation_doesNotSendSlack() {
        when(mockUseCase.generate(any())).thenReturn(5);

        scheduler.generateAtMidnight();

        verify(mockSlack, never()).send(any());
    }

    @Test
    @DisplayName("Slack 메시지에 환자정보 포함 금지 — 날짜와 카운트만")
    void generateAtMidnight_zeroCreated_messageContainsNoPatientInfo() {
        when(mockUseCase.generate(any())).thenReturn(0);

        scheduler.generateAtMidnight();

        verify(mockSlack).send(argThat(msg ->
                !msg.contains("patient") && !msg.contains("userId") && !msg.contains("@")
        ));
    }
}
