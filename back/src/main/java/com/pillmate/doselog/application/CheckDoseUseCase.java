package com.pillmate.doselog.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.doselog.application.dto.CheckDoseRequest;
import com.pillmate.doselog.application.dto.DoseLogResponse;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.common.security.CareGroupGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CheckDoseUseCase {

    private final DoseLogRepository doseLogRepository;
    private final CareGroupGuard careGroupGuard;

    @Transactional
    public DoseLogResponse check(CheckDoseRequest req, Long checkedBy) {
        DoseLog log = doseLogRepository.findById(req.doseLogId())
                .orElseThrow(() -> new PillmateException(ErrorCode.INVALID_REQUEST));
        careGroupGuard.requirePatientAccessible(log.getPatientId());

        if ("TAKE".equalsIgnoreCase(req.action())) {
            log.take(checkedBy);
        } else if ("SKIP".equalsIgnoreCase(req.action())) {
            log.skip(checkedBy, req.skipReason());
        } else {
            throw new PillmateException(ErrorCode.INVALID_REQUEST);
        }

        return DoseLogResponse.from(doseLogRepository.save(log));
    }
}
