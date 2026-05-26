package com.pillmate.report.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.report.application.dto.ReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RefreshInsightsUseCase {

    private final GenerateWeeklyReportUseCase generateWeekly;
    private final CareGroupGuard careGroupGuard;
    private final Clock clock;

    private final Map<Long, LocalDate> lastRefreshByPatient = new ConcurrentHashMap<>();

    @Transactional
    public ReportResponse refresh(Long patientId) {
        careGroupGuard.requirePatientAccessible(patientId);
        LocalDate today = LocalDate.now(clock);
        requireWithinDailyLimit(patientId, today);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return ReportResponse.from(generateWeekly.generate(patientId, weekStart));
    }

    private void requireWithinDailyLimit(Long patientId, LocalDate today) {
        LocalDate prev = lastRefreshByPatient.get(patientId);
        if (prev != null && prev.equals(today)) {
            throw new PillmateException(ErrorCode.REPORT_REFRESH_RATE_LIMITED);
        }
        lastRefreshByPatient.put(patientId, today);
    }
}
