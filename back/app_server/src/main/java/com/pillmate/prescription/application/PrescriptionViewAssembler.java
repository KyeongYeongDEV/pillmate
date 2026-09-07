package com.pillmate.prescription.application;

import com.pillmate.prescription.application.dto.NutrientNote;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.application.port.NutrientDepletionPort;
import com.pillmate.prescription.application.port.PrescriptionPeriodPort.PeriodStats;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.PrescriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 처방전 기간/진행률/약 상세 계산 — 본인 상세(GetPrescriptionDetailUseCase)와
 * 그룹 공유 상세(GetSharedPrescriptionUseCase)가 공유하는 조립 로직.
 */
@Component
@RequiredArgsConstructor
public class PrescriptionViewAssembler {

    private static final int DEFAULT_DURATION_DAYS = 30;

    private final DrugLookupPort drugLookupPort;
    private final NutrientDepletionPort nutrientDepletionPort;

    public record PeriodRange(LocalDate start, LocalDate end) {}

    public record DrugView(
            String nameRaw,
            String matchedDrugName,
            String matchedKdCode,
            BigDecimal doseAmount,
            String doseUnit,
            Integer frequency,
            Integer durationDays,
            BigDecimal confidence,
            String imageUrl,
            List<NutrientNote> nutrientNotes
    ) {}

    public PeriodRange resolvePeriod(LocalDate prescribedAt, PeriodStats stats) {
        if (stats != null) return new PeriodRange(stats.periodStart(), stats.periodEnd());
        return new PeriodRange(prescribedAt, prescribedAt.plusDays(DEFAULT_DURATION_DAYS - 1));
    }

    public PrescriptionStatus resolveStatus(LocalDate today, LocalDate periodEnd) {
        return !periodEnd.isBefore(today) ? PrescriptionStatus.ONGOING : PrescriptionStatus.COMPLETED;
    }

    public Integer resolveDaysRemaining(PrescriptionStatus status, LocalDate today, LocalDate periodEnd) {
        if (status != PrescriptionStatus.ONGOING) return null;
        return (int) Math.max(0, ChronoUnit.DAYS.between(today, periodEnd));
    }

    public Double resolveProgressRate(PrescriptionStatus status, LocalDate today, LocalDate periodStart, LocalDate periodEnd) {
        if (status == PrescriptionStatus.COMPLETED) return 1.0;
        if (!periodEnd.isAfter(periodStart)) return 1.0;
        long total = ChronoUnit.DAYS.between(periodStart, periodEnd);
        long elapsed = ChronoUnit.DAYS.between(periodStart, today);
        return Math.min(1.0, Math.max(0.0, (double) elapsed / total));
    }

    public Double resolveAdherenceRate(PeriodStats stats) {
        if (stats == null || stats.totalDoses() == 0) return null;
        return (double) stats.takenDoses() / stats.totalDoses();
    }

    public List<DrugView> toDrugViews(List<PrescribedDrug> drugs) {
        List<Long> matchedIds = drugs.stream()
                .map(PrescribedDrug::getDrugId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, DrugLookupPort.DrugSummary> summaries = drugLookupPort.findByIds(matchedIds);
        Map<Long, List<NutrientNote>> nutrientMap = matchedIds.isEmpty()
                ? Map.of()
                : nutrientDepletionPort.findByDrugIds(matchedIds);
        return drugs.stream().map(drug -> toDrugView(drug, summaries, nutrientMap)).toList();
    }

    private DrugView toDrugView(PrescribedDrug drug,
                                 Map<Long, DrugLookupPort.DrugSummary> summaries,
                                 Map<Long, List<NutrientNote>> nutrientMap) {
        DrugLookupPort.DrugSummary summary = drug.getDrugId() != null ? summaries.get(drug.getDrugId()) : null;
        List<NutrientNote> notes = drug.getDrugId() != null
                ? nutrientMap.getOrDefault(drug.getDrugId(), List.of())
                : List.of();
        return new DrugView(
                drug.getNameRaw(),
                summary != null ? summary.name() : null,
                summary != null ? summary.kdCode() : null,
                drug.getDoseAmount(), drug.getDoseUnit(),
                drug.getFrequency(), drug.getDurationDays(), drug.getConfidence(),
                summary != null ? summary.imageUrl() : null,
                notes.isEmpty() ? null : notes);
    }
}
