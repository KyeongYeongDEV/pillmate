package com.pillmate.prescription.application.port;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface PrescriptionPeriodPort {

    record PeriodStats(LocalDate periodStart, LocalDate periodEnd, long totalDoses, long takenDoses) {}

    Map<Long, PeriodStats> fetchStatsByPrescriptionIds(List<Long> prescriptionIds);
}
