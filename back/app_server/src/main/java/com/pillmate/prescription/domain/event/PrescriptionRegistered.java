package com.pillmate.prescription.domain.event;

public record PrescriptionRegistered(
        Long actorUserId,
        Long prescriptionId
) {}
