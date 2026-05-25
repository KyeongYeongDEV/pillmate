package com.pillmate.schedule.domain.service;

import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class ScheduleConflictChecker {

    public boolean hasConflict(Long patientId, Long drugId, TimeOfDay timeOfDay,
                                LocalDate startDate, LocalDate endDate,
                                List<Schedule> existingSchedules) {
        return existingSchedules.stream()
                .filter(Schedule::isActive)
                .filter(s -> s.getPatientId().equals(patientId))
                .filter(s -> s.getDrugId().equals(drugId))
                .filter(s -> s.getTimeOfDay() == timeOfDay)
                .anyMatch(s -> s.overlapsWith(startDate, endDate));
    }
}
