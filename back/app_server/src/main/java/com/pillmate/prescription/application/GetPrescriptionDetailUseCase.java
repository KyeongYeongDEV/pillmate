package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.dto.NutrientNote;
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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GetPrescriptionDetailUseCase {

    private static final int DEFAULT_DURATION_DAYS = 30;

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
        Map<Long, PeriodStats> statsMap = prescriptionPeriodPort.fetchStatsByPrescriptionIds(List.of(prescriptionId));
        PeriodStats stats = statsMap.get(prescriptionId);
        LocalDate today = LocalDate.now(clock);
        LocalDate[] period = resolvePeriod(prescription.getPrescribedAt(), stats);
        PrescriptionStatus status = resolveStatus(today, period[1]);
        return new PrescriptionDetailResponse(
                prescription.getId(), prescription.getPrescribedAt(), prescription.getOcrStatus(),
                resolveImageUrl(prescription.getImageKey()), toDrugDetails(prescription.getDrugs()),
                prescription.getLabel(), prescription.getMemo(), prescription.getSymptom(), status,
                period[0], period[1],
                resolveDaysRemaining(status, today, period[1]),
                resolveProgressRate(status, today, period[0], period[1]),
                resolveAdherenceRate(stats),
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

    private LocalDate[] resolvePeriod(LocalDate prescribedAt, PeriodStats stats) {
        if (stats != null) return new LocalDate[]{stats.periodStart(), stats.periodEnd()};
        return new LocalDate[]{prescribedAt, prescribedAt.plusDays(DEFAULT_DURATION_DAYS - 1)};
    }

    private PrescriptionStatus resolveStatus(LocalDate today, LocalDate end) {
        return !end.isBefore(today) ? PrescriptionStatus.ONGOING : PrescriptionStatus.COMPLETED;
    }

    private Integer resolveDaysRemaining(PrescriptionStatus status, LocalDate today, LocalDate end) {
        if (status != PrescriptionStatus.ONGOING) return null;
        return (int) Math.max(0, ChronoUnit.DAYS.between(today, end));
    }

    private Double resolveProgressRate(PrescriptionStatus status, LocalDate today, LocalDate start, LocalDate end) {
        if (status == PrescriptionStatus.COMPLETED) return 1.0;
        if (!end.isAfter(start)) return 1.0;
        long total = ChronoUnit.DAYS.between(start, end);
        long elapsed = ChronoUnit.DAYS.between(start, today);
        return Math.min(1.0, Math.max(0.0, (double) elapsed / total));
    }

    private Double resolveAdherenceRate(PeriodStats stats) {
        if (stats == null || stats.totalDoses() == 0) return null;
        return (double) stats.takenDoses() / stats.totalDoses();
    }

    private String resolveImageUrl(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) return null;
        return fileStoragePort.generateGetUrl(imageKey);
    }

    private List<DrugDetail> toDrugDetails(List<PrescribedDrug> drugs) {
        List<Long> matchedIds = drugs.stream()
                .map(PrescribedDrug::getDrugId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, DrugLookupPort.DrugSummary> summaries = drugLookupPort.findByIds(matchedIds);
        Map<Long, List<NutrientNote>> nutrientMap = matchedIds.isEmpty()
                ? Map.of()
                : nutrientDepletionPort.findByDrugIds(matchedIds);
        return drugs.stream().map(d -> toDrugDetail(d, summaries, nutrientMap)).toList();
    }

    private DrugDetail toDrugDetail(PrescribedDrug drug,
                                    Map<Long, DrugLookupPort.DrugSummary> summaries,
                                    Map<Long, List<NutrientNote>> nutrientMap) {
        DrugLookupPort.DrugSummary summary = drug.getDrugId() != null ? summaries.get(drug.getDrugId()) : null;
        List<NutrientNote> notes = drug.getDrugId() != null
                ? nutrientMap.getOrDefault(drug.getDrugId(), List.of())
                : List.of();
        return new DrugDetail(
                drug.getNameRaw(),
                summary != null ? summary.name() : null,
                summary != null ? summary.kdCode() : null,
                drug.getDoseAmount(), drug.getDoseUnit(),
                drug.getFrequency(), drug.getDurationDays(), drug.getConfidence(),
                summary != null ? summary.imageUrl() : null,
                notes.isEmpty() ? null : notes);
    }
}
