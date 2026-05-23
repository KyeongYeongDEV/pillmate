package com.pillmate.prescription.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Prescription Aggregate — 처방전과 처방약 컬렉션")
class PrescriptionTest {

    private Prescription newPrescription() {
        return Prescription.create(1L, 2L, "prescriptions/uuid.jpg", LocalDate.now());
    }

    @Test
    @DisplayName("addDrug 호출 시 자식 컬렉션이 늘어나고 부모 양방향 관계가 설정된다")
    void addDrug_appendsDrugAndLinksParent() {
        Prescription prescription = newPrescription();
        PrescribedDrug drug = PrescribedDrug.builder()
                .drugId(101L)
                .nameRaw("타이레놀")
                .doseAmount(new BigDecimal("1.00"))
                .doseUnit("정")
                .frequency(3)
                .durationDays(7)
                .confidence(new BigDecimal("0.95"))
                .build();

        prescription.addDrug(drug);

        assertThat(prescription.getDrugs()).hasSize(1);
        assertThat(prescription.getDrugs().get(0).getPrescription()).isEqualTo(prescription);
    }

    @Test
    @DisplayName("모든 confidence 가 0.7 이상이면 markOcrDone() 후 DONE 상태")
    void markOcrDone_whenAllConfidenceHigh_setsStatusDone() {
        Prescription prescription = newPrescription();
        prescription.addDrug(drugWithConfidence(new BigDecimal("0.92")));
        prescription.addDrug(drugWithConfidence(new BigDecimal("0.85")));

        prescription.markOcrDone();

        assertThat(prescription.getOcrStatus()).isEqualTo(OcrStatus.DONE);
    }

    @Test
    @DisplayName("confidence 가 0.7 미만인 항목이 하나라도 있으면 markOcrDone() 호출이 MANUAL 로 강제된다")
    void markOcrDone_whenAnyLowConfidence_forcedToManual() {
        Prescription prescription = newPrescription();
        prescription.addDrug(drugWithConfidence(new BigDecimal("0.92")));
        prescription.addDrug(drugWithConfidence(new BigDecimal("0.55")));

        prescription.markOcrDone();

        assertThat(prescription.getOcrStatus()).isEqualTo(OcrStatus.MANUAL);
    }

    @Test
    @DisplayName("markOcrFailed() 는 FAILED 상태로 전이한다")
    void markOcrFailed_setsStatusFailed() {
        Prescription prescription = newPrescription();

        prescription.markOcrFailed();

        assertThat(prescription.getOcrStatus()).isEqualTo(OcrStatus.FAILED);
    }

    @Test
    @DisplayName("markManualReview() 는 MANUAL 상태로 전이한다")
    void markManualReview_setsStatusManual() {
        Prescription prescription = newPrescription();

        prescription.markManualReview();

        assertThat(prescription.getOcrStatus()).isEqualTo(OcrStatus.MANUAL);
    }

    private PrescribedDrug drugWithConfidence(BigDecimal confidence) {
        return PrescribedDrug.builder()
                .drugId(101L)
                .nameRaw("약")
                .doseAmount(new BigDecimal("1.00"))
                .doseUnit("정")
                .frequency(3)
                .durationDays(7)
                .confidence(confidence)
                .build();
    }
}
