package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.dto.ScheduleResponse;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("DeactivateScheduleUseCase — 비활성화 + 소유자 검증")
@ExtendWith(MockitoExtension.class)
class DeactivateScheduleUseCaseTest {

    private static final java.time.Instant FIXED_NOW = java.time.Instant.parse("2026-07-13T10:00:00Z");

    @Mock ScheduleRepository scheduleRepository;
    @Mock CareGroupGuard careGroupGuard;
    @Mock PatientAccessGuard patientAccessGuard;
    @Mock com.pillmate.schedule.application.port.PeriodAdjustDoseLogsPort periodAdjustDoseLogsPort;
    @org.mockito.Spy java.time.Clock clock = java.time.Clock.fixed(FIXED_NOW, java.time.ZoneOffset.UTC);
    @InjectMocks DeactivateScheduleUseCase sut;

    private static final Long SCHEDULE_ID = 1L;
    private static final Long PATIENT_ID  = 2L;
    private static final Long GROUP_ID    = 5L;

    @BeforeEach
    void setUp() {
        UserContext.set(PATIENT_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    private Schedule schedule() {
        Schedule s = Schedule.of(GROUP_ID, PATIENT_ID, 10L, TimeOfDay.MORNING,
                LocalTime.of(8, 0), LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), PATIENT_ID);
        ReflectionTestUtils.setField(s, "id", SCHEDULE_ID);
        return s;
    }

    @Test
    @DisplayName("소유자 아닌 사용자 비활성화 시도 → PATIENT_ACCESS_DENIED, 저장 X")
    void deactivate_whenNotOwner_throwsPatientAccessDenied() {
        // given
        UserContext.set(99L);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule()));
        doThrow(new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED))
                .when(patientAccessGuard).requireAccess(99L, PATIENT_ID);

        // when / then
        assertThatThrownBy(() -> sut.deactivate(SCHEDULE_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("소유자가 비활성화 → active=false 저장")
    void deactivate_whenOwner_deactivatesAndSaves() {
        // given
        Schedule schedule = schedule();
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ScheduleResponse response = sut.deactivate(SCHEDULE_ID);

        // then
        assertThat(response).isNotNull();
        verify(scheduleRepository).save(any(Schedule.class));
    }

    @Test
    @DisplayName("T-BE-REMINDER-FIX P1-2: 비활성화 시 현재 시각 이후 PENDING dose_log skip — 중단한 약 리마인더 오발송 차단")
    void deactivate_skipsFuturePendingDoseLogs() {
        // given
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule()));
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        sut.deactivate(SCHEDULE_ID);

        // then
        verify(periodAdjustDoseLogsPort).skipPendingFrom(SCHEDULE_ID, FIXED_NOW);
    }

    @Test
    @DisplayName("소유자 아니면 dose_log skip 도 호출되지 않음")
    void deactivate_whenNotOwner_neverSkipsDoseLogs() {
        // given
        UserContext.set(99L);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule()));
        doThrow(new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED))
                .when(patientAccessGuard).requireAccess(99L, PATIENT_ID);

        // when / then
        assertThatThrownBy(() -> sut.deactivate(SCHEDULE_ID))
                .isInstanceOf(PillmateException.class);
        verify(periodAdjustDoseLogsPort, never()).skipPendingFrom(any(), any());
    }

    @Test
    @DisplayName("T-BE-SOLO-SCHEDULE-GUARD-FIX: 솔로(careGroupId null) 본인 → 그룹 가드 스킵하고 비활성화 성공")
    void deactivate_soloScheduleOwner_succeedsWithoutGroupGuard() {
        // given
        Schedule solo = Schedule.of(null, PATIENT_ID, 10L, TimeOfDay.MORNING,
                LocalTime.of(8, 0), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 30), PATIENT_ID);
        ReflectionTestUtils.setField(solo, "id", SCHEDULE_ID);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(solo));
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ScheduleResponse response = sut.deactivate(SCHEDULE_ID);

        // then
        assertThat(response).isNotNull();
        verify(careGroupGuard, never()).requireAccessible(any());
        verify(patientAccessGuard).requireAccess(PATIENT_ID, PATIENT_ID);
    }

    @Test
    @DisplayName("T-BE-SOLO-SCHEDULE-GUARD-FIX: 솔로 스케줄 타인 → PATIENT_ACCESS_DENIED (무가드 경로 없음)")
    void deactivate_soloScheduleNotOwner_denied() {
        // given
        UserContext.set(99L);
        Schedule solo = Schedule.of(null, PATIENT_ID, 10L, TimeOfDay.MORNING,
                LocalTime.of(8, 0), LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 30), PATIENT_ID);
        ReflectionTestUtils.setField(solo, "id", SCHEDULE_ID);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(solo));
        doThrow(new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED))
                .when(patientAccessGuard).requireAccess(99L, PATIENT_ID);

        // when / then
        assertThatThrownBy(() -> sut.deactivate(SCHEDULE_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("그룹 스케줄은 기존 그룹 가드 그대로 호출 (regression)")
    void deactivate_groupSchedule_stillChecksGroupGuard() {
        // given
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule()));
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        sut.deactivate(SCHEDULE_ID);

        // then
        verify(careGroupGuard).requireAccessible(GROUP_ID);
    }

    @Test
    @DisplayName("스케줄 미존재 → SCHEDULE_NOT_FOUND")
    void deactivate_whenNotFound_throws() {
        // given
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.deactivate(SCHEDULE_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.SCHEDULE_NOT_FOUND);
    }
}
