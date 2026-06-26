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

@DisplayName("GetDayScheduleUseCase — 처방전(약봉투) 단위 행 단위 테스트")
@ExtendWith(MockitoExtension.class)
class GetDayScheduleUseCaseTest {

    @Mock ScheduleDayQueryPort scheduleDayQueryPort;
    @InjectMocks GetDayScheduleService sut;

    private static final Long PATIENT_ID = 1L;
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 21);

    @BeforeEach
    void setUp() {
        UserContext.set(PATIENT_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("서로 다른 시각 처방전 2개 → 2개 슬롯, 시각 ASC, 처방전 이름·prescriptionId 노출")
    void execute_distinctTimes_returnsPrescriptionRows() {
        // given
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(
                        row(6L, LocalTime.of(8, 0), 100L, TODAY,
                                List.of("타이레놀", "게보린", "판콜에이"), java.util.Arrays.asList("#fff", null, "#f00"), 9L, "TAKEN"),
                        row(7L, LocalTime.of(19, 0), 200L, TODAY,
                                List.of("아목시실린"), List.of("#0f0"), 10L, "PENDING")
                )
        );

        // when
        DayScheduleResponse response = sut.execute(TODAY);

        // then
        assertThat(response.totalCount()).isEqualTo(2);
        assertThat(response.doneCount()).isEqualTo(1);

        SlotView first = response.slots().get(0);
        assertThat(first.time()).isEqualTo("08:00");
        assertThat(first.prescriptionId()).isEqualTo(100L);
        assertThat(first.prescriptionName()).isEqualTo("6월21일·타이레놀 외2종");
        assertThat(first.items()).containsExactly("타이레놀", "게보린", "판콜에이");
        assertThat(first.drugCount()).isEqualTo(3);
        assertThat(first.pillColors()).containsExactly("#fff", "#999999", "#f00");
        assertThat(first.state()).isEqualTo("done");

        SlotView second = response.slots().get(1);
        assertThat(second.prescriptionId()).isEqualTo(200L);
        assertThat(second.prescriptionName()).isEqualTo("6월21일·아목시실린");
        assertThat(second.state()).isEqualTo("wait");
    }

    @Test
    @DisplayName("같은 시각 처방전 2개 → 약을 합치지 않고 처방전 행 2개로 분리")
    void execute_sameTime_keepsSeparatePrescriptionRows() {
        // given
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(
                        row(6L, LocalTime.of(8, 0), 100L, TODAY,
                                List.of("타이레놀"), List.of("#fff"), 9L, "TAKEN"),
                        row(7L, LocalTime.of(8, 0), 200L, TODAY,
                                List.of("게보린"), List.of("#f00"), 10L, "TAKEN")
                )
        );

        // when
        DayScheduleResponse response = sut.execute(TODAY);

        // then
        assertThat(response.slots()).hasSize(2);
        assertThat(response.slots().get(0).prescriptionId()).isEqualTo(100L);
        assertThat(response.slots().get(0).items()).containsExactly("타이레놀");
        assertThat(response.slots().get(1).prescriptionId()).isEqualTo(200L);
        assertThat(response.slots().get(1).items()).containsExactly("게보린");
        assertThat(response.slots().get(0).id()).isNotEqualTo(response.slots().get(1).id());
    }

    @Test
    @DisplayName("약 0종 처방전 → 이름 'N월N일 처방전', items 비어있음, drugCount 0")
    void execute_zeroDrugPrescription_showsDateOnlyName() {
        // given
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(row(6L, LocalTime.of(8, 0), 100L, TODAY, List.of(), List.of(), 9L, "PENDING"))
        );

        // when
        DayScheduleResponse response = sut.execute(TODAY);

        // then
        SlotView slot = response.slots().get(0);
        assertThat(slot.prescriptionName()).isEqualTo("6월21일 처방전");
        assertThat(slot.items()).isEmpty();
        assertThat(slot.drugCount()).isZero();
    }

    @Test
    @DisplayName("dose_log 없는 처방전 행 — state=wait, doseLogId=null, doseLogIds 비어있음")
    void execute_whenNoDoseLog_slotStateIsWait() {
        // given
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(row(6L, LocalTime.of(8, 0), 100L, TODAY, List.of("타이레놀"), List.of("#fff"), null, null))
        );

        // when
        DayScheduleResponse response = sut.execute(TODAY);

        // then
        SlotView slot = response.slots().get(0);
        assertThat(slot.state()).isEqualTo("wait");
        assertThat(slot.doseLogId()).isNull();
        assertThat(slot.doseLogIds()).isEmpty();
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

    @Test
    @DisplayName("동일 처방전·동일 시각 스케줄 2개 → slotId 1개(중복 없음), doseLogIds 합산")
    void execute_duplicateSameTimeAndPrescription_mergedIntoOneSlot() {
        // given
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(
                        row(1L, LocalTime.of(9, 0), 100L, TODAY, List.of("타이레놀"), List.of("#fff"), 10L, "TAKEN"),
                        row(2L, LocalTime.of(9, 0), 100L, TODAY, List.of("타이레놀"), List.of("#fff"), 11L, "TAKEN")
                )
        );

        // when
        DayScheduleResponse response = sut.execute(TODAY);

        // then — 슬롯 1개로 병합, slotId 중복 없음
        assertThat(response.slots()).hasSize(1);
        SlotView slot = response.slots().get(0);
        assertThat(slot.id()).isEqualTo("09:00@100");
        assertThat(slot.doseLogIds()).containsExactlyInAnyOrder(10L, 11L);
        assertThat(slot.state()).isEqualTo("done");
    }

    @Test
    @DisplayName("동일 시각 중복 슬롯: 1개만 TAKEN → 병합 state=wait")
    void execute_duplicateSameTime_oneNotTaken_stateIsWait() {
        // given
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(
                        row(1L, LocalTime.of(9, 0), 100L, TODAY, List.of("타이레놀"), List.of("#fff"), 10L, "TAKEN"),
                        row(2L, LocalTime.of(9, 0), 100L, TODAY, List.of("타이레놀"), List.of("#fff"), null, null)
                )
        );

        // when
        DayScheduleResponse response = sut.execute(TODAY);

        // then — 모두 TAKEN 아니므로 wait
        assertThat(response.slots()).hasSize(1);
        assertThat(response.slots().get(0).state()).isEqualTo("wait");
        assertThat(response.slots().get(0).doseLogIds()).containsExactly(10L);
    }

    // ─── 레거시(prescription_id IS NULL) 시드 표시 ────────────────────────────

    @Test
    @DisplayName("레거시 — prescription_id IS NULL 스케줄의 dose_log 가 day view 에 표시, 라벨=약이름")
    void execute_legacyNullPrescription_appearsWithDrugNameLabel() {
        // given — prescription_id IS NULL, singleDrugName="타이레놀"
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(legacyRow(10L, LocalTime.of(8, 0), "타이레놀", 20L, "TAKEN"))
        );

        // when
        DayScheduleResponse response = sut.execute(TODAY);

        // then
        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.doneCount()).isEqualTo(1);
        SlotView slot = response.slots().get(0);
        assertThat(slot.prescriptionId()).isNull();
        assertThat(slot.prescriptionName()).isEqualTo("타이레놀");
        assertThat(slot.state()).isEqualTo("done");
        assertThat(slot.doseLogId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("레거시 — singleDrugName 도 없으면 라벨='복약'")
    void execute_legacyNoSingleDrugName_labelIsFallback() {
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(legacyRow(10L, LocalTime.of(9, 0), null, null, null))
        );

        DayScheduleResponse response = sut.execute(TODAY);

        assertThat(response.slots()).hasSize(1);
        assertThat(response.slots().get(0).prescriptionName()).isEqualTo("복약");
    }

    @Test
    @DisplayName("레거시 — 같은 시각 서로 다른 약 2개 → slotId 충돌 없이 2 슬롯")
    void execute_twoLegacyAtSameTime_noSlotIdCollision() {
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(
                        legacyRow(10L, LocalTime.of(8, 0), "타이레놀", 20L, "TAKEN"),
                        legacyRow(11L, LocalTime.of(8, 0), "아스피린",  21L, "PENDING")
                )
        );

        DayScheduleResponse response = sut.execute(TODAY);

        assertThat(response.slots()).hasSize(2);
        assertThat(response.slots().get(0).id()).isNotEqualTo(response.slots().get(1).id());
    }

    @Test
    @DisplayName("레거시·처방전 혼재 — 각각 별도 슬롯, 처방전 기반 슬롯 회귀 0")
    void execute_mixedLegacyAndPrescription_separateSlots() {
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(
                        row(1L, LocalTime.of(8, 0), 100L, TODAY, List.of("게보린"), List.of("#fff"), 9L, "TAKEN"),
                        legacyRow(10L, LocalTime.of(8, 0), "타이레놀", 20L, "TAKEN")
                )
        );

        DayScheduleResponse response = sut.execute(TODAY);

        assertThat(response.slots()).hasSize(2);
        long prescriptionSlots = response.slots().stream().filter(s -> s.prescriptionId() != null).count();
        long legacySlots       = response.slots().stream().filter(s -> s.prescriptionId() == null).count();
        assertThat(prescriptionSlots).isEqualTo(1);
        assertThat(legacySlots).isEqualTo(1);
    }

    // ─── label 매핑 (TimeOfDay → 한국어) ──────────────────────────────────────

    @Test
    @DisplayName("08:00(아침 시각) → label='아침'")
    void execute_morningTime_labelIsAchim() {
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(row(1L, LocalTime.of(8, 0), 100L, TODAY, List.of("타이레놀"), List.of("#fff"), 9L, "TAKEN")));

        SlotView slot = sut.execute(TODAY).slots().get(0);

        assertThat(slot.label()).isEqualTo("아침");
    }

    @Test
    @DisplayName("12:30(점심 시각) → label='점심'")
    void execute_noonTime_labelIsJeomsim() {
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(row(1L, LocalTime.of(12, 30), 100L, TODAY, List.of("타이레놀"), List.of("#fff"), 9L, "TAKEN")));

        SlotView slot = sut.execute(TODAY).slots().get(0);

        assertThat(slot.label()).isEqualTo("점심");
    }

    @Test
    @DisplayName("19:00(저녁 시각) → label='저녁'")
    void execute_eveningTime_labelIsJonyeok() {
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(row(1L, LocalTime.of(19, 0), 100L, TODAY, List.of("타이레놀"), List.of("#fff"), 9L, "TAKEN")));

        SlotView slot = sut.execute(TODAY).slots().get(0);

        assertThat(slot.label()).isEqualTo("저녁");
    }

    @Test
    @DisplayName("22:00(취침 전 시각) → label='취침 전'")
    void execute_bedtime_labelIsBedtime() {
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(row(1L, LocalTime.of(22, 0), 100L, TODAY, List.of("타이레놀"), List.of("#fff"), 9L, "TAKEN")));

        SlotView slot = sut.execute(TODAY).slots().get(0);

        assertThat(slot.label()).isEqualTo("취침 전");
    }

    @Test
    @DisplayName("커스텀 시각(09:30) → label='09:30' (시간 문자열)")
    void execute_customTime_labelIsTimeString() {
        given(scheduleDayQueryPort.findByPatientAndDate(PATIENT_ID, TODAY)).willReturn(
                List.of(row(1L, LocalTime.of(9, 30), 100L, TODAY, List.of("타이레놀"), List.of("#fff"), 9L, "TAKEN")));

        SlotView slot = sut.execute(TODAY).slots().get(0);

        assertThat(slot.label()).isEqualTo("09:30");
    }

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private DayScheduleProjection row(Long scheduleId, LocalTime customTime, Long prescriptionId,
                                      LocalDate prescribedAt, List<String> drugNames, List<String> pillColors,
                                      Long doseLogId, String doseStatus) {
        return new DayScheduleProjection(
                scheduleId, customTime, prescriptionId, prescribedAt, drugNames, pillColors,
                doseLogId, doseStatus, null);
    }

    private DayScheduleProjection legacyRow(Long scheduleId, LocalTime customTime,
                                             String singleDrugName, Long doseLogId, String doseStatus) {
        return new DayScheduleProjection(
                scheduleId, customTime, null, null, List.of(), List.of(),
                doseLogId, doseStatus, singleDrugName);
    }
}
