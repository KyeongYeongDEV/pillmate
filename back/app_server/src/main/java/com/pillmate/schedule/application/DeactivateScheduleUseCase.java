package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.dto.ScheduleResponse;
import com.pillmate.schedule.application.port.PeriodAdjustDoseLogsPort;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DeactivateScheduleUseCase {

    private final ScheduleRepository scheduleRepository;
    private final CareGroupGuard careGroupGuard;
    private final PatientAccessGuard patientAccessGuard;
    private final PeriodAdjustDoseLogsPort periodAdjustDoseLogsPort;
    private final Clock clock;

    @Transactional
    public ScheduleResponse deactivate(Long scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new PillmateException(ErrorCode.SCHEDULE_NOT_FOUND));
        // 솔로(그룹 없는) 스케줄은 본인 소유 확인만 — PrescriptionScheduleService null 분기 선례
        if (schedule.getCareGroupId() != null) {
            careGroupGuard.requireAccessible(schedule.getCareGroupId());
        }
        patientAccessGuard.requireAccess(UserContext.get(), schedule.getPatientId());
        schedule.deactivate();
        Schedule saved = scheduleRepository.save(schedule);
        // 중단한 약에 "드실 시간이에요" 리마인더 오발송 차단
        periodAdjustDoseLogsPort.skipPendingFrom(scheduleId, Instant.now(clock));
        return ScheduleResponse.from(saved);
    }
}
