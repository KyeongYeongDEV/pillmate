package com.pillmate.prescription.application;

import com.pillmate.caregroup.application.MedicationShareService;
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
 * 케어그룹에 공유된 약봉투(처방전)의 알약 정보 조회 — 구성원축(누구에게)과 약봉투축(어느 약봉투를)
 * 두 축이 모두 켜져 있어야 열람을 허용한다.
 */
@Service
@RequiredArgsConstructor
public class GetSharedPrescriptionUseCase {

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionViewAssembler assembler;
    private final PrescriptionPeriodPort prescriptionPeriodPort;
    private final MedicationShareService medicationShareService;
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

    // 2축 AND 판정 — 본인이거나, 약봉투축(공유 켬)과 구성원축(owner→viewer grant) 이 둘 다
    // 성립해야 허용한다. 구성원축 판정(멤버십·크로스그룹 차단 포함)은 MedicationShareService 에 위임.
    private void requireShared(Long groupId, Prescription prescription, Long viewerUserId) {
        Long ownerUserId = prescription.getPatientId();
        if (viewerUserId.equals(ownerUserId)) {
            return;
        }
        boolean allowed = prescription.isSharedWithGroup()
                && medicationShareService.isMemberGranted(groupId, ownerUserId, viewerUserId);
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
