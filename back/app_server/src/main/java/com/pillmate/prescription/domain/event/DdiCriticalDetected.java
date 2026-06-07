package com.pillmate.prescription.domain.event;

import java.util.List;

public record DdiCriticalDetected(
        Long userId,
        Long prescriptionId,
        List<String> warnings
) {}
