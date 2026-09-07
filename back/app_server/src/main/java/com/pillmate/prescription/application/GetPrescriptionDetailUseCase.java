package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.PrescriptionViewAssembler.PeriodRange;
import com.pillmate.prescription.application.dto.PrescriptionDetailResponse;
import com.pillmate.prescription.application.dto.PrescriptionDetailResponse.DrugDetail;
import com.pillmate.prescription.application.dto.PrescriptionInsightView;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.application.port.FileStoragePort;
import com.pillmate.prescription.application.port.NutrientDepletionPort;
import com.pillmate.prescription.application.port.PrescriptionPeriodPort;
import com.pillmate.prescription.application.port.PrescriptionPeriodPort.PeriodStats;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.model.PrescriptionStatus;
import com.pillmate.prescription.domain.repository.PrescriptionInsightRepository;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GetPrescriptionDetailUseCase {

    private final PrescriptionRepository prescriptionRepository;
    private final DrugLookupPort drugLookupPort;
    private final FileStoragePort fileStoragePort;
    private final PatientAccessGuard patientAccessGuard;
    private final PrescriptionPeriodPort prescriptionPeriodPort;
    private final NutrientDepletionPort nutrientDepletionPort;
    private final PrescriptionInsightRepository prescriptionInsightRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PrescriptionDetailResponse detail(Long prescriptionId) {
        Prescription prescription = findOwnPrescription(prescriptionId);
        // 두 use case(본인 상세/그룹 공유 상세)가 조립 로직을 공유하되, 생성자 시그니처(테스트 호환) 유지 위해 여기서 직접 구성
        PrescriptionViewAssembler assembler = new PrescriptionViewAssembler(drugLookupPort, nutrientDepletionPort);
        Map<Long, PeriodStats> statsMap = prescriptionPeriodPort.fetchStatsByPrescriptionIds(List.of(prescriptionId));
        PeriodStats stats = statsMap.get(prescriptionId);
        LocalDate today = LocalDate.now(clock);
        PeriodRange period = assembler.resolvePeriod(prescription.getPrescribedAt(), stats);
        PrescriptionStatus status = assembler.resolveStatus(today, period.end());
        return new PrescriptionDetailResponse(
                prescription.getId(), prescription.getPrescribedAt(), prescription.getOcrStatus(),
                resolveImageUrl(prescription.getImageKey()), toDrugDetails(assembler, prescription.getDrugs()),
                prescription.getLabel(), prescription.getMemo(), prescription.getSymptom(), status,
                period.start(), period.end(),
                assembler.resolveDaysRemaining(status, today, period.end()),
                assembler.resolveProgressRate(status, today, period.start(), period.end()),
                assembler.resolveAdherenceRate(stats),
                resolveInsights(prescriptionId));
    }

    private List<PrescriptionInsightView> resolveInsights(Long prescriptionId) {
        List<PrescriptionInsightView> views = prescriptionInsightRepository.findByPrescriptionId(prescriptionId)
                .stream().map(PrescriptionInsightView::from).toList();
        return views.isEmpty() ? null : views;
    }

    private Prescription findOwnPrescription(Long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new PillmateException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        patientAccessGuard.requireAccess(UserContext.get(), prescription.getPatientId());
        return prescription;
    }

    private String resolveImageUrl(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) return null;
        return fileStoragePort.generateGetUrl(imageKey);
    }

    private List<DrugDetail> toDrugDetails(PrescriptionViewAssembler assembler, List<PrescribedDrug> drugs) {
        return assembler.toDrugViews(drugs).stream().map(this::toDrugDetail).toList();
    }

    private DrugDetail toDrugDetail(PrescriptionViewAssembler.DrugView view) {
        return new DrugDetail(
                view.nameRaw(), view.matchedDrugName(), view.matchedKdCode(),
                view.doseAmount(), view.doseUnit(), view.frequency(), view.durationDays(),
                view.confidence(), view.imageUrl(), view.nutrientNotes());
    }
}
