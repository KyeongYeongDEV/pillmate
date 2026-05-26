package com.pillmate.schedule.domain.repository;

import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ScheduleRepository {
    Schedule save(Schedule schedule);
    Optional<Schedule> findById(Long id);
    List<Schedule> findActiveByPatientAndTime(Long patientId, TimeOfDay timeOfDay, LocalDate date);
    List<Schedule> findAllByPatientId(Long patientId);
    List<Schedule> findByPatientIdAndActiveOrderByTimeOfDayAsc(Long patientId, boolean active);
}
