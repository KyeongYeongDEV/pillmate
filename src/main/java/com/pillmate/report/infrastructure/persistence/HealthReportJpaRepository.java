package com.pillmate.report.infrastructure.persistence;

import com.pillmate.report.domain.model.HealthReport;
import com.pillmate.report.domain.model.PeriodType;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface HealthReportJpaRepository extends JpaRepository<HealthReport, Long> {

    Optional<HealthReport> findByPatientIdAndPeriodStartAndPeriodType(
            Long patientId, LocalDate periodStart, PeriodType periodType);

    List<HealthReport> findByPatientIdOrderByPeriodStartDesc(Long patientId, PageRequest pageable);
}
