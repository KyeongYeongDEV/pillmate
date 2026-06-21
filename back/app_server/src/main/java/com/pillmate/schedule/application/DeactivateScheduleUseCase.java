package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.dto.ScheduleResponse;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeactivateScheduleUseCase {

    private final ScheduleRepository scheduleRepository;
    private final CareGroupGuard careGroupGuard;
    private final PatientAccessGuard patientAccessGuard;

    @Transactional
    public ScheduleResponse deactivate(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new PillmateException(ErrorCode.SCHEDULE_NOT_FOUND));
        careGroupGuard.requireAccessible(schedule.getCareGroupId());
        patientAccessGuard.requireAccess(UserContext.get(), schedule.getPatientId());
        schedule.deactivate();
        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }
}
