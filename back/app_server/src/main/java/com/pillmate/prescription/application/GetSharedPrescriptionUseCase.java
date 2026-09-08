package com.pillmate.prescription.application;

import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.prescription.application.PrescriptionViewAssembler.PeriodRange;
import com.pillmate.prescription.application.dto.SharedPrescriptionResponse;
import com.pillmate.prescription.application.dto.SharedPrescriptionResponse.SharedDrugDetail;
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
 * 케어그룹에 공유된 약봉투(처방전)의 알약 정보 조회 — 약봉투 단위 공유 설정(L2 권한)만 신뢰한다.
 */
@Service
@RequiredArgsConstructor
public class GetSharedPrescriptionUseCase {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionViewAssembler assembler;
    private final PrescriptionPeriodPort prescriptionPeriodPort;
    private final MembershipRepository membershipRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public SharedPrescriptionResponse execute(Long groupId, Long prescriptionId, Long viewerUserId) {
        Prescription prescription = findPrescription(prescriptionId);
        requireShared(groupId, prescription, viewerUserId);

        PeriodStats stats = prescriptionPeriodPort
                .fetchStatsByPrescriptionIds(List.of(prescriptionId)).get(prescriptionId);
        LocalDate today = LocalDate.now(clock);
        PeriodRange period = assembler.resolvePeriod(prescription.getPrescribedAt(), stats);
        PrescriptionStatus status = assembler.resolveStatus(today, period.end());

        return new SharedPrescriptionResponse(
                prescription.getId(), prescription.getPatientId(), prescription.getPrescribedAt(),
                prescription.getLabel(), status, period.start(), period.end(),
                assembler.resolveDaysRemaining(status, today, period.end()),
                assembler.resolveProgressRate(status, today, period.start(), period.end()),
                assembler.resolveAdherenceRate(stats),
                toSharedDrugDetails(prescription));
    }

    private Prescription findPrescription(Long prescriptionId) {
        return prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new PillmateException(ErrorCode.PRESCRIPTION_NOT_FOUND));
    }

    // 약봉투(처방전) 단위 공유 판정 — 본인이거나, 그 약봉투가 정확히 groupId 에 공유 켠 상태이고
    // owner·viewer 둘 다 그 그룹 ACTIVE 멤버인 경우만 허용(크로스그룹 차단).
    private void requireShared(Long groupId, Prescription prescription, Long viewerUserId) {
        Long ownerUserId = prescription.getPatientId();
        if (viewerUserId.equals(ownerUserId)) {
            return;
        }
        boolean allowed = prescription.isSharedWithGroup()
                && groupId.equals(prescription.getCareGroupId())
                && membershipRepository.existsByCareGroupIdAndUserId(groupId, ownerUserId)
                && membershipRepository.existsByCareGroupIdAndUserId(groupId, viewerUserId);
        if (!allowed) {
            throw new PillmateException(ErrorCode.MEDICATION_SHARE_NOT_GRANTED);
        }
    }

    private List<SharedDrugDetail> toSharedDrugDetails(Prescription prescription) {
        return assembler.toDrugViews(prescription.getDrugs()).stream()
                .map(this::toSharedDrugDetail)
                .toList();
    }

    private SharedDrugDetail toSharedDrugDetail(PrescriptionViewAssembler.DrugView view) {
        return new SharedDrugDetail(
                view.nameRaw(), view.matchedDrugName(), view.matchedKdCode(),
                view.doseAmount(), view.doseUnit(), view.frequency(), view.durationDays(),
                view.imageUrl(), view.nutrientNotes());
    }
}
