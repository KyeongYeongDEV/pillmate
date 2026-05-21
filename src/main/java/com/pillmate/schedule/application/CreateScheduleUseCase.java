package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.schedule.application.dto.CreateScheduleRequest;
import com.pillmate.schedule.application.dto.CreateScheduleResponse;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import com.pillmate.schedule.domain.service.ScheduleConflictChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CreateScheduleUseCase {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleConflictChecker conflictChecker;

    @Transactional
    public CreateScheduleResponse create(CreateScheduleRequest req, Long createdBy) {
        List<Schedule> existing = scheduleRepository.findActiveByPatientAndTime(
                req.patientId(), req.timeOfDay(), req.startDate());

        if (conflictChecker.hasConflict(req.patientId(), req.timeOfDay(),
                req.startDate(), req.endDate(), existing)) {
            throw new PillmateException(ErrorCode.SCHEDULE_CONFLICT);
        }

        Schedule schedule = Schedule.of(req.careGroupId(), req.patientId(), req.drugId(),
                req.timeOfDay(), req.startDate(), req.endDate(), createdBy);

        return CreateScheduleResponse.from(scheduleRepository.save(schedule));
    }
}
