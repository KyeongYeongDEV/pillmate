package com.pillmate.doselog.infrastructure.persistence;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class DoseLogRepositoryImpl implements DoseLogRepository {

    private final DoseLogJpaRepository jpa;

    @Override public DoseLog save(DoseLog log) { return jpa.save(log); }
    @Override public List<DoseLog> saveAll(List<DoseLog> logs) { return jpa.saveAll(logs); }
    @Override public Optional<DoseLog> findById(Long id) { return jpa.findById(id); }
    @Override public List<DoseLog> findAllByIdIn(java.util.Collection<Long> ids) {
        return jpa.findAllById(ids);
    }

    @Override
    public List<DoseLog> findByPatientIdAndScheduledAtBetween(Long patientId, Instant from, Instant to) {
        return jpa.findByPatientIdAndScheduledAtBetween(patientId, from, to);
    }

    @Override
    public boolean existsByScheduleIdAndScheduledAtInRange(Long scheduleId, Instant fromInclusive, Instant toExclusive) {
        return jpa.existsByScheduleIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
                scheduleId, fromInclusive, toExclusive);
    }

    @Override
    public List<DoseLog> findByScheduleIdAndStatusFrom(Long scheduleId, DoseStatus status, Instant fromInclusive) {
        return jpa.findByScheduleIdAndStatusAndScheduledAtGreaterThanEqual(scheduleId, status, fromInclusive);
    }

    @Override
    public List<DoseLog> findTakenNotGroupNotifiedBetween(Instant fromInclusive, Instant toInclusive) {
        return jpa.findByStatusAndCheckedAtBetweenAndGroupNotifiedAtIsNull(
                DoseStatus.TAKEN, fromInclusive, toInclusive);
    }

    @Override
    public List<DoseLog> findPendingNotRemindedBetween(Instant fromInclusive, Instant toInclusive) {
        return jpa.findByStatusAndScheduledAtBetweenAndRemindedAtIsNull(
                DoseStatus.PENDING, fromInclusive, toInclusive);
    }

    // @Modifying JPQL 은 활성 트랜잭션 필수 — 폴러 호출 경로는 비트랜잭션이라 여기서 개시
    // (infra @Transactional 선례: PrescriptionLookupAdapter)
    @Override
    @Transactional
    public int markRemindedIfPending(Long doseLogId, Instant now) {
        return jpa.markRemindedIfStatus(doseLogId, now, DoseStatus.PENDING);
    }
}
