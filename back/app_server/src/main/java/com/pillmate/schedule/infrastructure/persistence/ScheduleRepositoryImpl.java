package com.pillmate.schedule.infrastructure.persistence;

import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class ScheduleRepositoryImpl implements ScheduleRepository {

    private final ScheduleJpaRepository jpa;

    @Override public Schedule save(Schedule s) { return jpa.save(s); }
    @Override public Optional<Schedule> findById(Long id) { return jpa.findById(id); }

    @Override
    public List<Schedule> findActiveByPatientAndTime(Long patientId, TimeOfDay timeOfDay, LocalDate date) {
        return jpa.findActiveByPatientAndTime(patientId, timeOfDay, date);
    }

    @Override
    public List<Schedule> findAllByPatientId(Long patientId) {
        return jpa.findAllByPatientId(patientId);
    }

    @Override
    public List<Schedule> findByPatientIdAndActiveOrderByTimeOfDayAsc(Long patientId, boolean active) {
        return jpa.findByPatientIdAndActiveOrderByTimeOfDayAsc(patientId, active);
    }

    @Override
    public List<Schedule> findAllActiveOn(LocalDate date) {
        return jpa.findAllActiveOn(date);
    }
}
