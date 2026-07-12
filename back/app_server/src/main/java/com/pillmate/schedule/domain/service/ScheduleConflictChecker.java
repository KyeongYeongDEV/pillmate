package com.pillmate.schedule.domain.service;

import com.pillmate.schedule.domain.model.Schedule;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

@Component
public class ScheduleConflictChecker {

    public boolean hasConflict(Long patientId, Long drugId, LocalTime customTime,
                                LocalDate startDate, LocalDate endDate,
                                List<Schedule> existingSchedules) {
        return existingSchedules.stream()
                .filter(Schedule::isActive)
                .filter(s -> s.getPatientId().equals(patientId))
                .filter(s -> s.getDrugId().equals(drugId))
                .filter(s -> Objects.equals(s.getCustomTime(), customTime))
                .anyMatch(s -> s.overlapsWith(startDate, endDate));
    }

    public boolean hasPrescriptionSlotConflict(Long prescriptionId, LocalTime customTime,
                                               LocalDate startDate, LocalDate endDate,
                                               List<Schedule> existingSchedules) {
        return existingSchedules.stream()
                .filter(Schedule::isActive)
                .filter(s -> Objects.equals(s.getPrescriptionId(), prescriptionId))
                .filter(s -> Objects.equals(s.getCustomTime(), customTime))
                .anyMatch(s -> s.overlapsWith(startDate, endDate));
    }
}
