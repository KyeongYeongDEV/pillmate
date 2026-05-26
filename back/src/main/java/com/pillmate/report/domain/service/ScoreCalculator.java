package com.pillmate.report.domain.service;

import org.springframework.stereotype.Component;

@Component
public class ScoreCalculator {

    private static final double ADHERENCE_WEIGHT = 0.7;
    private static final double TIME_WEIGHT = 0.2;
    private static final double COMPLETION_WEIGHT = 0.1;

    public int calculate(int taken, int total, int onTime, int prescriptionComplete) {
        if (total <= 0) {
            return 0;
        }
        double adherence = ratio(taken, total) * 100;
        double timely = ratio(onTime, total) * 100;
        double completion = clamp(prescriptionComplete);
        double score = adherence * ADHERENCE_WEIGHT
                + timely * TIME_WEIGHT
                + completion * COMPLETION_WEIGHT;
        return (int) Math.round(clamp(score));
    }

    private double ratio(int part, int total) {
        return total == 0 ? 0.0 : part / (double) total;
    }

    private double clamp(double v) {
        return Math.min(100, Math.max(0, v));
    }
}
