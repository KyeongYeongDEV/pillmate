package com.pillmate.schedule.application;

import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.schedule.application.dto.ScheduleResponse;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListSchedulesUseCase {

    private final ScheduleRepository scheduleRepository;
    private final CareGroupGuard careGroupGuard;

    @Transactional(readOnly = true)
    public List<ScheduleResponse> list(Long patientId, Boolean active) {
        careGroupGuard.requirePatientAccessible(patientId);
        boolean activeFilter = active == null || active;
        return scheduleRepository.findByPatientIdAndActiveOrderByTimeOfDayAsc(patientId, activeFilter)
                .stream()
                .map(ScheduleResponse::from)
                .toList();
    }
}
