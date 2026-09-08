package com.pillmate.schedule.application.port;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public interface ScheduleDayQueryPort {

    List<DayScheduleProjection> findByPatientAndDate(Long patientId, LocalDate date);

    record DayScheduleProjection(
            Long scheduleId,
            LocalTime customTime,
            Long prescriptionId,
            LocalDate prescribedAt,
            List<String> drugNames,
            List<String> pillColors,
            Long doseLogId,
            String doseStatus,
            String singleDrugName,   // prescription_id IS NULL 레거시 행의 drugs.name fallback
            String label,            // 사용자가 등록 시 지정한 약봉투 이름 (prescriptions.label, null/blank 가능)
            Long prescriptionCareGroupId, // prescriptions.care_group_id — 약봉투(처방전) 단위 공유 판정용
            Boolean sharedWithGroup       // prescriptions.shared_with_group — null 이면 false 취급
    ) {}
}
