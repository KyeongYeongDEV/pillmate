package com.pillmate.schedule.application;

import com.pillmate.common.prescription.PrescriptionLabel;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.dto.DayScheduleResponse;
import com.pillmate.schedule.application.dto.SlotView;
import com.pillmate.schedule.application.port.ScheduleDayQueryPort;
import com.pillmate.schedule.application.port.ScheduleDayQueryPort.DayScheduleProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
        List<DayScheduleProjection> rows = scheduleDayQueryPort.findByPatientAndDate(patientId, date);
        List<SlotView> slots = mergeToSlots(rows);
        int doneCount = (int) slots.stream().filter(slot -> "done".equals(slot.state())).count();
        return new DayScheduleResponse(date, slots.size(), doneCount, slots);
    }

    // 동일 (customTime, prescriptionId) 그룹을 1개 SlotView로 병합 — slotId 중복 방지
    private List<SlotView> mergeToSlots(List<DayScheduleProjection> rows) {
        Map<String, List<DayScheduleProjection>> grouped = new LinkedHashMap<>();
        for (DayScheduleProjection row : rows) {
            String key = slotId(formatTime(row.customTime()), row.prescriptionId());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(row);
        }
        return grouped.values().stream().map(this::mergeGroup).toList();
    }

    private SlotView mergeGroup(List<DayScheduleProjection> group) {
        DayScheduleProjection first = group.get(0);
        String time = formatTime(first.customTime());
        List<String> items = first.drugNames() != null ? first.drugNames() : List.of();
        String prescriptionName = PrescriptionLabel.of(first.prescribedAt(), leadDrugName(items), items.size());
        List<Long> doseLogIds = group.stream()
                .map(DayScheduleProjection::doseLogId)
                .filter(Objects::nonNull)
                .toList();
        Long primaryDoseLogId = doseLogIds.isEmpty() ? null : doseLogIds.get(0);
        String state = group.stream().allMatch(r -> "TAKEN".equals(r.doseStatus())) ? "done" : "wait";
        return new SlotView(
                slotId(time, first.prescriptionId()),
                time,
                time,
                state,
                items,
                primaryDoseLogId,
                doseLogIds,
                time,
                items.size(),
                resolveColors(first.pillColors()),
                first.prescriptionId(),
                prescriptionName
        );
    }

    private String formatTime(java.time.LocalTime customTime) {
        return customTime != null ? customTime.format(HH_MM) : "";
    }

    private String slotId(String time, Long prescriptionId) {
        return time + "@" + prescriptionId;
    }

    private String leadDrugName(List<String> items) {
        return items.isEmpty() ? null : items.get(0);
    }

    private List<String> resolveColors(List<String> colors) {
        if (colors == null) {
            return List.of();
        }
        return colors.stream().map(color -> color != null ? color : DEFAULT_COLOR).toList();
    }
}
