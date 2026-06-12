package com.pillmate.schedule.application;

import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.dto.MonthScheduleResponse;
import com.pillmate.schedule.application.port.ScheduleMonthQueryPort;
import com.pillmate.schedule.application.port.ScheduleMonthQueryPort.DayDoseCount;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@DisplayName("GetMonthScheduleUseCase — 월 복약 현황 집계 단위 테스트")
@ExtendWith(MockitoExtension.class)
class GetMonthScheduleUseCaseTest {

    @Mock ScheduleMonthQueryPort scheduleMonthQueryPort;
    @InjectMocks GetMonthScheduleService sut;

    private static final Long PATIENT_ID = 1L;
    private static final YearMonth JUNE = YearMonth.of(2026, 6);

    @BeforeEach
    void setUp() {
        UserContext.set(PATIENT_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("KST 월 경계 — 6월 조회 시 from=5/31 15:00Z(=KST 6/1 00:00), to=6/30 15:00Z(=KST 7/1 00:00)")
    void execute_queriesWithKstMonthRange() {
        // given
        given(scheduleMonthQueryPort.findDailyDoseCounts(eq(PATIENT_ID), any(), any()))
                .willReturn(List.of());

        // when
        sut.execute(JUNE);

        // then
        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        then(scheduleMonthQueryPort).should()
                .findDailyDoseCounts(eq(PATIENT_ID), fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getValue()).isEqualTo(Instant.parse("2026-05-31T15:00:00Z"));
        assertThat(toCaptor.getValue()).isEqualTo(Instant.parse("2026-06-30T15:00:00Z"));
    }

    @Test
    @DisplayName("날짜별 adherence 판정 — 4/4 FULL, 2/4 PARTIAL, 0/4 MISS")
    void execute_mapsAdherencePerDay() {
        // given
        given(scheduleMonthQueryPort.findDailyDoseCounts(eq(PATIENT_ID), any(), any()))
                .willReturn(List.of(
                        new DayDoseCount(LocalDate.of(2026, 6, 1), 4, 4),
                        new DayDoseCount(LocalDate.of(2026, 6, 2), 4, 2),
                        new DayDoseCount(LocalDate.of(2026, 6, 3), 4, 0)
                ));

        // when
        MonthScheduleResponse response = sut.execute(JUNE);

        // then
        assertThat(response.month()).isEqualTo("2026-06");
        assertThat(response.days()).hasSize(3);
        assertThat(response.days().get(0).adherence()).isEqualTo("FULL");
        assertThat(response.days().get(0).totalCount()).isEqualTo(4);
        assertThat(response.days().get(0).takenCount()).isEqualTo(4);
        assertThat(response.days().get(1).adherence()).isEqualTo("PARTIAL");
        assertThat(response.days().get(2).adherence()).isEqualTo("MISS");
    }

    @Test
    @DisplayName("dose_logs 없는 월 — days 빈 배열 (FE dot 미표시)")
    void execute_whenNoLogs_returnsEmptyDays() {
        given(scheduleMonthQueryPort.findDailyDoseCounts(eq(PATIENT_ID), any(), any()))
                .willReturn(List.of());

        MonthScheduleResponse response = sut.execute(JUNE);

        assertThat(response.days()).isEmpty();
    }

    @Test
    @DisplayName("UserContext 의 본인 patientId 만 사용 — 파라미터 노출 X (#68 도메인 피벗)")
    void execute_usesUserContextAsPatientId() {
        // given
        UserContext.set(99L);
        given(scheduleMonthQueryPort.findDailyDoseCounts(eq(99L), any(), any()))
                .willReturn(List.of());

        // when
        MonthScheduleResponse response = sut.execute(JUNE);

        // then
        assertThat(response.days()).isEmpty();
        then(scheduleMonthQueryPort).should().findDailyDoseCounts(eq(99L), any(), any());
    }
}
