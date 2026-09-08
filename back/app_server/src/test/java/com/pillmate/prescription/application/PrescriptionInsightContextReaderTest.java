package com.pillmate.prescription.application;

import com.pillmate.prescription.application.PrescriptionInsightContextReader.RecommendationContext;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.application.port.DrugLookupPort.DrugSummary;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PrescriptionInsightContextReader — 인사이트 생성용 컨텍스트 로드 (미매칭 약봉투 차단)")
class PrescriptionInsightContextReaderTest {

    private static final Long PRESCRIPTION_ID = 42L;
    private static final Long PATIENT_ID = 7L;

    @Mock PrescriptionRepository prescriptionRepository;
    @Mock DrugLookupPort drugLookupPort;

    private PrescriptionInsightContextReader sut;

    @BeforeEach
    void setUp() {
        sut = new PrescriptionInsightContextReader(prescriptionRepository, drugLookupPort);
    }

    @Test
    @DisplayName("모든 약이 매칭된 처방전 — 컨텍스트 반환")
    void load_allDrugsMatched_returnsContext() {
        Prescription prescription = prescription();
        prescription.addDrug(matchedDrug(101L, "타이레놀정"));
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(prescription));
        given(drugLookupPort.findByIds(anyCollection()))
                .willReturn(Map.of(101L, new DrugSummary(101L, "KD-001", "타이레놀정500밀리그램", null)));

        Optional<RecommendationContext> context = sut.load(PRESCRIPTION_ID);

        assertThat(context).isPresent();
        assertThat(context.get().patientId()).isEqualTo(PATIENT_ID);
        assertThat(context.get().drugs()).hasSize(1);
    }

    @Test
    @DisplayName("매칭 안 된 약(직접입력)이 섞인 처방전 — 빈 Optional, 식약처 조회도 안 함")
    void load_anyDrugUnmatched_returnsEmptyAndSkipsDrugLookup() {
        Prescription prescription = prescription();
        prescription.addDrug(matchedDrug(101L, "타이레놀정"));
        prescription.addDrug(unmatchedDrug("동광나자티딘캡슐"));
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(prescription));

        Optional<RecommendationContext> context = sut.load(PRESCRIPTION_ID);

        assertThat(context).isEmpty();
        verify(drugLookupPort, never()).findByIds(anyCollection());
    }

    @Test
    @DisplayName("존재하지 않는 처방전 — 빈 Optional")
    void load_prescriptionNotFound_returnsEmpty() {
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.empty());

        Optional<RecommendationContext> context = sut.load(PRESCRIPTION_ID);

        assertThat(context).isEmpty();
        verify(drugLookupPort, never()).findByIds(anyCollection());
    }

    private Prescription prescription() {
        Prescription p = Prescription.create(PATIENT_ID, null, LocalDate.of(2026, 6, 1));
        ReflectionTestUtils.setField(p, "id", PRESCRIPTION_ID);
        return p;
    }

    private PrescribedDrug matchedDrug(Long drugId, String nameRaw) {
        return PrescribedDrug.builder()
                .drugId(drugId).nameRaw(nameRaw)
                .doseAmount(new BigDecimal("1.00")).doseUnit("정")
                .frequency(3).durationDays(7).confidence(new BigDecimal("0.95"))
                .build();
    }

    private PrescribedDrug unmatchedDrug(String nameRaw) {
        return PrescribedDrug.builder()
                .nameRaw(nameRaw)
                .doseAmount(new BigDecimal("1.00")).doseUnit("정")
                .frequency(3).durationDays(7).confidence(new BigDecimal("0.95"))
                .build();
    }
}
