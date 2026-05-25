package com.pillmate.report.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.report.application.dto.ReportResponse;
import com.pillmate.report.domain.model.HealthReport;
import com.pillmate.report.domain.model.PeriodType;
import com.pillmate.report.domain.repository.HealthReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class GetWeeklyReportUseCase {

    private final HealthReportRepository reportRepository;
    private final GenerateWeeklyReportUseCase generateWeekly;
    private final CareGroupGuard careGroupGuard;

    @Transactional
    public ReportResponse getOrGenerate(Long patientId, LocalDate weekStart) {
        careGroupGuard.requirePatientAccessible(patientId);
        HealthReport report = reportRepository
                .findByPatientIdAndPeriodStartAndPeriodType(patientId, weekStart, PeriodType.WEEKLY)
                .orElseGet(() -> generateWeekly.generate(patientId, weekStart));
        return ReportResponse.from(report);
    }

    @Transactional(readOnly = true)
    public ReportResponse getOrThrow(Long patientId, LocalDate weekStart) {
        careGroupGuard.requirePatientAccessible(patientId);
        HealthReport report = reportRepository
                .findByPatientIdAndPeriodStartAndPeriodType(patientId, weekStart, PeriodType.WEEKLY)
                .orElseThrow(() -> new PillmateException(ErrorCode.REPORT_NOT_FOUND));
        return ReportResponse.from(report);
    }
}
