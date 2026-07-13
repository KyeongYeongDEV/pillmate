package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.prescription.application.port.SchedulingPort;
import com.pillmate.schedule.application.port.PeriodAdjustDoseLogsPort;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import com.pillmate.schedule.domain.service.ScheduleConflictChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.pillmate.schedule.domain.model.TimeOfDay.EVENING;
import static com.pillmate.schedule.domain.model.TimeOfDay.MORNING;
import static com.pillmate.schedule.domain.model.TimeOfDay.NOON;

@Service
@RequiredArgsConstructor
public class PrescriptionScheduleService implements PrescriptionSchedulePort, SchedulingPort {

    private static final List<TimeOfDay> DEFAULT_SLOTS = List.of(MORNING, NOON, EVENING);

    private final ScheduleRepository scheduleRepository;
    private final ScheduleConflictChecker conflictChecker;
    private final CareGroupGuard careGroupGuard;
    private final PatientAccessGuard patientAccessGuard;
    private final PeriodAdjustDoseLogsPort periodAdjustDoseLogsPort;
    private final Clock clock;

    @Override
    @Transactional
    public List<CreatedSchedule> createForPrescription(CreatePrescriptionSchedulesCommand command) {
        patientAccessGuard.requireAccess(command.requesterId(), command.patientId());
        requireValidPeriod(command.startDate(), command.endDate());
        if (command.careGroupId() != null) {
            careGroupGuard.requireAccessible(command.careGroupId());
        }

        List<Schedule> existing = new ArrayList<>(
                scheduleRepository.findActiveByPrescriptionId(command.prescriptionId()));
        List<CreatedSchedule> created = new ArrayList<>();
        for (ResolvedSlot slot : resolveSlots(command.slots())) {
            Optional<Schedule> saved = createIfNoConflict(command, slot, existing);
            if (saved.isPresent()) {
                existing.add(saved.get());
                created.add(toCreatedSchedule(saved.get()));
            }
        }
        return created;
    }

    private List<ResolvedSlot> resolveSlots(List<SlotSpec> slots) {
        if (slots == null || slots.isEmpty()) {
            return DEFAULT_SLOTS.stream()
                    .map(timeOfDay -> new ResolvedSlot(timeOfDay, timeOfDay.defaultTime()))
                    .toList();
        }
        return slots.stream()
                .map(this::toResolvedSlot)
                .toList();
    }

    private ResolvedSlot toResolvedSlot(SlotSpec slot) {
        TimeOfDay timeOfDay = parseTimeOfDay(slot.timeOfDay());
        LocalTime customTime = slot.customTime() != null ? slot.customTime() : timeOfDay.defaultTime();
        return new ResolvedSlot(timeOfDay, customTime);
    }

    private TimeOfDay parseTimeOfDay(String raw) {
        try {
            return TimeOfDay.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new PillmateException(ErrorCode.SCHEDULE_INVALID_TIME_OF_DAY);
        }
    }

    private void requireValidPeriod(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new PillmateException(ErrorCode.SCHEDULE_INVALID_PERIOD);
        }
    }

    private Optional<Schedule> createIfNoConflict(
            CreatePrescriptionSchedulesCommand command, ResolvedSlot slot, List<Schedule> existing) {
        if (conflictChecker.hasPrescriptionSlotConflict(
                command.prescriptionId(), slot.customTime(), command.startDate(), command.endDate(), existing)) {
            return Optional.empty();
        }
        return Optional.of(scheduleRepository.save(buildSchedule(command, slot)));
    }

    private Schedule buildSchedule(CreatePrescriptionSchedulesCommand command, ResolvedSlot slot) {
        return Schedule.forPrescription(command.careGroupId(), command.patientId(), command.prescriptionId(),
                slot.timeOfDay(), slot.customTime(), command.startDate(), command.endDate(), command.requesterId());
    }

    private CreatedSchedule toCreatedSchedule(Schedule schedule) {
        return new CreatedSchedule(schedule.getId(), schedule.getTimeOfDay().name(),
                schedule.getCustomTime(), schedule.getStartDate(), schedule.getEndDate());
    }

    private record ResolvedSlot(TimeOfDay timeOfDay, LocalTime customTime) {}

    @Override
    @Transactional
    public List<SchedulingPort.ScheduledSlot> createForPrescription(SchedulingPort.CreateScheduleCommand command) {
        List<CreatedSchedule> schedules = createForPrescription(toInternalCommand(command));
        return schedules.stream().map(this::toScheduledSlot).toList();
    }

    private CreatePrescriptionSchedulesCommand toInternalCommand(SchedulingPort.CreateScheduleCommand cmd) {
        List<SlotSpec> slots = cmd.slots() == null ? null :
                cmd.slots().stream()
                        .map(s -> new SlotSpec(s.timeOfDay(), s.customTime()))
                        .toList();
        return new CreatePrescriptionSchedulesCommand(
                cmd.careGroupId(), cmd.patientId(), cmd.prescriptionId(),
                cmd.requesterId(), slots, cmd.startDate(), cmd.endDate());
    }

    private SchedulingPort.ScheduledSlot toScheduledSlot(CreatedSchedule s) {
        return new SchedulingPort.ScheduledSlot(
                s.scheduleId(), s.timeOfDay(), s.customTime(), s.startDate(), s.endDate());
    }

    @Override
    @Transactional
    public void deactivateByPrescriptionId(Long prescriptionId) {
        Instant now = Instant.now(clock);
        scheduleRepository.findActiveByPrescriptionId(prescriptionId)
                .forEach(schedule -> {
                    schedule.deactivate();
                    scheduleRepository.save(schedule);
                    // 삭제한 처방에 리마인더 오발송 차단
                    periodAdjustDoseLogsPort.skipPendingFrom(schedule.getId(), now);
                });
    }
}
