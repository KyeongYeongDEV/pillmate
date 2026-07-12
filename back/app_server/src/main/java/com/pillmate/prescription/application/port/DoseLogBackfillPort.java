package com.pillmate.prescription.application.port;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface DoseLogBackfillPort {

    /**
     * 오늘(KST) 날짜에 한해, 활성 기간(startDate~endDate)에 오늘이 포함되는 슬롯에 대해서만
     * dose_logs 를 즉시 생성(백필)한다. 이미 존재하면 건너뜀(멱등 — 야간 배치와 겹쳐도 안전).
     */
    int backfillToday(Long patientId, List<BackfillSlot> slots, LocalDate today);

    record BackfillSlot(Long scheduleId, LocalTime customTime, LocalDate startDate, LocalDate endDate) {}
}
