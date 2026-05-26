package com.pillmate.report.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.report.application.GetDailyAdherenceUseCase;
import com.pillmate.report.application.GetLatestInsightsUseCase;
import com.pillmate.report.application.GetWeeklyReportUseCase;
import com.pillmate.report.application.RefreshInsightsUseCase;
import com.pillmate.report.application.dto.ReportResponse;
import com.pillmate.report.application.dto.ReportResponse.InsightView;
import com.pillmate.report.domain.model.DailyBreakdown;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportController {

    private final GetWeeklyReportUseCase getWeeklyReport;
    private final GetDailyAdherenceUseCase getDailyAdherence;
    private final GetLatestInsightsUseCase getLatestInsights;
    private final RefreshInsightsUseCase refreshInsights;

    @GetMapping("/weekly")
    public ResponseEntity<ApiResponse<ReportResponse>> weekly(
            @RequestParam Long patientId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        LocalDate target = (weekStart == null)
                ? LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : weekStart;
        return ResponseEntity.ok(ApiResponse.success(getWeeklyReport.getOrGenerate(patientId, target)));
    }

    @GetMapping("/monthly")
    public ResponseEntity<ApiResponse<ReportResponse>> monthly(
            @RequestParam Long patientId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate month) {
        LocalDate base = (month == null) ? LocalDate.now() : month;
        LocalDate weekStart = base.with(TemporalAdjusters.firstDayOfMonth())
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return ResponseEntity.ok(ApiResponse.success(getWeeklyReport.getOrGenerate(patientId, weekStart)));
    }

    @GetMapping("/daily")
    public ResponseEntity<ApiResponse<List<DailyBreakdown>>> daily(
            @RequestParam Long patientId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success(getDailyAdherence.getDaily(patientId, from, to)));
    }

    @GetMapping("/insights")
    public ResponseEntity<ApiResponse<List<InsightView>>> insights(@RequestParam Long patientId) {
        return ResponseEntity.ok(ApiResponse.success(getLatestInsights.getLatest(patientId)));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<ReportResponse>> refresh(@RequestParam Long patientId) {
        return ResponseEntity.ok(ApiResponse.success(refreshInsights.refresh(patientId)));
    }
}
