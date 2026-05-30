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
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GetDayScheduleService implements GetDayScheduleUseCase {

    private static final Map<String, String> SLOT_ID = Map.of(
            "MORNING", "morning", "NOON", "noon", "EVENING", "evening", "BEDTIME", "bedtime");
    private static final Map<String, String> SLOT_TIME = Map.of(
            "MORNING", "08:00", "NOON", "12:30", "EVENING", "19:00", "BEDTIME", "22:00");
    private static final Map<String, String> SLOT_LABEL = Map.of(
            "MORNING", "아침", "NOON", "점심", "EVENING", "저녁", "BEDTIME", "취침");
    private static final String DEFAULT_COLOR = "#999999";

    private final ScheduleDayQueryPort scheduleDayQueryPort;

    @Override
    @Transactional(readOnly = true)
    public DayScheduleResponse execute(LocalDate date) {
        Long patientId = UserContext.get();
        List<DayScheduleProjection> projections = scheduleDayQueryPort.findByPatientAndDate(patientId, date);
        List<SlotView> slots = projections.stream().map(this::toSlotView).toList();
        int doneCount = (int) slots.stream().filter(s -> "done".equals(s.state())).count();
        return new DayScheduleResponse(date, slots.size(), doneCount, slots);
    }

    private SlotView toSlotView(DayScheduleProjection p) {
        String state = "TAKEN".equals(p.doseStatus()) ? "done" : "wait";
        String color = p.pillColor() != null ? p.pillColor() : DEFAULT_COLOR;
        List<String> items = p.drugName() != null ? List.of(p.drugName()) : List.of();
        return new SlotView(
                SLOT_ID.getOrDefault(p.timeOfDay(), p.timeOfDay().toLowerCase()),
                SLOT_TIME.getOrDefault(p.timeOfDay(), ""),
                SLOT_LABEL.getOrDefault(p.timeOfDay(), p.timeOfDay()),
                state,
                items,
                p.doseLogId(),
                items.size(),
                List.of(color)
        );
    }
}
