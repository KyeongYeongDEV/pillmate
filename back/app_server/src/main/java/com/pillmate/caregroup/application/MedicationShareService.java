package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.ShareablePrescriptionView;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.prescription.application.PrescriptionViewAssembler;
import com.pillmate.prescription.application.PrescriptionViewAssembler.PeriodRange;
import com.pillmate.prescription.application.port.PrescriptionPeriodPort;
import com.pillmate.prescription.application.port.PrescriptionPeriodPort.PeriodStats;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.model.PrescriptionStatus;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 알약 정보(L2) 공유 설정 — 약봉투(처방전) 단위 그룹 공유 토글.
 * L1(복약 여부)은 CareGroupGuard.requirePatientAccessible 로 별도 공개 — 본 서비스와 무관.
 */
@Service
@RequiredArgsConstructor
public class MedicationShareService {

    private final PrescriptionRepository prescriptionRepository;
    private final MembershipRepository membershipRepository;
    private final PrescriptionViewAssembler assembler;
    private final PrescriptionPeriodPort prescriptionPeriodPort;
    private final Clock clock;

    // 이 그룹에서 "현재 복용중(ONGOING)"인 내 약봉투 목록 + 각각의 공유 여부.
    @Transactional(readOnly = true)
    public List<ShareablePrescriptionView> getShareablePrescriptions(Long groupId, Long ownerUserId) {
        requireActiveMember(groupId, ownerUserId);
        List<Prescription> mine = prescriptionRepository.findAllByPatientIdAndCareGroupId(ownerUserId, groupId);
        Map<Long, PeriodStats> statsMap = prescriptionPeriodPort
                .fetchStatsByPrescriptionIds(mine.stream().map(Prescription::getId).toList());
        LocalDate today = LocalDate.now(clock);
        return mine.stream()
                .map(p -> toShareableView(p, statsMap.get(p.getId()), today))
                .filter(v -> v.status() == PrescriptionStatus.ONGOING)
                .toList();
    }

    // 약봉투 하나의 공유 on/off. 소유자 본인 + 그 약봉투가 정확히 이 groupId 소속인지 반드시 확인
    // (다른 그룹 약봉투를 이 groupId 로 토글 못 하게, 남의 약봉투를 못 건드리게 — 둘 다 필수 방어).
    @Transactional
    public void updatePrescriptionShare(Long groupId, Long ownerUserId, Long prescriptionId, boolean enabled) {
        requireActiveMember(groupId, ownerUserId);
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new PillmateException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        requireOwnPrescriptionInGroup(prescription, ownerUserId, groupId);
        if (enabled) {
            prescription.shareWithGroup();
        } else {
            prescription.unshareFromGroup();
        }
        prescriptionRepository.save(prescription);
    }

    private ShareablePrescriptionView toShareableView(Prescription p, PeriodStats stats, LocalDate today) {
        PeriodRange period = assembler.resolvePeriod(p.getPrescribedAt(), stats);
        PrescriptionStatus status = assembler.resolveStatus(today, period.end());
        return new ShareablePrescriptionView(
                p.getId(), p.getLabel(), p.getPrescribedAt(), p.isSharedWithGroup(), status);
    }

    private void requireOwnPrescriptionInGroup(Prescription prescription, Long ownerUserId, Long groupId) {
        boolean isOwner = prescription.getPatientId().equals(ownerUserId);
        boolean isThisGroup = groupId.equals(prescription.getCareGroupId());
        if (!isOwner || !isThisGroup) {
            throw new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED);
        }
    }

    private void requireActiveMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByCareGroupIdAndUserId(groupId, userId)) {
            throw new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        }
    }
}
