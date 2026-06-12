package com.pillmate.doselog.infrastructure.persistence;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface DoseLogJpaRepository extends JpaRepository<DoseLog, Long> {

    List<DoseLog> findByPatientIdAndScheduledAtBetween(Long patientId, Instant from, Instant to);

    Optional<DoseLog> findByScheduleIdAndScheduledAt(Long scheduleId, Instant scheduledAt);

    List<DoseLog> findByStatusAndCheckedAtBetweenAndGroupNotifiedAtIsNull(DoseStatus status, Instant from, Instant to);
}
