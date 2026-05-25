package com.pillmate.report.application;

import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.report.application.port.DoseLogStatsPort;
import com.pillmate.report.application.port.DoseLogStatsPort.DailyCount;
import com.pillmate.report.domain.model.DailyBreakdown;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetDailyAdherenceUseCase {

    private final DoseLogStatsPort statsPort;
    private final CareGroupGuard careGroupGuard;

    @Transactional(readOnly = true)
    public List<DailyBreakdown> getDaily(Long patientId, LocalDate from, LocalDate to) {
        careGroupGuard.requirePatientAccessible(patientId);
        List<DailyCount> counts = statsPort.dailyCounts(patientId, from, to);
        return counts.stream()
                .map(c -> DailyBreakdown.of(c.date(), c.taken(), c.total()))
                .toList();
    }
}
