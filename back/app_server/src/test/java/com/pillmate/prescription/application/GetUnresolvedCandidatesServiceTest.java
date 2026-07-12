package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.dto.UnresolvedCandidateDto;
import com.pillmate.prescription.domain.model.CandidateDecisionType;
import com.pillmate.prescription.domain.model.PrescribedDrugCandidate;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetUnresolvedCandidatesService")
class GetUnresolvedCandidatesServiceTest {

    @InjectMocks
    private GetUnresolvedCandidatesService getUnresolvedCandidatesService;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Spy
    private PatientAccessGuard patientAccessGuard = new PatientAccessGuard();

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    @DisplayName("미해결 candidate가 있으면 목록으로 반환된다")
    void getUnresolved_returnsUnresolvedCandidates() {
        UserContext.set(2L);
        Prescription prescription = Prescription.create(2L, "prescriptions/uuid.jpg", LocalDate.now());
        PrescribedDrugCandidate candidate = PrescribedDrugCandidate.create(
                0, CandidateDecisionType.CONFIRM, "ambiguous", "[{\"drugId\": 12320}]");
        prescription.attachCandidates(List.of(candidate));
        given(prescriptionRepository.findById(1L)).willReturn(Optional.of(prescription));

        List<UnresolvedCandidateDto> result = getUnresolvedCandidatesService.getUnresolved(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).decisionType()).isEqualTo(CandidateDecisionType.CONFIRM);
        assertThat(result.get(0).reason()).isEqualTo("ambiguous");
    }

    @Test
    @DisplayName("IDOR: 타인의 처방전 candidate 조회 시 403 PATIENT_ACCESS_DENIED")
    void getUnresolved_whenOtherPatientPrescription_throws403() {
        UserContext.set(99L);
        Prescription prescription = Prescription.create(2L, "prescriptions/uuid.jpg", LocalDate.now());
        given(prescriptionRepository.findById(1L)).willReturn(Optional.of(prescription));

        assertThatThrownBy(() -> getUnresolvedCandidatesService.getUnresolved(1L))
                .isInstanceOf(PillmateException.class)
                .satisfies(e -> assertThat(((PillmateException) e).getErrorCode())
                        .isEqualTo(ErrorCode.PATIENT_ACCESS_DENIED));
    }
}
