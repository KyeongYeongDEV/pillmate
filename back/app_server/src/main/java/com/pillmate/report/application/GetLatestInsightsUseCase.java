package com.pillmate.report.application;

import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.report.application.dto.ReportResponse.InsightView;
import com.pillmate.report.domain.repository.HealthReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetLatestInsightsUseCase {

    private final HealthReportRepository reportRepository;
    private final CareGroupGuard careGroupGuard;

    @Transactional(readOnly = true)
    public List<InsightView> getLatest(Long patientId) {
        careGroupGuard.requirePatientAccessible(patientId);
        return reportRepository.findLatestByPatientId(patientId)
                .map(r -> r.getInsights().stream().map(InsightView::from).toList())
                .orElseGet(List::of);
    }
}
