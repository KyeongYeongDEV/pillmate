package com.pillmate.schedule.domain.service;

import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ScheduleConflictChecker — customTime(정확 시각) 기준 충돌 판정")
class ScheduleConflictCheckerTest {

    private static final LocalDate START = LocalDate.of(2026, 5, 25);
    private static final LocalDate END = LocalDate.of(2026, 6, 1);
    private static final LocalDate PRESC_START = LocalDate.of(2026, 6, 21);
    private static final LocalDate PRESC_END = LocalDate.of(2026, 6, 27);

    private final ScheduleConflictChecker checker = new ScheduleConflictChecker();

    @Test
    @DisplayName("같은 patient + 같은 정확 시각 + 같은 drug 활성 → 충돌")
    void detect_whenSamePatientSameExactTimeSameDrug_conflict() {
        Schedule existing = active(2L, 10L, TimeOfDay.MORNING, LocalTime.of(8, 0));

        boolean conflict = checker.hasConflict(
                2L, 10L, LocalTime.of(8, 0), START, END, List.of(existing));

        assertThat(conflict).isTrue();
    }

    @Test
    @DisplayName("같은 bucket(MORNING) 이라도 정확 시각 다르면 충돌 아님 — 08:00 존재, 10:00 신규")
    void detect_whenSameBucketDifferentExactTime_noConflict() {
        Schedule existing = active(2L, 10L, TimeOfDay.MORNING, LocalTime.of(8, 0));

        boolean conflict = checker.hasConflict(
                2L, 10L, LocalTime.of(10, 0), START, END, List.of(existing));

        assertThat(conflict).isFalse();
    }

    @Test
    @DisplayName("active=false 스케줄은 충돌 대상 아님")
    void detect_whenInactive_noConflict() {
        Schedule existing = active(2L, 10L, TimeOfDay.MORNING, LocalTime.of(8, 0));
        existing.deactivate();

        boolean conflict = checker.hasConflict(
                2L, 10L, LocalTime.of(8, 0), START, END, List.of(existing));

        assertThat(conflict).isFalse();
    }

    @Test
    @DisplayName("다른 drug 이면 같은 시각이라도 충돌 아님")
    void detect_whenDifferentDrug_noConflict() {
        Schedule existing = active(2L, 11L, TimeOfDay.MORNING, LocalTime.of(8, 0));

        boolean conflict = checker.hasConflict(
                2L, 10L, LocalTime.of(8, 0), START, END, List.of(existing));

        assertThat(conflict).isFalse();
    }

    @Test
    @DisplayName("정확 시각 다르면 충돌 아님 — 19:00 존재, 08:00 신규 (다른 bucket)")
    void detect_whenDifferentExactTime_noConflict() {
        Schedule existing = active(2L, 10L, TimeOfDay.EVENING, LocalTime.of(19, 0));

        boolean conflict = checker.hasConflict(
                2L, 10L, LocalTime.of(8, 0), START, END, List.of(existing));

        assertThat(conflict).isFalse();
    }

    @Test
    @DisplayName("같은 처방전 + 같은 정확 시각 활성 → 슬롯 충돌")
    void prescriptionSlot_whenSamePrescriptionSameExactTime_conflict() {
        Schedule existing = prescriptionSlot(99L, TimeOfDay.MORNING, LocalTime.of(8, 0));

        boolean conflict = checker.hasPrescriptionSlotConflict(
                99L, LocalTime.of(8, 0), PRESC_START, PRESC_END, List.of(existing));

        assertThat(conflict).isTrue();
    }

    @Test
    @DisplayName("같은 처방전 같은 bucket 이라도 정확 시각 다르면 슬롯 충돌 아님 — 08:00·10:00 둘 다 등록 가능")
    void prescriptionSlot_whenSameBucketDifferentExactTime_noConflict() {
        Schedule existing = prescriptionSlot(99L, TimeOfDay.MORNING, LocalTime.of(8, 0));

        boolean conflict = checker.hasPrescriptionSlotConflict(
                99L, LocalTime.of(10, 0), PRESC_START, PRESC_END, List.of(existing));

        assertThat(conflict).isFalse();
    }

