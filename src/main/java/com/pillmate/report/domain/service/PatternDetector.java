package com.pillmate.report.domain.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class PatternDetector {

    private static final double EVENING_MISS_THRESHOLD = 0.20;
    private static final double DRUG_MISS_THRESHOLD = 0.30;
    private static final int CONSECUTIVE_MISS_THRESHOLD = 3;
    private static final int TIME_DELAY_MINUTES_THRESHOLD = 60;

    public List<DetectedPattern> detect(PatternInput input) {
        List<DetectedPattern> patterns = new ArrayList<>();
        addEveningMiss(input, patterns);
        addDrugMiss(input, patterns);
        addConsecutiveMiss(input, patterns);
        addTimeDelay(input, patterns);
        return patterns;
    }

    private void addEveningMiss(PatternInput input, List<DetectedPattern> out) {
        int eveningTotal = input.eveningTotal();
        int eveningMissed = input.eveningMissed();
        if (eveningTotal > 0 && eveningMissed / (double) eveningTotal > EVENING_MISS_THRESHOLD) {
            out.add(DetectedPattern.of(DetectedPattern.PatternType.EVENING_MISS,
                    "저녁 복용 누락", eveningMissed, eveningTotal));
        }
    }

    private void addDrugMiss(PatternInput input, List<DetectedPattern> out) {
        for (Map.Entry<String, int[]> entry : input.missedByDrug().entrySet()) {
            int missed = entry.getValue()[0];
            int total = entry.getValue()[1];
            if (total > 0 && missed / (double) total > DRUG_MISS_THRESHOLD) {
                out.add(DetectedPattern.ofDrug(DetectedPattern.PatternType.DRUG_MISS,
                        "특정 약 누락", missed, total, entry.getKey()));
            }
        }
    }

    private void addConsecutiveMiss(PatternInput input, List<DetectedPattern> out) {
        if (input.maxConsecutiveMissDays() >= CONSECUTIVE_MISS_THRESHOLD) {
            out.add(DetectedPattern.of(DetectedPattern.PatternType.CONSECUTIVE_MISS,
                    "연속 누락", input.maxConsecutiveMissDays(), 0));
        }
    }

    private void addTimeDelay(PatternInput input, List<DetectedPattern> out) {
        if (input.avgDelayMinutes() >= TIME_DELAY_MINUTES_THRESHOLD) {
            out.add(DetectedPattern.of(DetectedPattern.PatternType.TIME_DELAY,
                    "복용 시간 지연", input.avgDelayMinutes(), 0));
        }
    }

    public record PatternInput(
            int eveningTotal,
            int eveningMissed,
            Map<String, int[]> missedByDrug,
            int maxConsecutiveMissDays,
            int avgDelayMinutes
    ) {}
}
