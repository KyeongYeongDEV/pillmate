package com.pillmate.schedule.application.dto;

import com.pillmate.schedule.domain.model.TimeOfDay;

import java.time.LocalDate;

public record SlotEditView(
        Long scheduleId,
        TimeOfDay timeOfDay,
        String time,
        LocalDate endDate,
        boolean editable
) {}
