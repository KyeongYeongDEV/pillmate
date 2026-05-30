package com.pillmate.schedule.application.dto;

import java.util.List;

public record SlotView(
        String id,
        String time,
        String label,
        String state,
        List<String> items,
        Long doseLogId,
        int drugCount,
        List<String> pillColors
) {}
