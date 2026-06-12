package com.pillmate.doselog.infrastructure.persistence;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class DoseLogRepositoryImpl implements DoseLogRepository {

    private final DoseLogJpaRepository jpa;

    @Override public DoseLog save(DoseLog log) { return jpa.save(log); }
    @Override public Optional<DoseLog> findById(Long id) { return jpa.findById(id); }

    @Override
    public List<DoseLog> findByPatientIdAndScheduledAtBetween(Long patientId, Instant from, Instant to) {
        return jpa.findByPatientIdAndScheduledAtBetween(patientId, from, to);
    }

    @Override
    public Optional<DoseLog> findByScheduleIdAndScheduledAt(Long scheduleId, Instant scheduledAt) {
        return jpa.findByScheduleIdAndScheduledAt(scheduleId, scheduledAt);
    }

    @Override
    public List<DoseLog> findTakenNotGroupNotifiedBetween(Instant fromInclusive, Instant toInclusive) {
        return jpa.findByStatusAndCheckedAtBetweenAndGroupNotifiedAtIsNull(
                DoseStatus.TAKEN, fromInclusive, toInclusive);
    }
}
