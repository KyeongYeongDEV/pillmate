package com.pillmate.prescription.presentation.dto;

import com.pillmate.prescription.application.dto.DrugItem;
import com.pillmate.prescription.application.dto.RegisterPrescriptionCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RegisterPrescriptionRequest(
        @NotNull Long careGroupId,
        @NotNull Long patientId,
        @NotNull LocalDate prescribedAt,
        @NotBlank String imageKey,
        @NotEmpty @Valid List<Item> items
) {
    public RegisterPrescriptionCommand toCommand() {
        return new RegisterPrescriptionCommand(
                careGroupId, patientId, prescribedAt, imageKey,
                items.stream().map(Item::toDrugItem).toList());
    }

    public record Item(
            @NotBlank String kdCode,
            String nameRaw,
            BigDecimal doseAmount,
            String doseUnit,
            Integer frequency,
            Integer durationDays,
            BigDecimal confidence
    ) {
        public DrugItem toDrugItem() {
            return new DrugItem(kdCode, nameRaw, doseAmount, doseUnit, frequency, durationDays, confidence);
        }
    }
}
