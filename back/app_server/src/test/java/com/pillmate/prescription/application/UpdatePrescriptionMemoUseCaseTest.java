package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("UpdatePrescriptionMemoUseCase — 메모/라벨 수정")
class UpdatePrescriptionMemoUseCaseTest {

    private static final Long OWNER_ID      = 7L;
    private static final Long OTHER_ID      = 99L;
    private static final Long PRESCRIPTION_ID = 1L;

    @Mock PrescriptionRepository prescriptionRepository;

    private UpdatePrescriptionMemoUseCase sut;

    @BeforeEach
    void setUp() {
        sut = new UpdatePrescriptionMemoUseCase(prescriptionRepository, new PatientAccessGuard());
    }

    @AfterEach void tearDown() { UserContext.clear(); }

    @Test
    @DisplayName("본인 처방전 — label/memo 영속화")
    void update_ownPrescription_persistsMemo() {
        UserContext.set(OWNER_ID);
        Prescription p = prescription(OWNER_ID);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));

        sut.update(PRESCRIPTION_ID, "아침약", "식후 30분");

        assertThat(p.getLabel()).isEqualTo("아침약");
        assertThat(p.getMemo()).isEqualTo("식후 30분");
    }

    @Test
    @DisplayName("타인 처방전 — PATIENT_ACCESS_DENIED (403)")
    void update_otherPatient_throwsAccessDenied() {
        UserContext.set(OTHER_ID);
        Prescription p = prescription(OWNER_ID);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> sut.update(PRESCRIPTION_ID, "라벨", "메모"))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);
    }

    @Test
    @DisplayName("존재하지 않는 처방전 — PRESCRIPTION_NOT_FOUND")
    void update_notFound_throws() {
        UserContext.set(OWNER_ID);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.update(PRESCRIPTION_ID, "라벨", "메모"))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRESCRIPTION_NOT_FOUND);
    }

    private Prescription prescription(Long patientId) {
        Prescription p = Prescription.create(patientId, null, LocalDate.of(2026, 6, 1));
        ReflectionTestUtils.setField(p, "id", PRESCRIPTION_ID);
        return p;
    }
}
