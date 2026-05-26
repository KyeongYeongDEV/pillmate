package com.pillmate.report.domain.repository;

import com.pillmate.report.domain.model.HealthReport;
import com.pillmate.report.domain.model.PeriodType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface HealthReportRepository {
    HealthReport save(HealthReport report);
    Optional<HealthReport> findById(Long id);
    Optional<HealthReport> findByPatientIdAndPeriodStartAndPeriodType(
            Long patientId, LocalDate periodStart, PeriodType periodType);
    Optional<HealthReport> findLatestByPatientId(Long patientId);
    List<HealthReport> findRecentByPatientId(Long patientId, int limit);
}
