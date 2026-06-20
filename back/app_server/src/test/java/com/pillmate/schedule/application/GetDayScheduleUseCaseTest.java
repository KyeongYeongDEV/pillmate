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
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@DisplayName("GetDayScheduleUseCase — customTime 그룹핑 단위 테스트")
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
    @DisplayName("서로 다른 customTime 4개 → 4개 슬롯, 시각 ASC, HH:mm 라벨, TAKEN→done")
    void execute_distinctTimes_returns4Slots() {
        // given
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(
                        projection(6L, "MORNING", LocalTime.of(8, 0),  "타이레놀500mg", null, 9L, "TAKEN"),
                        projection(7L, "NOON",    LocalTime.of(12, 30), "타이레놀500mg", null, 10L, "PENDING"),
                        projection(8L, "EVENING", LocalTime.of(19, 0),  "타이레놀500mg", null, 11L, "SKIPPED"),
                        projection(9L, "BEDTIME", LocalTime.of(22, 0),  "타이레놀500mg", null, 12L, "PENDING")
                )
        );

        // when
        DayScheduleResponse response = sut.execute(TODAY);

        // then
        assertThat(response.date()).isEqualTo(TODAY);
        assertThat(response.totalCount()).isEqualTo(4);
        assertThat(response.doneCount()).isEqualTo(1);
        assertThat(response.slots()).hasSize(4);

        SlotView first = response.slots().get(0);
        assertThat(first.id()).isEqualTo("08:00");
        assertThat(first.time()).isEqualTo("08:00");
        assertThat(first.label()).isEqualTo("08:00");
        assertThat(first.customTime()).isEqualTo("08:00");
        assertThat(first.state()).isEqualTo("done");
        assertThat(first.doseLogId()).isEqualTo(9L);
        assertThat(first.doseLogIds()).containsExactly(9L);

        assertThat(response.slots().get(1).state()).isEqualTo("wait");
        assertThat(response.slots().get(2).state()).isEqualTo("wait");
    }

    @Test
    @DisplayName("동일 customTime 약 2개 → 1개 슬롯으로 그룹 (items 2, doseLogIds 2, 전부 TAKEN→done)")
    void execute_sameTime_mergesIntoOneSlot() {
        // given
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(
                        projection(6L, "MORNING", LocalTime.of(8, 0), "타이레놀500mg", "#ff0000", 9L, "TAKEN"),
                        projection(7L, "MORNING", LocalTime.of(8, 0), "게보린",       "#00ff00", 10L, "TAKEN")
                )
        );

        // when
        DayScheduleResponse response = sut.execute(TODAY);

        // then
        assertThat(response.slots()).hasSize(1);
        SlotView slot = response.slots().get(0);
        assertThat(slot.customTime()).isEqualTo("08:00");
        assertThat(slot.items()).containsExactly("타이레놀500mg", "게보린");
        assertThat(slot.drugCount()).isEqualTo(2);
        assertThat(slot.doseLogIds()).containsExactly(9L, 10L);
        assertThat(slot.pillColors()).containsExactly("#ff0000", "#00ff00");
        assertThat(slot.state()).isEqualTo("done");
        assertThat(response.doneCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("동일 customTime 그룹에 하나라도 미복용 → state=wait")
    void execute_sameTime_partialTaken_isWait() {
        // given
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(
                        projection(6L, "MORNING", LocalTime.of(8, 0), "타이레놀500mg", null, 9L, "TAKEN"),
                        projection(7L, "MORNING", LocalTime.of(8, 0), "게보린",       null, 10L, "PENDING")
                )
        );

        // when
        DayScheduleResponse response = sut.execute(TODAY);

        // then
        assertThat(response.slots()).hasSize(1);
        assertThat(response.slots().get(0).state()).isEqualTo("wait");
        assertThat(response.slots().get(0).doseLogIds()).containsExactly(9L, 10L);
    }

    @Test
    @DisplayName("dose_log 없는 슬롯 — state=wait, doseLogId=null, doseLogIds 비어있음")
    void execute_whenNoDoseLog_slotStateIsWait() {
        // given
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(
                        projection(6L, "MORNING", LocalTime.of(8, 0), "타이레놀500mg", null, null, null)
                )
        );

        // when
        DayScheduleResponse response = sut.execute(TODAY);

        // then
        SlotView slot = response.slots().get(0);
        assertThat(slot.state()).isEqualTo("wait");
        assertThat(slot.doseLogId()).isNull();
        assertThat(slot.doseLogIds()).isEmpty();
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

    private DayScheduleProjection projection(Long scheduleId, String timeOfDay, LocalTime customTime,
                                              String drugName, String pillColor,
                                              Long doseLogId, String doseStatus) {
        return new DayScheduleProjection(scheduleId, timeOfDay, customTime, drugName, pillColor, doseLogId, doseStatus);
    }
}
