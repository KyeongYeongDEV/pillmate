package com.pillmate.schedule.application;

import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.dto.DayScheduleResponse;
import com.pillmate.schedule.application.dto.SlotView;
import com.pillmate.schedule.application.port.ScheduleDayQueryPort;
import com.pillmate.schedule.application.port.ScheduleDayQueryPort.DayScheduleProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GetDayScheduleService implements GetDayScheduleUseCase {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DEFAULT_COLOR = "#999999";

    private final ScheduleDayQueryPort scheduleDayQueryPort;

    @Override
    @Transactional(readOnly = true)
    public DayScheduleResponse execute(LocalDate date) {
        Long patientId = UserContext.get();
        List<DayScheduleProjection> projections = scheduleDayQueryPort.findByPatientAndDate(patientId, date);
        List<SlotView> slots = groupByCustomTime(projections);
        int doneCount = (int) slots.stream().filter(s -> "done".equals(s.state())).count();
        return new DayScheduleResponse(date, slots.size(), doneCount, slots);
    }

    private List<SlotView> groupByCustomTime(List<DayScheduleProjection> projections) {
        Map<LocalTime, List<DayScheduleProjection>> grouped = new LinkedHashMap<>();
        for (DayScheduleProjection projection : projections) {
            grouped.computeIfAbsent(projection.customTime(), key -> new ArrayList<>()).add(projection);
        }
        return grouped.entrySet().stream()
                .map(entry -> toSlotView(entry.getKey(), entry.getValue()))
                .toList();
    }

    private SlotView toSlotView(LocalTime customTime, List<DayScheduleProjection> group) {
        String time = customTime != null ? customTime.format(HH_MM) : "";
        List<String> items = group.stream().map(DayScheduleProjection::drugName).filter(n -> n != null).toList();
        List<Long> doseLogIds = group.stream().map(DayScheduleProjection::doseLogId).filter(id -> id != null).toList();
        List<String> pillColors = group.stream().map(this::colorOf).toList();
        return new SlotView(
                time,
                time,
                time,
                resolveState(group),
                items,
                doseLogIds.isEmpty() ? null : doseLogIds.get(0),
                doseLogIds,
                time,
                items.size(),
                pillColors
        );
    }

    private String resolveState(List<DayScheduleProjection> group) {
        boolean allTaken = group.stream().allMatch(p -> "TAKEN".equals(p.doseStatus()));
        return allTaken ? "done" : "wait";
    }

    private String colorOf(DayScheduleProjection projection) {
        return projection.pillColor() != null ? projection.pillColor() : DEFAULT_COLOR;
    }
}
