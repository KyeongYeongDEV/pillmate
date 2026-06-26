package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.port.SchedulingPort;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SoftDeletePrescriptionService implements SoftDeletePrescriptionUseCase {

    private final PrescriptionRepository prescriptionRepository;
    private final SchedulingPort schedulingPort;
    private final PatientAccessGuard patientAccessGuard;

    @Override
    @Transactional
    public void delete(Long prescriptionId) {
        Prescription prescription = findOwnPrescription(prescriptionId);
        prescription.softDelete();
        prescriptionRepository.save(prescription);
        schedulingPort.deactivateByPrescriptionId(prescriptionId);
    }

    private Prescription findOwnPrescription(Long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new PillmateException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        patientAccessGuard.requireAccess(UserContext.get(), prescription.getPatientId());
        return prescription;
    }
}
