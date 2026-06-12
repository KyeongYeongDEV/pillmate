package com.pillmate.doselog.domain.event;

public record DoseCheckCanceled(
        Long doseLogId,
        Long actorUserId,
        Long scheduleId
) {}
