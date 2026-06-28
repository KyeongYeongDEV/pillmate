package com.pillmate.schedule.application;

import com.pillmate.common.prescription.PrescriptionLabel;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.dto.DayScheduleResponse;
import com.pillmate.schedule.application.dto.SlotView;
import com.pillmate.schedule.application.port.ScheduleDayQueryPort;
import com.pillmate.schedule.application.port.ScheduleDayQueryPort.DayScheduleProjection;
import com.pillmate.schedule.domain.model.TimeOfDay;
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
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class GetDayScheduleService implements GetDayScheduleUseCase {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final String DEFAULT_COLOR = "#999999";
    private static final int LABEL_DISPLAY_LIMIT = 5;
    private static final int LABEL_ELLIPSIS_HEAD = 3;
    private static final Map<LocalTime, String> TIME_OF_DAY_LABELS = Map.of(
            TimeOfDay.MORNING.defaultTime(), "아침",
            TimeOfDay.NOON.defaultTime(), "점심",
            TimeOfDay.EVENING.defaultTime(), "저녁",
            TimeOfDay.BEDTIME.defaultTime(), "취침 전"
    );

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

    // 시간(customTime) 단위 그룹 — 같은 시각의 모든 처방전/레거시 행을 1슬롯으로 머지.
    private List<SlotView> mergeToSlots(List<DayScheduleProjection> rows) {
        Map<String, List<DayScheduleProjection>> grouped = new LinkedHashMap<>();
        for (DayScheduleProjection row : rows) {
            grouped.computeIfAbsent(formatTime(row.customTime()), k -> new ArrayList<>()).add(row);
        }
        return grouped.values().stream().map(this::mergeGroup).toList();
    }

    private SlotView mergeGroup(List<DayScheduleProjection> group) {
        DayScheduleProjection first = group.get(0);
        String time = formatTime(first.customTime());
        List<Long> doseLogIds = group.stream()
                .map(DayScheduleProjection::doseLogId)
                .filter(Objects::nonNull)
                .toList();
        Long primaryDoseLogId = doseLogIds.isEmpty() ? null : doseLogIds.get(0);
        String state = group.stream().allMatch(r -> "TAKEN".equals(r.doseStatus())) ? "done" : "wait";
        List<String> items = group.stream().flatMap(r -> rowItems(r).stream()).toList();
        List<String> colors = group.stream().flatMap(r -> rowColors(r).stream()).toList();
        String prescriptionName = joinPrescriptionLabels(group.stream().map(this::rowLabel).toList());

        return new SlotView(
                time, time, resolveLabel(first.customTime()), state, items,
                primaryDoseLogId, doseLogIds, time, items.size(), colors,
                firstPrescriptionId(group), prescriptionName
        );
    }

    private List<String> rowItems(DayScheduleProjection row) {
        if (row.prescriptionId() != null) {
            return row.drugNames() != null ? row.drugNames() : List.of();
        }
        return row.singleDrugName() != null ? List.of(row.singleDrugName()) : List.of();
    }

    private List<String> rowColors(DayScheduleProjection row) {
        return row.prescriptionId() != null ? resolveColors(row.pillColors()) : List.of();
    }

    private String rowLabel(DayScheduleProjection row) {
        if (row.prescriptionId() != null) {
            List<String> names = row.drugNames() != null ? row.drugNames() : List.of();
            return PrescriptionLabel.of(row.prescribedAt(), leadDrugName(names), names.size());
        }
        return row.singleDrugName() != null ? row.singleDrugName() : "복약";
    }

    private String joinPrescriptionLabels(List<String> labels) {
        List<String> unique = labels.stream().distinct().toList();
        if (unique.size() <= LABEL_DISPLAY_LIMIT) {
            return String.join(", ", unique);
        }
        String head = String.join(", ", unique.subList(0, LABEL_ELLIPSIS_HEAD));
        return head + " 외 " + (unique.size() - LABEL_ELLIPSIS_HEAD) + "건";
    }

    private Long firstPrescriptionId(List<DayScheduleProjection> group) {
        return group.stream()
                .map(DayScheduleProjection::prescriptionId)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String formatTime(java.time.LocalTime customTime) {
        return customTime != null ? customTime.format(HH_MM) : "";
    }

    private String resolveLabel(LocalTime customTime) {
        if (customTime == null) return "";
        String label = TIME_OF_DAY_LABELS.get(customTime);
        return label != null ? label : customTime.format(HH_MM);
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
