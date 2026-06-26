package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.port.SchedulingPort;
import com.pillmate.prescription.domain.model.OcrStatus;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("SoftDeletePrescriptionService — 처방전 소프트 삭제")
class SoftDeletePrescriptionServiceTest {

    private static final Long PATIENT_ID = 7L;
    private static final Long OTHER_ID = 99L;
    private static final Long PRESCRIPTION_ID = 1L;

    @Mock PrescriptionRepository prescriptionRepository;
    @Mock SchedulingPort schedulingPort;

    private SoftDeletePrescriptionService sut;

    @BeforeEach
    void setUp() {
        UserContext.set(PATIENT_ID);
        sut = new SoftDeletePrescriptionService(
                prescriptionRepository, schedulingPort, new PatientAccessGuard());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("본인 처방전 소프트 삭제 → deletedAt 세팅 후 저장, 행 물리적으로 보존")
    void softDelete_ownPrescription_setsDeletedAtAndSaves() {
        Prescription prescription = ownPrescription();
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(prescription));

        sut.delete(PRESCRIPTION_ID);

        ArgumentCaptor<Prescription> captor = ArgumentCaptor.forClass(Prescription.class);
        verify(prescriptionRepository).save(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
        assertThat(captor.getValue().getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("타인 처방전 삭제 시도 → PATIENT_ACCESS_DENIED 예외")
    void softDelete_otherPatientPrescription_throwsAccessDenied() {
        Prescription other = prescriptionOf(OTHER_ID);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(other));

        assertThatThrownBy(() -> sut.delete(PRESCRIPTION_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);

        verify(prescriptionRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 처방전 → PRESCRIPTION_NOT_FOUND 예외")
    void softDelete_notFound_throwsPrescriptionNotFound() {
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.delete(PRESCRIPTION_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRESCRIPTION_NOT_FOUND);
    }

    @Test
    @DisplayName("소프트 삭제 시 연관 스케줄 비활성 요청 위임")
    void softDelete_deactivatesLinkedSchedules() {
        Prescription prescription = ownPrescription();
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(prescription));

        sut.delete(PRESCRIPTION_ID);

        verify(schedulingPort).deactivateByPrescriptionId(PRESCRIPTION_ID);
    }

    private Prescription ownPrescription() {
        return prescriptionOf(PATIENT_ID);
    }

    private Prescription prescriptionOf(Long patientId) {
        Prescription p = Prescription.create(patientId, "img.jpg", LocalDate.now());
        ReflectionTestUtils.setField(p, "id", PRESCRIPTION_ID);
        return p;
    }
}
