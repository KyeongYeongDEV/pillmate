package com.pillmate.doselog.infrastructure.persistence;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

interface DoseLogJpaRepository extends JpaRepository<DoseLog, Long> {

    List<DoseLog> findByPatientIdAndScheduledAtBetween(Long patientId, Instant from, Instant to);

    boolean existsByScheduleIdAndScheduledAtGreaterThanEqualAndScheduledAtLessThan(
            Long scheduleId, Instant fromInclusive, Instant toExclusive);

    List<DoseLog> findByScheduleIdAndStatusAndScheduledAtGreaterThanEqual(
            Long scheduleId, DoseStatus status, Instant fromInclusive);

    List<DoseLog> findByStatusAndCheckedAtBetweenAndGroupNotifiedAtIsNull(DoseStatus status, Instant from, Instant to);

    List<DoseLog> findByStatusAndScheduledAtBetweenAndRemindedAtIsNull(DoseStatus status, Instant from, Instant to);

    // 조건부 원자 클레임 — 동시 복용체크(TAKEN)·타 인스턴스 선점 시 0행 (entity merge 로 인한 되돌림 원천 차단)
    @Modifying
    @Query("""
            UPDATE DoseLog d SET d.remindedAt = :now
            WHERE d.id = :id AND d.remindedAt IS NULL AND d.status = :status
            """)
    int markRemindedIfStatus(@Param("id") Long id, @Param("now") Instant now,
                             @Param("status") DoseStatus status);

    List<DoseLog> findByStatusAndScheduledAtBetweenAndOverdueNotifiedAtIsNull(DoseStatus status, Instant from, Instant to);

    // 조건부 원자 클레임 — markRemindedIfStatus 와 동일 패턴 (지연 알림 중복발송 원천 차단)
    @Modifying
    @Query("""
            UPDATE DoseLog d SET d.overdueNotifiedAt = :now
            WHERE d.id = :id AND d.overdueNotifiedAt IS NULL AND d.status = :status
            """)
    int markOverdueNotifiedIfStatus(@Param("id") Long id, @Param("now") Instant now,
                                    @Param("status") DoseStatus status);

    // 조건부 원자 클레임 — 동시 폴러/재시도/HTTP 이중발송 차단 + 엔티티 merge 없이 flag 만 갱신해
    // 스테일 status 되돌림(lost-update) 원천 차단 (markRemindedIfStatus 와 동일 사상)
    @Modifying
    @Query("""
            UPDATE DoseLog d SET d.groupNotifiedAt = :now
            WHERE d.id = :id AND d.groupNotifiedAt IS NULL
            """)
    int markGroupNotifiedIfNull(@Param("id") Long id, @Param("now") Instant now);

    Optional<DoseLog> findFirstByPatientIdAndScheduleIdInAndStatusAndScheduledAtLessThanEqualOrderByScheduledAtAsc(
            Long patientId, Collection<Long> scheduleIds, DoseStatus status, Instant scheduledAtInclusive);
}
