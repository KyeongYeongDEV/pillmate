package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.dto.SlotEditView;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("GetPrescriptionSlotsService — 처방전별 활성 슬롯 조회 + editable 판정 + 본인소유 가드")
@ExtendWith(MockitoExtension.class)
class GetPrescriptionSlotsServiceTest {

    @Mock ScheduleRepository scheduleRepository;
    @Mock PatientAccessGuard patientAccessGuard;

    private static final Long USER_ID = 1L;
    private static final Long PRESCRIPTION_ID = 100L;
    private static final LocalDate START = LocalDate.of(2026, 1, 1);

    // KST 2026-06-21 12:00 (UTC 2026-06-21T03:00)
    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-21T03:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        UserContext.set(USER_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private GetPrescriptionSlotsService sut() {
        return new GetPrescriptionSlotsService(scheduleRepository, patientAccessGuard, FIXED_CLOCK);
    }

    private Schedule activeSchedule(Long id, Long patientId, TimeOfDay timeOfDay,
                                    LocalTime customTime, LocalDate endDate) {
        Schedule s = Schedule.forPrescription(
                5L, patientId, PRESCRIPTION_ID, timeOfDay, customTime, START, endDate, USER_ID);
        ReflectionTestUtils.setField(s, "id", id);
        return s;
    }

    @Test
    @DisplayName("활성 슬롯 2개 → SlotEditView 리스트 반환, time HH:mm 형식")
    void execute_withTwoActiveSlots_returnsMappedViews() {
        // given
        Schedule morning = activeSchedule(1L, USER_ID, TimeOfDay.MORNING, LocalTime.of(8, 0),
                LocalDate.of(2026, 12, 31));
        Schedule evening = activeSchedule(2L, USER_ID, TimeOfDay.EVENING, LocalTime.of(19, 30),
                LocalDate.of(2026, 12, 31));
        given(scheduleRepository.findActiveByPrescriptionId(PRESCRIPTION_ID))
                .willReturn(List.of(morning, evening));

        // when
        List<SlotEditView> result = sut().execute(PRESCRIPTION_ID);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).scheduleId()).isEqualTo(1L);
        assertThat(result.get(0).timeOfDay()).isEqualTo(TimeOfDay.MORNING);
        assertThat(result.get(0).time()).isEqualTo("08:00");
        assertThat(result.get(1).time()).isEqualTo("19:30");
    }

    @Test
    @DisplayName("endDate null → editable=true")
    void execute_whenEndDateNull_editableIsTrue() {
        // given
        Schedule s = activeSchedule(1L, USER_ID, TimeOfDay.MORNING, LocalTime.of(8, 0), null);
        given(scheduleRepository.findActiveByPrescriptionId(PRESCRIPTION_ID)).willReturn(List.of(s));

        // when
        List<SlotEditView> result = sut().execute(PRESCRIPTION_ID);

        // then
        assertThat(result.get(0).editable()).isTrue();
        assertThat(result.get(0).endDate()).isNull();
    }

    @Test
    @DisplayName("endDate == today → editable=true (당일은 편집 가능)")
    void execute_whenEndDateIsToday_editableIsTrue() {
        // given — KST today = 2026-06-21
        LocalDate today = LocalDate.of(2026, 6, 21);
        Schedule s = activeSchedule(1L, USER_ID, TimeOfDay.MORNING, LocalTime.of(8, 0), today);
        given(scheduleRepository.findActiveByPrescriptionId(PRESCRIPTION_ID)).willReturn(List.of(s));

        // when
        List<SlotEditView> result = sut().execute(PRESCRIPTION_ID);

        // then
        assertThat(result.get(0).editable()).isTrue();
    }

    @Test
    @DisplayName("today > endDate → editable=false")
    void execute_whenEndDateIsPast_editableIsFalse() {
        // given — KST today = 2026-06-21, endDate = 2026-06-20 (어제)
        LocalDate yesterday = LocalDate.of(2026, 6, 20);
        Schedule s = activeSchedule(1L, USER_ID, TimeOfDay.MORNING, LocalTime.of(8, 0), yesterday);
        given(scheduleRepository.findActiveByPrescriptionId(PRESCRIPTION_ID)).willReturn(List.of(s));

        // when
        List<SlotEditView> result = sut().execute(PRESCRIPTION_ID);

        // then
        assertThat(result.get(0).editable()).isFalse();
        assertThat(result.get(0).endDate()).isEqualTo(yesterday);
    }

    @Test
    @DisplayName("처방전에 활성 슬롯 없음 → 빈 리스트 반환, 가드 호출 없음")
    void execute_whenNoActiveSlots_returnsEmptyList() {
        // given
        given(scheduleRepository.findActiveByPrescriptionId(PRESCRIPTION_ID)).willReturn(List.of());

        // when
        List<SlotEditView> result = sut().execute(PRESCRIPTION_ID);

        // then
        assertThat(result).isEmpty();
        verify(patientAccessGuard, never()).requireAccess(USER_ID, USER_ID);
    }

    @Test
    @DisplayName("타인 처방전 조회 → PATIENT_ACCESS_DENIED")
    void execute_whenOtherPatient_throwsPatientAccessDenied() {
        // given
        Long otherPatientId = 99L;
        Schedule s = activeSchedule(1L, otherPatientId, TimeOfDay.MORNING, LocalTime.of(8, 0),
                LocalDate.of(2026, 12, 31));
        given(scheduleRepository.findActiveByPrescriptionId(PRESCRIPTION_ID)).willReturn(List.of(s));
        doThrow(new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED))
                .when(patientAccessGuard).requireAccess(USER_ID, otherPatientId);

        // when / then
        assertThatThrownBy(() -> sut().execute(PRESCRIPTION_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);
    }

    @Test
    @DisplayName("customTime 이 timeOfDay.defaultTime 과 다를 때 → customTime 값을 time 으로 사용")
    void execute_customTimeDiffersFromDefault_usesCustomTime() {
        // given — MORNING default=08:00, customTime=09:15
        Schedule s = activeSchedule(1L, USER_ID, TimeOfDay.MORNING, LocalTime.of(9, 15),
                LocalDate.of(2026, 12, 31));
        given(scheduleRepository.findActiveByPrescriptionId(PRESCRIPTION_ID)).willReturn(List.of(s));

        // when
        List<SlotEditView> result = sut().execute(PRESCRIPTION_ID);

        // then
        assertThat(result.get(0).time()).isEqualTo("09:15");
        assertThat(result.get(0).timeOfDay()).isEqualTo(TimeOfDay.MORNING);
    }
}
