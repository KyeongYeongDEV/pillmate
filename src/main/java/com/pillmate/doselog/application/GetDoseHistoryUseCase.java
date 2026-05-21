package com.pillmate.doselog.application;

import com.pillmate.doselog.application.dto.DoseLogResponse;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetDoseHistoryUseCase {

    private final DoseLogRepository doseLogRepository;

    @Transactional(readOnly = true)
    public List<DoseLogResponse> getHistory(Long patientId, Instant from, Instant to) {
        return doseLogRepository.findByPatientIdAndScheduledAtBetween(patientId, from, to)
                .stream()
                .map(DoseLogResponse::from)
                .toList();
    }
}
