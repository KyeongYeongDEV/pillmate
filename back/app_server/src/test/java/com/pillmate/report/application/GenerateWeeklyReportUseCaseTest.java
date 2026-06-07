package com.pillmate.report.application;

import com.pillmate.report.application.port.DoseLogStatsPort;
import com.pillmate.report.application.port.LlmInsightPort;
import com.pillmate.report.application.port.PrescriptionContextPort;
import com.pillmate.report.domain.event.WeeklyReportGenerated;
import com.pillmate.report.domain.model.HealthReport;
import com.pillmate.report.domain.model.PeriodType;
import com.pillmate.report.domain.repository.HealthReportRepository;
import com.pillmate.report.domain.service.PatternDetector;
import com.pillmate.report.domain.service.ScoreCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@DisplayName("GenerateWeeklyReportUseCase — 리포트 생성 후 WeeklyReportGenerated 발행")
@ExtendWith(MockitoExtension.class)
class GenerateWeeklyReportUseCaseTest {

    @Mock HealthReportRepository reportRepository;
    @Mock DoseLogStatsPort statsPort;
    @Mock PrescriptionContextPort prescriptionContextPort;
    @Mock LlmInsightPort llmInsightPort;
    @Mock ScoreCalculator scoreCalculator;
    @Mock PatternDetector patternDetector;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks GenerateWeeklyReportUseCase sut;

    private static final Long PATIENT_ID = 1L;
    private static final Long GROUP_ID = 5L;
    private static final LocalDate WEEK_START = LocalDate.of(2026, 6, 1);

    @Test
    @DisplayName("리포트 저장 성공 시 WeeklyReportGenerated 이벤트 publishEvent 호출")
    void generate_publishesWeeklyReportGeneratedEvent() {
        // given
        DoseLogStatsPort.PeriodStats stats = new DoseLogStatsPort.PeriodStats(10, 8, 1, 0, 7, 3, 1, 0, 0, Map.of());
        given(prescriptionContextPort.loadContext(PATIENT_ID))
                .willReturn(new PrescriptionContextPort.PatientContext(GROUP_ID, List.of()));
        given(statsPort.aggregate(any(), any(), any())).willReturn(stats);
        given(statsPort.dailyCounts(any(), any(), any())).willReturn(List.of());
        given(scoreCalculator.calculate(8, 10, 7, 100)).willReturn(80);
        given(patternDetector.detect(any())).willReturn(List.of());
        given(llmInsightPort.generate(any())).willReturn(List.of());

        HealthReport savedReport = HealthReport.create(
                GROUP_ID, PATIENT_ID, PeriodType.WEEKLY,
                WEEK_START, WEEK_START.plusDays(6), 80, null,
                new BigDecimal("80.00"), 10, 8, 1, 0, List.of());
        given(reportRepository.save(any(HealthReport.class))).willReturn(savedReport);

        // when
        sut.generate(PATIENT_ID, WEEK_START);

        // then
        ArgumentCaptor<WeeklyReportGenerated> captor = ArgumentCaptor.forClass(WeeklyReportGenerated.class);
        verify(eventPublisher).publishEvent(captor.capture());
        WeeklyReportGenerated event = captor.getValue();
        assertThat(event.actorUserId()).isEqualTo(PATIENT_ID);
        assertThat(event.weekStart()).isEqualTo(WEEK_START);
    }
}
