package com.pillmate.prescription.application;

import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.dto.PrescriptionSummary;
import com.pillmate.prescription.application.port.PrescriptionPeriodPort;
import com.pillmate.prescription.application.port.PrescriptionPeriodPort.PeriodStats;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.model.PrescriptionStatus;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GetPrescriptionsUseCase {

    private static final int SUMMARY_NAME_LIMIT = 3;
    private static final int DEFAULT_DURATION_DAYS = 30;

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionPeriodPort prescriptionPeriodPort;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PrescriptionSummary> list() {
        Long patientId = UserContext.get();
        List<Prescription> prescriptions = prescriptionRepository.findAllByPatientId(patientId);
        List<Long> ids = prescriptions.stream().map(Prescription::getId).toList();
        Map<Long, PeriodStats> statsMap = prescriptionPeriodPort.fetchStatsByPrescriptionIds(ids);
        LocalDate today = LocalDate.now(clock);
        return prescriptions.stream()
                .sorted(Comparator.comparing(Prescription::getPrescribedAt).reversed())
                .map(p -> toSummary(p, statsMap.get(p.getId()), today))
                .toList();
    }

    private PrescriptionSummary toSummary(Prescription p, PeriodStats stats, LocalDate today) {
        List<PrescribedDrug> drugs = p.getDrugs();
        LocalDate[] period = resolvePeriod(p.getPrescribedAt(), stats);
        LocalDate periodStart = period[0];
        LocalDate periodEnd = period[1];
        PrescriptionStatus status = resolveStatus(periodEnd, today);
        return new PrescriptionSummary(
                p.getId(), p.getPrescribedAt(), p.getOcrStatus(),
                drugs.size(), summarizeNames(drugs), p.getCreatedAt(),
                p.getLabel(), p.getMemo(), status,
                periodStart, periodEnd,
                daysRemaining(status, periodEnd, today),
                progressRate(status, periodStart, periodEnd, today),
                adherenceRate(stats));
    }

    private LocalDate[] resolvePeriod(LocalDate prescribedAt, PeriodStats stats) {
        if (stats != null) {
            return new LocalDate[]{stats.periodStart(), stats.periodEnd()};
        }
        return new LocalDate[]{prescribedAt, prescribedAt.plusDays(DEFAULT_DURATION_DAYS - 1)};
    }

    private PrescriptionStatus resolveStatus(LocalDate periodEnd, LocalDate today) {
        return !periodEnd.isBefore(today) ? PrescriptionStatus.ONGOING : PrescriptionStatus.COMPLETED;
    }

    private Integer daysRemaining(PrescriptionStatus status, LocalDate periodEnd, LocalDate today) {
        if (status != PrescriptionStatus.ONGOING) return null;
        return (int) Math.max(0, ChronoUnit.DAYS.between(today, periodEnd));
    }

    private Double progressRate(PrescriptionStatus status, LocalDate periodStart, LocalDate periodEnd, LocalDate today) {
        if (status == PrescriptionStatus.COMPLETED) return 1.0;
        if (!periodEnd.isAfter(periodStart)) return 1.0;
        long total = ChronoUnit.DAYS.between(periodStart, periodEnd);
        long elapsed = ChronoUnit.DAYS.between(periodStart, today);
        return Math.min(1.0, Math.max(0.0, (double) elapsed / total));
    }

    private Double adherenceRate(PeriodStats stats) {
        if (stats == null || stats.totalDoses() == 0) return null;
        return (double) stats.takenDoses() / stats.totalDoses();
    }

    private String summarizeNames(List<PrescribedDrug> drugs) {
        return drugs.stream()
                .limit(SUMMARY_NAME_LIMIT)
                .map(PrescribedDrug::getNameRaw)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
