package com.pillmate.schedule.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.port.PeriodAdjustDoseLogsPort;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RemovePrescriptionSlotUseCase {

    private final ScheduleRepository scheduleRepository;
    private final CareGroupGuard careGroupGuard;
    private final PatientAccessGuard patientAccessGuard;
    private final PeriodAdjustDoseLogsPort periodAdjustDoseLogsPort;
    private final Clock clock;

    @Transactional
    public int removeSlot(Long prescriptionId, TimeOfDay timeOfDay) {
        List<Schedule> active = scheduleRepository.findActiveByPrescriptionId(prescriptionId);
        requireExisting(active);
        Schedule sample = active.get(0);
        patientAccessGuard.requireAccess(UserContext.get(), sample.getPatientId());
        // 솔로(그룹 없는) 스케줄은 본인 소유 확인만 — PrescriptionScheduleService null 분기 선례
        if (sample.getCareGroupId() != null) {
            careGroupGuard.requireAccessible(sample.getCareGroupId());
        }
        List<Schedule> atSlot = active.stream().filter(s -> s.getTimeOfDay() == timeOfDay).toList();
        atSlot.forEach(this::deactivate);
        return atSlot.size();
    }

    private void requireExisting(List<Schedule> active) {
        if (active.isEmpty()) {
            throw new PillmateException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
    }

    private void deactivate(Schedule schedule) {
        schedule.deactivate();
        scheduleRepository.save(schedule);
        // 삭제한 슬롯에 리마인더 오발송 차단
        periodAdjustDoseLogsPort.skipPendingFrom(schedule.getId(), Instant.now(clock));
    }
}
