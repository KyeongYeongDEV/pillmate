package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.port.PrescriptionLookupPort;
import com.pillmate.prescription.application.port.PrescriptionLookupPort.PrescriptionOwner;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.CreatePrescriptionSchedulesCommand;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.CreatedSchedule;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.SlotSpec;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AddPrescriptionSlotUseCase {

    private static final int DEFAULT_DURATION_DAYS = 7;

    private final ScheduleRepository scheduleRepository;
    private final PrescriptionScheduleService prescriptionScheduleService;
    private final PrescriptionLookupPort prescriptionLookupPort;
    private final PatientAccessGuard patientAccessGuard;
    private final CareGroupGuard careGroupGuard;
    private final Clock clock;

    @Transactional
    public List<CreatedSchedule> addSlot(Long prescriptionId, TimeOfDay timeOfDay, LocalTime customTime) {
        PrescriptionOwner owner = requireOwner(prescriptionId);
        requireAccess(owner);
        List<Schedule> existing = scheduleRepository.findActiveByPrescriptionId(prescriptionId);
        LocalDate startDate = resolveStart(existing, owner);
        LocalDate endDate = resolveEnd(existing, owner, startDate);
        requireNotExpired(endDate);
        requireNoTimeConflict(existing, effectiveTime(timeOfDay, customTime));
        return prescriptionScheduleService.createForPrescription(
                buildCommand(prescriptionId, owner, existing, startDate, endDate, timeOfDay, customTime));
    }

    private PrescriptionOwner requireOwner(Long prescriptionId) {
        return prescriptionLookupPort.findOwner(prescriptionId)
                .orElseThrow(() -> new PillmateException(ErrorCode.PRESCRIPTION_NOT_FOUND));
    }

    // 슬롯 추가는 처방전 소유자 본인만 — 형제 유스케이스(remove/update/get)와 동일 가드. 누락 시 IDOR.
    private void requireAccess(PrescriptionOwner owner) {
        patientAccessGuard.requireAccess(UserContext.get(), owner.patientId());
        if (owner.careGroupId() != null) {
            careGroupGuard.requireAccessible(owner.careGroupId());
        }
    }

    private LocalDate resolveStart(List<Schedule> existing, PrescriptionOwner owner) {
        return existing.isEmpty() ? owner.prescribedAt() : existing.get(0).getStartDate();
    }

    private LocalDate resolveEnd(List<Schedule> existing, PrescriptionOwner owner, LocalDate start) {
        if (!existing.isEmpty()) return existing.get(0).getEndDate();
        int duration = owner.maxDurationDays() > 0 ? owner.maxDurationDays() : DEFAULT_DURATION_DAYS;
        return start.plusDays(duration - 1);
    }

    private void requireNotExpired(LocalDate endDate) {
        if (LocalDate.now(clock).isAfter(endDate)) {
            throw new PillmateException(ErrorCode.PRESCRIPTION_PERIOD_ENDED);
        }
    }

    private LocalTime effectiveTime(TimeOfDay timeOfDay, LocalTime customTime) {
        return customTime != null ? customTime : timeOfDay.defaultTime();
    }

    private void requireNoTimeConflict(List<Schedule> existing, LocalTime newTime) {
        boolean conflict = existing.stream().anyMatch(s -> newTime.equals(s.getCustomTime()));
        if (conflict) {
            throw new PillmateException(ErrorCode.SCHEDULE_CONFLICT);
        }
    }

    private CreatePrescriptionSchedulesCommand buildCommand(
            Long prescriptionId, PrescriptionOwner owner, List<Schedule> existing,
            LocalDate startDate, LocalDate endDate, TimeOfDay timeOfDay, LocalTime customTime) {
        Schedule sample = existing.isEmpty() ? null : existing.get(0);
        Long careGroupId = sample != null ? sample.getCareGroupId() : owner.careGroupId();
        Long patientId   = sample != null ? sample.getPatientId()   : owner.patientId();
        return new CreatePrescriptionSchedulesCommand(
                careGroupId, patientId, prescriptionId, UserContext.get(),
                List.of(new SlotSpec(timeOfDay.name(), customTime)),
                startDate, endDate);
    }
}
