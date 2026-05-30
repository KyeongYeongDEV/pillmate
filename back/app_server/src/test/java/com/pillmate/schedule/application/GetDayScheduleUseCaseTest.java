package com.pillmate.schedule.application;

import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.dto.DayScheduleResponse;
import com.pillmate.schedule.application.dto.SlotView;
import com.pillmate.schedule.application.port.ScheduleDayQueryPort;
import com.pillmate.schedule.application.port.ScheduleDayQueryPort.DayScheduleProjection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@DisplayName("GetDayScheduleUseCase — 단위 테스트")
@ExtendWith(MockitoExtension.class)
class GetDayScheduleUseCaseTest {

    @Mock ScheduleDayQueryPort scheduleDayQueryPort;
    @InjectMocks GetDayScheduleService sut;

    private static final Long PATIENT_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 5, 30);

    @BeforeEach
    void setUp() {
        UserContext.set(PATIENT_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("4개 슬롯 반환 — TAKEN→done, PENDING→wait, SKIPPED→wait")
    void execute_returns4Slots_withStatusMapping() {
        // given
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(
                        projection(6L, "MORNING", "타이레놀500mg", null, 9L, "TAKEN"),
                        projection(7L, "NOON",    "타이레놀500mg", null, 10L, "PENDING"),
                        projection(8L, "EVENING", "타이레놀500mg", null, 11L, "SKIPPED"),
                        projection(9L, "BEDTIME", "타이레놀500mg", null, 12L, "PENDING")
                )
        );

        // when
        DayScheduleResponse response = sut.execute(TODAY);

        // then
        assertThat(response.date()).isEqualTo(TODAY);
        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.doneCount()).isEqualTo(1);
        assertThat(response.slots()).hasSize(4);

        SlotView morning = response.slots().get(0);
        assertThat(morning.id()).isEqualTo("morning");
        assertThat(morning.time()).isEqualTo("08:00");
        assertThat(morning.label()).isEqualTo("아침");
        assertThat(morning.state()).isEqualTo("done");
        assertThat(morning.doseLogId()).isEqualTo(9L);

        SlotView noon = response.slots().get(1);
        assertThat(noon.state()).isEqualTo("wait");

        SlotView evening = response.slots().get(2);
        assertThat(evening.state()).isEqualTo("wait");
    }

    @Test
    @DisplayName("dose_log 없는 슬롯 — state=wait, doseLogId=null")
    void execute_whenNoDoseLog_slotStateIsWait() {
        // given
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(
                        projection(6L, "MORNING", "타이레놀500mg", null, null, null)
                )
        );

        // when
        DayScheduleResponse response = sut.execute(TODAY);

        // then
        SlotView slot = response.slots().get(0);
        assertThat(slot.state()).isEqualTo("wait");
        assertThat(slot.doseLogId()).isNull();
        assertThat(slot.drugCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("UserContext 에서 patientId 자동 — 파라미터 노출 X")
    void execute_usesUserContextAsPatientId() {
        // given
        UserContext.set(99L);
        given(scheduleDayQueryPort.findByPatientAndDate(99L, TODAY)).willReturn(List.of());

        // when
        DayScheduleResponse response = sut.execute(TODAY);

        // then
        assertThat(response.slots()).isEmpty();
        assertThat(response.totalCount()).isZero();
    }

    private DayScheduleProjection projection(Long scheduleId, String timeOfDay,
                                              String drugName, String pillColor,
                                              Long doseLogId, String doseStatus) {
        return new DayScheduleProjection(scheduleId, timeOfDay, drugName, pillColor, doseLogId, doseStatus);
    }
}
