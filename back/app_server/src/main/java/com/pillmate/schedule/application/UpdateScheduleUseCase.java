package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.schedule.application.dto.ScheduleResponse;
import com.pillmate.schedule.application.dto.UpdateScheduleRequest;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateScheduleUseCase {

    private final ScheduleRepository scheduleRepository;
    private final CareGroupGuard careGroupGuard;

    @Transactional
    public ScheduleResponse update(Long scheduleId, UpdateScheduleRequest request) {
        Schedule schedule = findOrThrow(scheduleId);
        careGroupGuard.requireAccessible(schedule.getCareGroupId());
        applyChanges(schedule, request);
        return ScheduleResponse.from(scheduleRepository.save(schedule));
    }

    private Schedule findOrThrow(Long scheduleId) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new PillmateException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private void applyChanges(Schedule schedule, UpdateScheduleRequest request) {
        if (request.timeOfDay() != null) {
            schedule.updateTimeOfDay(request.timeOfDay());
        }
        if (request.endDate() != null) {
            schedule.updateEndDate(request.endDate());
        }
    }
}
