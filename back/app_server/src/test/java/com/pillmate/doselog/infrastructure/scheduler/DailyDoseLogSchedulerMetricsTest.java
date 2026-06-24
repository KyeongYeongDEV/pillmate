package com.pillmate.doselog.infrastructure.scheduler;

import com.pillmate.common.monitoring.SlackNotifier;
import com.pillmate.doselog.application.GenerateDailyDoseLogsUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DailyDoseLogSchedulerMetricsTest {

    private SimpleMeterRegistry registry;
    private GenerateDailyDoseLogsUseCase mockUseCase;
    private DailyDoseLogScheduler scheduler;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        mockUseCase = mock(GenerateDailyDoseLogsUseCase.class);
        scheduler = new DailyDoseLogScheduler(
                mockUseCase, Clock.systemUTC(), registry, mock(SlackNotifier.class));
    }

    @Test
    @DisplayName("정상 실행 시 success 카운터와 생성 슬롯 카운터가 증가한다")
    void generateAtMidnight_success_incrementsCounters() {
        when(mockUseCase.generate(any())).thenReturn(5);

        scheduler.generateAtMidnight();

        assertThat(successCount()).isEqualTo(1.0);
        assertThat(generatedCount()).isEqualTo(5.0);
        assertThat(failureCount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("0건 생성 시 success 카운터는 오르고 generated 카운터는 0")
    void generateAtMidnight_zeroGenerated_onlySuccessCounterIncrements() {
        when(mockUseCase.generate(any())).thenReturn(0);

        scheduler.generateAtMidnight();

        assertThat(successCount()).isEqualTo(1.0);
        assertThat(generatedCount()).isEqualTo(0.0);
        assertThat(failureCount()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("예외 발생 시 failure 카운터가 증가하고 OCR 흐름을 막지 않는다")
    void generateAtMidnight_failure_incrementsFailureCounterWithoutThrowing() {
        when(mockUseCase.generate(any())).thenThrow(new RuntimeException("db error"));

        scheduler.generateAtMidnight(); // must not throw

        assertThat(failureCount()).isEqualTo(1.0);
        assertThat(successCount()).isEqualTo(0.0);
    }

    private double successCount() {
        return registry.counter("pillmate.doselog.scheduler.runs", "result", "success").count();
    }

    private double failureCount() {
        return registry.counter("pillmate.doselog.scheduler.runs", "result", "failure").count();
    }

    private double generatedCount() {
        return registry.counter("pillmate.doselog.scheduler.generated").count();
    }
}
