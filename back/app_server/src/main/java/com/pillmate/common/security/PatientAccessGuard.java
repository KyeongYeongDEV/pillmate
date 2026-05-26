package com.pillmate.common.security;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class PatientAccessGuard {

    public boolean canAccess(Long viewerId, Long patientId) {
        if (viewerId == null || patientId == null) return false;
        return Objects.equals(viewerId, patientId);
    }

    public void requireAccess(Long viewerId, Long patientId) {
        if (!canAccess(viewerId, patientId)) {
            throw new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED);
        }
    }
}
