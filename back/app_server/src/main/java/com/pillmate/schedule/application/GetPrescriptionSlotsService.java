package com.pillmate.schedule.application;

import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.dto.SlotEditView;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetPrescriptionSlotsService implements GetPrescriptionSlotsUseCase {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final ScheduleRepository scheduleRepository;
    private final PatientAccessGuard patientAccessGuard;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<SlotEditView> execute(Long prescriptionId) {
        List<Schedule> schedules = scheduleRepository.findActiveByPrescriptionId(prescriptionId);
        if (schedules.isEmpty()) {
            return List.of();
        }
        patientAccessGuard.requireAccess(UserContext.get(), schedules.get(0).getPatientId());
        LocalDate today = LocalDate.now(clock.withZone(KST));
        return schedules.stream()
                .map(s -> toSlotEditView(s, today))
                .toList();
    }

    private SlotEditView toSlotEditView(Schedule s, LocalDate today) {
        return new SlotEditView(
                s.getId(),
                s.getTimeOfDay(),
                formatTime(s.getCustomTime()),
                s.getEndDate(),
                isEditable(s.getEndDate(), today)
        );
    }

    private String formatTime(LocalTime customTime) {
        return customTime != null ? customTime.format(HH_MM) : "";
    }

    private boolean isEditable(LocalDate endDate, LocalDate today) {
        return endDate == null || !today.isAfter(endDate);
    }
}
