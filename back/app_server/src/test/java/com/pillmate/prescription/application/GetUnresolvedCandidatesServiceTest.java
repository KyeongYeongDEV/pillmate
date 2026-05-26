package com.pillmate.prescription.application;

import com.pillmate.prescription.application.dto.UnresolvedCandidateDto;
import com.pillmate.prescription.domain.model.CandidateDecisionType;
import com.pillmate.prescription.domain.model.PrescribedDrugCandidate;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetUnresolvedCandidatesService")
class GetUnresolvedCandidatesServiceTest {

    @InjectMocks
    private GetUnresolvedCandidatesService getUnresolvedCandidatesService;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Test
    @DisplayName("미해결 candidate가 있으면 목록으로 반환된다")
    void getUnresolved_returnsUnresolvedCandidates() {
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
}
