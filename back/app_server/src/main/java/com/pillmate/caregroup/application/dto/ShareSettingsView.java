package com.pillmate.caregroup.application.dto;

import java.util.List;

/**
 * 공유 설정 화면 한 번의 조회로 필요한 전부 — 구성원별 공유 여부 + 내 약봉투별 공유 여부.
 * 최종 열람 판정은 두 축의 AND(둘 다 켜져야 공개) — MedicationShareService.isMemberGranted 참조.
 */
public record ShareSettingsView(
        List<ShareSettingView> members,
        List<ShareablePrescriptionView> prescriptions
) {}
