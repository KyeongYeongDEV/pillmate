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

    @Mock ScheduleRepository scheduleRepository;
    @Mock CareGroupGuard careGroupGuard;
    @Mock PatientAccessGuard patientAccessGuard;
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
