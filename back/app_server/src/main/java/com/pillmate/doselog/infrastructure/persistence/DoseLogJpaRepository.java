package com.pillmate.doselog.infrastructure.persistence;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

interface DoseLogJpaRepository extends JpaRepository<DoseLog, Long> {

    List<DoseLog> findByPatientIdAndScheduledAtBetween(Long patientId, Instant from, Instant to);

    boolean existsByScheduleIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
            Long scheduleId, Instant fromInclusive, Instant toExclusive);

    List<DoseLog> findByScheduleIdAndStatusAndScheduledAtGreaterThanEqual(
            Long scheduleId, DoseStatus status, Instant fromInclusive);

    List<DoseLog> findByStatusAndCheckedAtBetweenAndGroupNotifiedAtIsNull(DoseStatus status, Instant from, Instant to);
}
