package com.pillmate.prescription.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.application.port.OcrMatchLogPort;
import com.pillmate.prescription.domain.model.CandidateDecisionType;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.PrescribedDrugCandidate;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ResolveCandidateService")
class ResolveCandidateServiceTest {

    @InjectMocks
    private ResolveCandidateService resolveCandidateService;

    @Mock
    private PrescriptionRepository prescriptionRepository;

    @Mock
    private CareGroupGuard careGroupGuard;

    @Mock
    private DrugLookupPort drugLookupPort;

    @Mock
    private OcrMatchLogPort ocrMatchLogPort;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    private Prescription prescriptionWithCandidate(String optionsJson) {
        Prescription p = Prescription.create(2L, "prescriptions/uuid.jpg", LocalDate.now());
        PrescribedDrug drug = PrescribedDrug.builder()
                .drugId(null).nameRaw("타이레놀").doseAmount(BigDecimal.ONE)
                .doseUnit("정").frequency(3).durationDays(7)
                .confidence(new BigDecimal("0.6")).build();
        p.addDrug(drug);
        PrescribedDrugCandidate candidate = PrescribedDrugCandidate.create(
                0, CandidateDecisionType.CONFIRM, "ambiguous", optionsJson);
        p.attachCandidates(List.of(candidate));
        return p;
    }

    @Test
    @DisplayName("유효한 옵션 선택 시 candidate가 resolved되고 처방전이 저장된다")
    void resolve_whenOptionValid_savesAndUpdatesPrescription() {
        String optionsJson = "[{\"drugId\": 12320}]";
        Prescription prescription = prescriptionWithCandidate(optionsJson);
        given(prescriptionRepository.findById(1L)).willReturn(Optional.of(prescription));

        resolveCandidateService.resolve(1L, 0, 12320L, 1L);

        PrescribedDrugCandidate candidate = prescription.getCandidates().get(0);
        assertThat(candidate.getResolvedDrugId()).isEqualTo(12320L);
        verify(prescriptionRepository).save(prescription);
    }

    @Test
    @DisplayName("이미 resolved된 candidate에 재요청 시 409")
    void resolve_whenAlreadyResolved_throws409() {
        String optionsJson = "[{\"drugId\": 12320}]";
        Prescription prescription = prescriptionWithCandidate(optionsJson);
        given(prescriptionRepository.findById(1L)).willReturn(Optional.of(prescription));
        resolveCandidateService.resolve(1L, 0, 12320L, 1L);

        given(prescriptionRepository.findById(1L)).willReturn(Optional.of(prescription));
        assertThatThrownBy(() -> resolveCandidateService.resolve(1L, 0, 12320L, 1L))
                .isInstanceOf(PillmateException.class)
                .satisfies(e -> assertThat(((PillmateException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CANDIDATE_ALREADY_RESOLVED));
    }

    @Test
    @DisplayName("options_json에 없는 drugId 선택 시 400")
    void resolve_whenOptionNotInJson_throws400() {
        String optionsJson = "[{\"drugId\": 12320}]";
        Prescription prescription = prescriptionWithCandidate(optionsJson);
        given(prescriptionRepository.findById(1L)).willReturn(Optional.of(prescription));

        assertThatThrownBy(() -> resolveCandidateService.resolve(1L, 0, 99999L, 1L))
                .isInstanceOf(PillmateException.class)
                .satisfies(e -> assertThat(((PillmateException) e).getErrorCode())
                        .isEqualTo(ErrorCode.CANDIDATE_OPTION_INVALID));
    }

    @Test
    @DisplayName("resolve 성공 시 match log update를 best-effort로 호출한다")
    void resolve_whenOptionValid_callsMatchLogUpdate() {
        String optionsJson = "[{\"drugId\": 12320}]";
        Prescription prescription = prescriptionWithCandidate(optionsJson);
        given(prescriptionRepository.findById(1L)).willReturn(Optional.of(prescription));
        given(drugLookupPort.findById(12320L))
                .willReturn(Optional.of(new DrugLookupPort.DrugSummary(12320L, "A11AA11", "타이레놀정500mg", null)));

        resolveCandidateService.resolve(1L, 0, 12320L, 1L);

        verify(ocrMatchLogPort).updateUserCorrection("prescriptions/uuid.jpg", "타이레놀", "A11AA11");
    }

    @Test
    @DisplayName("drug 조회 실패 시 match log update를 호출하지 않는다 (best-effort)")
    void resolve_whenDrugNotFound_skipsMatchLogUpdate() {
        String optionsJson = "[{\"drugId\": 12320}]";
        Prescription prescription = prescriptionWithCandidate(optionsJson);
        given(prescriptionRepository.findById(1L)).willReturn(Optional.of(prescription));
        given(drugLookupPort.findById(12320L)).willReturn(Optional.empty());

        resolveCandidateService.resolve(1L, 0, 12320L, 1L);

        verify(ocrMatchLogPort, never()).updateUserCorrection(anyString(), anyString(), anyString());
    }
}
