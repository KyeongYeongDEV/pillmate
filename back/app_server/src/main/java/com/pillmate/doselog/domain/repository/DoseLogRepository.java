package com.pillmate.doselog.domain.repository;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DoseLogRepository {
    DoseLog save(DoseLog log);
    List<DoseLog> saveAll(List<DoseLog> logs);
    Optional<DoseLog> findById(Long id);
    List<DoseLog> findAllByIdIn(Collection<Long> ids);
    List<DoseLog> findByPatientIdAndScheduledAtBetween(Long patientId, Instant from, Instant to);
    boolean existsByScheduleIdAndScheduledAtInRange(Long scheduleId, Instant fromInclusive, Instant toExclusive);
    List<DoseLog> findByScheduleIdAndStatusFrom(Long scheduleId, DoseStatus status, Instant fromInclusive);
    List<DoseLog> findTakenNotGroupNotifiedBetween(Instant fromInclusive, Instant toInclusive);
    List<DoseLog> findPendingNotRemindedBetween(Instant fromInclusive, Instant toInclusive);
    int markRemindedIfPending(Long doseLogId, Instant now);
    List<DoseLog> findPendingOverdueNotNotifiedBetween(Instant fromInclusive, Instant toInclusive);
    int markOverdueNotifiedIfPending(Long doseLogId, Instant now);
    int markGroupNotifiedIfNotYet(Long doseLogId, Instant now);

    // 그룹 멤버 카드 직접 넛지용 — 주어진 스케줄 범위(호출측이 그룹 소속으로 이미 필터링) 안에서
    // 가장 오래 놓친(scheduledAt 오름차순 1건) PENDING dose. 없으면 empty.
    Optional<DoseLog> findEarliestOverduePendingByScheduleIds(Long patientId, Collection<Long> scheduleIds, Instant now);
}
