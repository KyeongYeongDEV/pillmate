package com.pillmate.doselog.application.dto;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;

import java.time.Instant;

public record DoseLogResponse(
        Long id,
        Long scheduleId,
        Long patientId,
        Instant scheduledAt,
        DoseStatus status,
        Long checkedBy,
        Instant checkedAt,
        String skipReason
) {
    public static DoseLogResponse from(DoseLog log) {
        return new DoseLogResponse(
                log.getId(), log.getScheduleId(), log.getPatientId(),
                log.getScheduledAt(), log.getStatus(),
                log.getCheckedBy(), log.getCheckedAt(), log.getSkipReason());
    }
}
