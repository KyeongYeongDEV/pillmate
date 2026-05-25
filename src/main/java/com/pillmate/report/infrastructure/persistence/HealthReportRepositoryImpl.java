package com.pillmate.report.infrastructure.persistence;

import com.pillmate.report.domain.model.HealthReport;
import com.pillmate.report.domain.model.PeriodType;
import com.pillmate.report.domain.repository.HealthReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class HealthReportRepositoryImpl implements HealthReportRepository {

    private final HealthReportJpaRepository jpa;

    @Override public HealthReport save(HealthReport report) { return jpa.save(report); }

    @Override public Optional<HealthReport> findById(Long id) { return jpa.findById(id); }

    @Override
    public Optional<HealthReport> findByPatientIdAndPeriodStartAndPeriodType(
            Long patientId, LocalDate periodStart, PeriodType periodType) {
        return jpa.findByPatientIdAndPeriodStartAndPeriodType(patientId, periodStart, periodType);
    }

    @Override
    public Optional<HealthReport> findLatestByPatientId(Long patientId) {
        List<HealthReport> rows = jpa.findByPatientIdOrderByPeriodStartDesc(
                patientId, PageRequest.of(0, 1));
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    @Override
    public List<HealthReport> findRecentByPatientId(Long patientId, int limit) {
        return jpa.findByPatientIdOrderByPeriodStartDesc(patientId, PageRequest.of(0, limit));
    }
}
