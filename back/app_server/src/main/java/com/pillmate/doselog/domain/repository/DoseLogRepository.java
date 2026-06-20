package com.pillmate.doselog.domain.repository;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DoseLogRepository {
    DoseLog save(DoseLog log);
    Optional<DoseLog> findById(Long id);
    List<DoseLog> findByPatientIdAndScheduledAtBetween(Long patientId, Instant from, Instant to);
    boolean existsByScheduleIdAndScheduledAtInRange(Long scheduleId, Instant fromInclusive, Instant toExclusive);
    List<DoseLog> findByScheduleIdAndStatusFrom(Long scheduleId, DoseStatus status, Instant fromInclusive);
    List<DoseLog> findTakenNotGroupNotifiedBetween(Instant fromInclusive, Instant toInclusive);
}
