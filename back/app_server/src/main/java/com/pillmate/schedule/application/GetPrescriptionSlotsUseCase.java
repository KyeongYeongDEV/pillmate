package com.pillmate.schedule.application;

import com.pillmate.schedule.application.dto.SlotEditView;

import java.util.List;

public interface GetPrescriptionSlotsUseCase {
    List<SlotEditView> execute(Long prescriptionId);
}
