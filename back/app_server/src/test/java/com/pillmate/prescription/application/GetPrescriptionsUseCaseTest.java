package com.pillmate.prescription.application;

import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.dto.PrescriptionSummary;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetPrescriptionsUseCase — 처방전 목록")
class GetPrescriptionsUseCaseTest {

    private static final Long PATIENT_ID = 7L;

    @InjectMocks GetPrescriptionsUseCase sut;
    @Mock PrescriptionRepository prescriptionRepository;

    @BeforeEach void setUp() { UserContext.set(PATIENT_ID); }
    @AfterEach void tearDown() { UserContext.clear(); }

    @Test
    @DisplayName("본인(UserContext) 처방전만 조회 위임")
    void list_queriesOnlyOwnPrescriptions() {
        given(prescriptionRepository.findAllByPatientId(PATIENT_ID)).willReturn(List.of());

        sut.list();

        verify(prescriptionRepository).findAllByPatientId(PATIENT_ID);
    }

    @Test
    @DisplayName("처방일(prescribedAt) 최신순 정렬")
    void list_sortedByPrescribedAtDesc() {
        Prescription older = prescription(LocalDate.of(2026, 5, 1), "타이레놀");
        Prescription newer = prescription(LocalDate.of(2026, 6, 10), "아스피린");
        given(prescriptionRepository.findAllByPatientId(PATIENT_ID))
                .willReturn(List.of(older, newer));

        List<PrescriptionSummary> result = sut.list();

        assertThat(result).extracting(PrescriptionSummary::prescribedAt)
                .containsExactly(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 5, 1));
    }

    @Test
    @DisplayName("요약 항목 — drugCount + 앞 3개 약품명 요약")
    void list_summarizesDrugCountAndNames() {
        Prescription p = prescription(LocalDate.of(2026, 6, 1), "타이레놀", "아스피린", "이부프로펜", "오메프라졸");
        given(prescriptionRepository.findAllByPatientId(PATIENT_ID)).willReturn(List.of(p));

        PrescriptionSummary summary = sut.list().get(0);

        assertThat(summary.drugCount()).isEqualTo(4);
        assertThat(summary.drugNames()).isEqualTo("타이레놀, 아스피린, 이부프로펜");
    }

    private Prescription prescription(LocalDate prescribedAt, String... drugNames) {
        Prescription p = Prescription.create(PATIENT_ID, "prescriptions/uuid.jpg", prescribedAt);
        for (String name : drugNames) {
            p.addDrug(PrescribedDrug.builder()
                    .nameRaw(name)
                    .doseAmount(new BigDecimal("1.00"))
                    .doseUnit("정")
                    .frequency(3)
                    .durationDays(7)
                    .confidence(new BigDecimal("0.95"))
                    .build());
        }
        return p;
    }
}
