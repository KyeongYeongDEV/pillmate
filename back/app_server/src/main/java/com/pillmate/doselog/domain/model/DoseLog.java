package com.pillmate.doselog.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

@Entity
@Table(name = "dose_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DoseLog {

    private static final Duration DELAYED_THRESHOLD = Duration.ofMinutes(30);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long scheduleId;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Instant scheduledAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DoseStatus status;

    private Long checkedBy;
    private Instant checkedAt;
    private String skipReason;
    private Instant groupNotifiedAt;
    private Instant remindedAt;
    private Instant overdueNotifiedAt;

    public static DoseLog of(Long scheduleId, Long patientId, Instant scheduledAt) {
        DoseLog log = new DoseLog();
        log.scheduleId = scheduleId;
        log.patientId = patientId;
        log.scheduledAt = scheduledAt;
        log.status = DoseStatus.PENDING;
        return log;
    }

    public void take(Long checkedBy, Clock clock) {
        if (status == DoseStatus.TAKEN) {
            return;
        }
        this.status = DoseStatus.TAKEN;
        this.checkedBy = checkedBy;
        this.checkedAt = Instant.now(clock);
    }

    public void take(Long checkedBy) {
        take(checkedBy, Clock.systemUTC());
    }

    public void skip(Long checkedBy, String reason, Clock clock) {
        if (status == DoseStatus.SKIPPED) {
            return;
        }
        this.status = DoseStatus.SKIPPED;
        this.checkedBy = checkedBy;
        this.checkedAt = Instant.now(clock);
        this.skipReason = reason;
    }

    public void skip(Long checkedBy, String reason) {
        skip(checkedBy, reason, Clock.systemUTC());
    }

    public void reschedule(Instant newScheduledAt) {
        if (status != DoseStatus.PENDING) {
            return;
        }
        // 시각이 실제로 바뀔 때만 새 알림 대상으로 취급 — 무조건 리셋하면 동일 시각 재저장(폼 재제출)에도
        // 이미 리마인드된 행이 리셋되어 poller RECENCY_WINDOW 안에서 중복 재발송될 수 있음 (2026-07-14)
        if (!newScheduledAt.equals(this.scheduledAt)) {
            this.remindedAt = null;
        }
        this.scheduledAt = newScheduledAt;
    }

    public boolean cancel() {
        if (status != DoseStatus.TAKEN) {
            return false;
        }
        this.status = DoseStatus.PENDING;
        this.checkedBy = null;
        this.checkedAt = null;
        this.skipReason = null;
        this.groupNotifiedAt = null;
        return true;
    }

    public void cancelForPeriodChange() {
        if (status == DoseStatus.PENDING) {
            this.status = DoseStatus.SKIPPED;
            this.skipReason = "기간 변경";
        }
    }

    public void markGroupNotified(Instant now) {
        this.groupNotifiedAt = now;
    }

    public boolean isGroupNotified() {
        return groupNotifiedAt != null;
    }

    public boolean isEditableOn(Clock clock) {
        return true;
    }

    public boolean isDelayed(Clock clock) {
        if (status != DoseStatus.PENDING) {
            return false;
        }
        return Instant.now(clock).isAfter(scheduledAt.plus(DELAYED_THRESHOLD));
    }
}
