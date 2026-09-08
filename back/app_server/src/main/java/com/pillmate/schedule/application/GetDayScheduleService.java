package com.pillmate.schedule.application;

import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.security.CareGroupGuard;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GetDayScheduleService implements GetDayScheduleUseCase {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M월 d일");
    private static final String DEFAULT_COLOR = "#999999";
    private static final int LABEL_DISPLAY_LIMIT = 5;
    private static final int LABEL_ELLIPSIS_HEAD = 3;
    private static final String[] CIRCLED_DIGITS = {
            "①", "②", "③", "④", "⑤", "⑥", "⑦", "⑧", "⑨", "⑩"
    };
    private static final Map<LocalTime, String> TIME_OF_DAY_LABELS = Map.of(
            TimeOfDay.MORNING.defaultTime(), "아침",
            TimeOfDay.NOON.defaultTime(), "점심",
            TimeOfDay.EVENING.defaultTime(), "저녁",
            TimeOfDay.BEDTIME.defaultTime(), "취침 전"
    );
    private static final String MEDICATION_DETAIL_MASK = "약 정보 비공개";

    private final ScheduleDayQueryPort scheduleDayQueryPort;
    private final CareGroupGuard careGroupGuard;
    private final MembershipRepository membershipRepository;

    @Override
    public DayScheduleResponse execute(LocalDate date) {
        return execute(date, null, null);
    }

    @Override
    public DayScheduleResponse execute(LocalDate date, Long patientId) {
        return execute(date, patientId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public DayScheduleResponse execute(LocalDate date, Long patientId, Long groupId) {
        Long viewerUserId = UserContext.get();
        Long resolvedPatientId = resolvePatientId(patientId);
        List<DayScheduleProjection> rows = scheduleDayQueryPort.findByPatientAndDate(resolvedPatientId, date);
        Map<Long, String> resolvedLabels = resolvePrescriptionLabels(rows);
        List<SlotView> slots = mergeToSlots(rows, resolvedLabels);
        slots = applyMasking(slots, rows, resolvedPatientId, viewerUserId, groupId);
        int doneCount = (int) slots.stream().filter(slot -> "done".equals(slot.state())).count();
        return new DayScheduleResponse(date, slots.size(), doneCount, slots);
    }

    private Long resolvePatientId(Long patientId) {
        Long targetPatientId = patientId != null ? patientId : UserContext.get();
        careGroupGuard.requirePatientAccessible(targetPatientId);
        return targetPatientId;
    }

    // L2(알약 정보) 마스킹 — 약봉투(처방전) 단위 판정. 본인 조회는 항상 공개.
    // 타인 조회는 슬롯의 prescriptionId 별로 공유 여부를 따져 슬롯 단위로 마스킹한다.
    private List<SlotView> applyMasking(List<SlotView> slots, List<DayScheduleProjection> rows,
                                         Long patientId, Long viewerUserId, Long groupId) {
        if (patientId.equals(viewerUserId)) {
            return slots;
        }
        Set<Long> maskedPrescriptionIds = resolveMaskedPrescriptionIds(rows, patientId, viewerUserId, groupId);
        return slots.stream()
                .map(slot -> shouldMaskSlot(slot, maskedPrescriptionIds) ? maskMedicationDetail(slot) : slot)
                .toList();
    }

    // 레거시 행(prescriptionId 없음)은 공유 개념이 없으므로 타인 조회 시 항상 마스킹 유지(fail-closed).
    private boolean shouldMaskSlot(SlotView slot, Set<Long> maskedPrescriptionIds) {
        if (slot.prescriptionId() == null) {
            return true;
        }
        return maskedPrescriptionIds.contains(slot.prescriptionId());
    }

    private Set<Long> resolveMaskedPrescriptionIds(List<DayScheduleProjection> rows,
                                                     Long patientId, Long viewerUserId, Long groupId) {
        Set<Long> masked = new HashSet<>();
        for (DayScheduleProjection row : rows) {
            if (row.prescriptionId() == null) {
                continue;
            }
            if (!canViewPrescriptionSlot(viewerUserId, patientId, row.prescriptionCareGroupId(),
                    row.sharedWithGroup(), groupId)) {
                masked.add(row.prescriptionId());
            }
        }
        return masked;
    }

    // groupId 없이 타인 조회하면 안전하게 마스킹(fail-closed). 요청 groupId 와 약봉투 소속 그룹이
    // 다르면 공유 켬이어도 차단(크로스그룹 차단). 둘 다 그 그룹 ACTIVE 멤버여야 최종 공개.
    private boolean canViewPrescriptionSlot(Long viewerUserId, Long patientId, Long prescriptionCareGroupId,
                                             Boolean sharedWithGroup, Long requestGroupId) {
        if (requestGroupId == null) {
            return false;
        }
        if (prescriptionCareGroupId == null || !Boolean.TRUE.equals(sharedWithGroup)) {
            return false;
        }
        if (!prescriptionCareGroupId.equals(requestGroupId)) {
            return false;
        }
        return isActiveMember(prescriptionCareGroupId, patientId) && isActiveMember(prescriptionCareGroupId, viewerUserId);
    }

    private boolean isActiveMember(Long careGroupId, Long userId) {
        return membershipRepository.existsByCareGroupIdAndUserId(careGroupId, userId);
    }

    private SlotView maskMedicationDetail(SlotView slot) {
        return new SlotView(
                slot.id(), slot.time(), slot.label(), slot.state(),
                List.of(), slot.doseLogId(), slot.doseLogIds(), slot.customTime(),
                slot.drugCount(), List.of(), slot.prescriptionId(), MEDICATION_DETAIL_MASK
        );
    }

    // 카드 표시용 처방전 이름 우선순위: ①사용자 label(non-blank) 그대로 ②없으면 'M월 D일 약봉투'
    // + 같은 prescribedAt 에 label 없는 약봉투가 여럿이면 prescriptionId 오름차순 ①②③ 번호로 구분(결정적 — 화면 간 일관).
    private Map<Long, String> resolvePrescriptionLabels(List<DayScheduleProjection> rows) {
        Map<Long, DayScheduleProjection> firstRowByPrescriptionId = new LinkedHashMap<>();
        for (DayScheduleProjection row : rows) {
            if (row.prescriptionId() != null) {
                firstRowByPrescriptionId.putIfAbsent(row.prescriptionId(), row);
            }
        }

        Map<Long, String> resolved = new LinkedHashMap<>();
        Map<LocalDate, List<Long>> blankLabelIdsByDate = new LinkedHashMap<>();
        firstRowByPrescriptionId.forEach((prescriptionId, row) -> {
            if (isNotBlank(row.label())) {
                resolved.put(prescriptionId, row.label());
            } else {
                blankLabelIdsByDate
                        .computeIfAbsent(row.prescribedAt(), k -> new ArrayList<>())
                        .add(prescriptionId);
            }
        });

        blankLabelIdsByDate.forEach((prescribedAt, ids) ->
                assignDefaultLabels(resolved, prescribedAt, ids));
        return resolved;
    }

    private void assignDefaultLabels(Map<Long, String> resolved, LocalDate prescribedAt, List<Long> ids) {
        List<Long> sorted = ids.stream().sorted().toList();
        String datePart = prescribedAt != null ? prescribedAt.format(MONTH_DAY) : "약봉투";
        boolean needsNumber = sorted.size() > 1;
        for (int i = 0; i < sorted.size(); i++) {
            String suffix = needsNumber ? " " + circledDigit(i + 1) : "";
            resolved.put(sorted.get(i), datePart + " 약봉투" + suffix);
        }
    }

    private String circledDigit(int n) {
        return n >= 1 && n <= CIRCLED_DIGITS.length ? CIRCLED_DIGITS[n - 1] : "(" + n + ")";
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    // 시간(customTime) 단위 그룹 — 같은 시각의 모든 처방전/레거시 행을 1슬롯으로 머지.
    private List<SlotView> mergeToSlots(List<DayScheduleProjection> rows, Map<Long, String> resolvedLabels) {
        Map<String, List<DayScheduleProjection>> grouped = new LinkedHashMap<>();
        for (DayScheduleProjection row : rows) {
            grouped.computeIfAbsent(formatTime(row.customTime()), k -> new ArrayList<>()).add(row);
        }
        return grouped.values().stream().map(group -> mergeGroup(group, resolvedLabels)).toList();
    }

    private SlotView mergeGroup(List<DayScheduleProjection> group, Map<Long, String> resolvedLabels) {
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
        String prescriptionName = joinPrescriptionLabels(
                group.stream().map(row -> rowLabel(row, resolvedLabels)).toList());

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

    private String rowLabel(DayScheduleProjection row, Map<Long, String> resolvedLabels) {
        if (row.prescriptionId() != null) {
            return resolvedLabels.getOrDefault(row.prescriptionId(), "약봉투");
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

    private List<String> resolveColors(List<String> colors) {
        if (colors == null) {
            return List.of();
        }
        return colors.stream().map(color -> color != null ? color : DEFAULT_COLOR).toList();
    }
}