    @Test
    @DisplayName("다른 처방전이면 같은 시각이라도 슬롯 충돌 아님 (시각별 처방전 여러 행 허용)")
    void prescriptionSlot_whenDifferentPrescription_noConflict() {
        Schedule existing = prescriptionSlot(88L, TimeOfDay.MORNING, LocalTime.of(8, 0));

        boolean conflict = checker.hasPrescriptionSlotConflict(
                99L, LocalTime.of(8, 0), PRESC_START, PRESC_END, List.of(existing));

        assertThat(conflict).isFalse();
    }

    @Test
    @DisplayName("같은 처방전이라도 정확 시각 다르면 슬롯 충돌 아님")
    void prescriptionSlot_whenDifferentExactTime_noConflict() {
        Schedule existing = prescriptionSlot(99L, TimeOfDay.EVENING, LocalTime.of(19, 0));

        boolean conflict = checker.hasPrescriptionSlotConflict(
                99L, LocalTime.of(8, 0), PRESC_START, PRESC_END, List.of(existing));

        assertThat(conflict).isFalse();
    }

    @Test
    @DisplayName("비활성 슬롯은 충돌 대상 아님")
    void prescriptionSlot_whenInactive_noConflict() {
        Schedule existing = prescriptionSlot(99L, TimeOfDay.MORNING, LocalTime.of(8, 0));
        existing.deactivate();

        boolean conflict = checker.hasPrescriptionSlotConflict(
                99L, LocalTime.of(8, 0), PRESC_START, PRESC_END, List.of(existing));

        assertThat(conflict).isFalse();
    }

    // T-BE-AUDIT-P1-FIXES Fix3 — 무기한(endDate null) 스케줄도 충돌 검사 대상
    @Test
    @DisplayName("무기한(endDate null) 스케줄 + 같은 정확 시각 → 충돌 (기간 우회 차단)")
    void detect_whenUnlimitedEndDateSameExactTime_conflict() {
        Schedule unlimited = Schedule.of(1L, 2L, 10L, TimeOfDay.MORNING, LocalTime.of(8, 0),
                LocalDate.of(2026, 5, 1), null, 1L);

        boolean conflict = checker.hasConflict(
                2L, 10L, LocalTime.of(8, 0), START, END, List.of(unlimited));

        assertThat(conflict).isTrue();
    }

    @Test
    @DisplayName("무기한 스케줄이라도 정확 시각 다르면 충돌 아님")
    void detect_whenUnlimitedEndDateDifferentTime_noConflict() {
        Schedule unlimited = Schedule.of(1L, 2L, 10L, TimeOfDay.MORNING, LocalTime.of(8, 0),
                LocalDate.of(2026, 5, 1), null, 1L);

        boolean conflict = checker.hasConflict(
                2L, 10L, LocalTime.of(10, 0), START, END, List.of(unlimited));

        assertThat(conflict).isFalse();
    }

    @Test
    @DisplayName("신규 요청이 무기한(endDate null)이어도 기존 스케줄과 충돌 판정 (NPE 없이)")
    void detect_whenNewRequestUnlimited_conflictSafely() {
        Schedule existing = active(2L, 10L, TimeOfDay.MORNING, LocalTime.of(8, 0));

        boolean conflict = checker.hasConflict(
                2L, 10L, LocalTime.of(8, 0), START, null, List.of(existing));

        assertThat(conflict).isTrue();
    }

    private Schedule active(Long patientId, Long drugId, TimeOfDay bucket, LocalTime customTime) {
        return Schedule.of(1L, patientId, drugId, bucket, customTime,
                LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 30), 1L);
    }

    private Schedule prescriptionSlot(Long prescriptionId, TimeOfDay bucket, LocalTime customTime) {
        return Schedule.forPrescription(1L, 2L, prescriptionId, bucket, customTime,
                LocalDate.of(2026, 6, 21), LocalDate.of(2026, 6, 27), 1L);
    }
}
