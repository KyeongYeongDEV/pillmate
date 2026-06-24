package com.pillmate.prescription.presentation.dto;

import com.pillmate.prescription.application.dto.DrugItem;
import com.pillmate.prescription.application.dto.RegisterPrescriptionCommand;
import com.pillmate.prescription.application.dto.ScheduleSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record RegisterPrescriptionRequest(
        @NotNull LocalDate prescribedAt,
        String imageKey,
        @NotEmpty @Valid List<Item> items,
        @Valid ScheduleSpecRequest schedule,
        @Size(max = 100) String label,
        @Size(max = 500) String memo
) {
    public RegisterPrescriptionRequest(LocalDate prescribedAt, String imageKey, List<Item> items) {
        this(prescribedAt, imageKey, items, null, null, null);
    }

    public RegisterPrescriptionCommand toCommand(Long patientId) {
        return new RegisterPrescriptionCommand(
                patientId, prescribedAt, imageKey,
                items.stream().map(Item::toDrugItem).toList(),
                schedule != null ? schedule.toSpec() : null,
                label,
                memo);
    }

    public record Item(
            String kdCode,
            @NotBlank String nameRaw,
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

    public record ScheduleSpecRequest(
            @NotNull Long careGroupId,
            @Valid List<SlotRequest> slots,
            LocalDate startDate,
            LocalDate endDate
    ) {
        public ScheduleSpec toSpec() {
            return new ScheduleSpec(careGroupId, toSlotInputs(), startDate, endDate);
        }

        private List<ScheduleSpec.SlotInput> toSlotInputs() {
            return slots == null ? null : slots.stream().map(SlotRequest::toInput).toList();
        }
    }

    public record SlotRequest(@NotBlank String timeOfDay, LocalTime customTime) {
        public ScheduleSpec.SlotInput toInput() {
            return new ScheduleSpec.SlotInput(timeOfDay, customTime);
        }
    }
}
