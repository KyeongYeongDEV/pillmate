package com.pillmate.doselog.domain.repository;

import com.pillmate.doselog.domain.model.DoseLog;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DoseLogRepository {
    DoseLog save(DoseLog log);
    Optional<DoseLog> findById(Long id);
    List<DoseLog> findByPatientIdAndScheduledAtBetween(Long patientId, Instant from, Instant to);
    Optional<DoseLog> findByScheduleIdAndScheduledAt(Long scheduleId, Instant scheduledAt);
}
