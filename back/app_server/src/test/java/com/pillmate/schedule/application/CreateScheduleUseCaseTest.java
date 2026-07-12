package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.schedule.application.dto.CreateScheduleRequest;
import com.pillmate.schedule.application.dto.CreateScheduleResponse;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import com.pillmate.schedule.domain.service.ScheduleConflictChecker;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("CreateScheduleUseCase — 스케줄 생성 + 소유자 검증")
@ExtendWith(MockitoExtension.class)
class CreateScheduleUseCaseTest {

    @Mock ScheduleRepository scheduleRepository;
    @Mock ScheduleConflictChecker conflictChecker;
    @Mock CareGroupGuard careGroupGuard;
    @Mock PatientAccessGuard patientAccessGuard;
    @InjectMocks CreateScheduleUseCase sut;

    private static final Long GROUP_ID  = 5L;
    private static final Long PATIENT_ID = 2L;
    private static final Long DRUG_ID   = 10L;
    private static final LocalDate START = LocalDate.of(2026, 6, 1);
    private static final LocalDate END   = LocalDate.of(2026, 6, 30);

    @Test
    @DisplayName("요청자 != 환자 → PATIENT_ACCESS_DENIED, 스케줄 저장 X")
    void create_whenNotOwner_throwsPatientAccessDenied() {
        // given
        Long requesterId = 99L;
        doThrow(new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED))
                .when(patientAccessGuard).requireAccess(requesterId, PATIENT_ID);
        CreateScheduleRequest req = new CreateScheduleRequest(
                GROUP_ID, PATIENT_ID, DRUG_ID, TimeOfDay.MORNING, null, START, END);

        // when / then
        assertThatThrownBy(() -> sut.create(req, requesterId))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);
        verify(scheduleRepository, never()).save(any());
    }

    @Test
    @DisplayName("요청자 == 환자, 충돌 없음 → 스케줄 생성 성공")
    void create_whenOwnerAndNoConflict_savesSchedule() {
        // given
        given(scheduleRepository.findActiveByPatient(PATIENT_ID, START))
                .willReturn(List.of());
        given(conflictChecker.hasConflict(any(), any(), any(), any(), any(), any())).willReturn(false);
        given(scheduleRepository.save(any(Schedule.class))).willAnswer(inv -> inv.getArgument(0));
        CreateScheduleRequest req = new CreateScheduleRequest(
                GROUP_ID, PATIENT_ID, DRUG_ID, TimeOfDay.MORNING, LocalTime.of(8, 0), START, END);

        // when
        CreateScheduleResponse response = sut.create(req, PATIENT_ID);

        // then
        assertThat(response).isNotNull();
        verify(scheduleRepository).save(any(Schedule.class));
    }
}
