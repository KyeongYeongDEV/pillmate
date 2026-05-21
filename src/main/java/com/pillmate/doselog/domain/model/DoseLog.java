package com.pillmate.doselog.domain.model;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "dose_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DoseLog {

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

    public static DoseLog of(Long scheduleId, Long patientId, Instant scheduledAt) {
        DoseLog log = new DoseLog();
        log.scheduleId = scheduleId;
        log.patientId = patientId;
        log.scheduledAt = scheduledAt;
        log.status = DoseStatus.PENDING;
        return log;
    }

    public void take(Long checkedBy) {
        if (status != DoseStatus.PENDING) {
            throw new PillmateException(ErrorCode.INVALID_REQUEST);
        }
        this.status = DoseStatus.TAKEN;
        this.checkedBy = checkedBy;
        this.checkedAt = Instant.now();
    }

    public void skip(Long checkedBy, String reason) {
        if (status != DoseStatus.PENDING) {
            throw new PillmateException(ErrorCode.INVALID_REQUEST);
        }
        this.status = DoseStatus.SKIPPED;
        this.checkedBy = checkedBy;
        this.checkedAt = Instant.now();
        this.skipReason = reason;
    }
}
