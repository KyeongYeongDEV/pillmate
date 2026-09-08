package com.pillmate.caregroup.application.dto;

import com.pillmate.prescription.domain.model.PrescriptionStatus;

import java.time.LocalDate;

/**
 * 그룹 안에서 내 약봉투(처방전) 하나의 공유 설정 화면용 한 행.
 */
public record ShareablePrescriptionView(
        Long prescriptionId, String label, LocalDate prescribedAt, boolean shared, PrescriptionStatus status
) {}
