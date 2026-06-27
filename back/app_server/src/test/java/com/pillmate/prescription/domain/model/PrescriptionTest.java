package com.pillmate.prescription.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Prescription Aggregate — 처방전과 처방약 컬렉션")
class PrescriptionTest {

    private Prescription newPrescription() {
        return Prescription.create(2L, "prescriptions/uuid.jpg", LocalDate.now());
    }

    @Test
    @DisplayName("create with label/memo → 필드 영속화")
    void create_withLabelAndMemo_persistsFields() {
        Prescription p = Prescription.create(1L, "img.jpg", LocalDate.now(), "내과 처방", "감기 처방전");
        assertThat(p.getLabel()).isEqualTo("내과 처방");
        assertThat(p.getMemo()).isEqualTo("감기 처방전");
    }

    @Test
    @DisplayName("create without label/memo → null")
    void create_withoutLabelMemo_fieldsAreNull() {
        Prescription p = Prescription.create(1L, "img.jpg", LocalDate.now());
        assertThat(p.getLabel()).isNull();
        assertThat(p.getMemo()).isNull();
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
    @DisplayName("매칭 안 된 약(drugId null)이 하나라도 있으면 markOcrDone() 이 MANUAL 로 강제된다")
    void markOcrDone_whenAnyDrugUnmatched_forcedToManual() {
        Prescription prescription = newPrescription();
        prescription.addDrug(drugWithConfidence(new BigDecimal("0.95")));
        prescription.addDrug(unmatchedDrug(new BigDecimal("0.95")));

        prescription.markOcrDone();

        assertThat(prescription.getOcrStatus()).isEqualTo(OcrStatus.MANUAL);
    }

    @Test
    @DisplayName("모든 약이 매칭되고 confidence ≥ 0.7 이면 markOcrDone() 후 DONE 상태")
    void markOcrDone_whenAllMatchedAndHighConfidence_setsDone() {
        Prescription prescription = newPrescription();
        prescription.addDrug(drugWithConfidence(new BigDecimal("0.92")));
        prescription.addDrug(drugWithConfidence(new BigDecimal("0.85")));

        prescription.markOcrDone();

        assertThat(prescription.getOcrStatus()).isEqualTo(OcrStatus.DONE);
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

    @Test
    @DisplayName("softDelete() 호출 전에는 isDeleted() = false")
    void isDeleted_beforeSoftDelete_returnsFalse() {
        Prescription p = newPrescription();

        assertThat(p.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("softDelete() 호출 후 isDeleted() = true, deletedAt != null")
    void softDelete_setsDeletedAtAndIsDeletedTrue() {
        Prescription p = newPrescription();

        p.softDelete();

        assertThat(p.isDeleted()).isTrue();
        assertThat(p.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("create_withSymptom — symptom 필드 저장")
    void create_withSymptom_setsField() {
        Prescription p = Prescription.create(1L, "img.jpg", LocalDate.now(), "감기", "식후 30분", "상기도 감염");
        assertThat(p.getSymptom()).isEqualTo("상기도 감염");
    }

    @Test
    @DisplayName("create_withoutSymptom — symptom null")
    void create_withoutSymptom_isNull() {
        Prescription p = Prescription.create(1L, "img.jpg", LocalDate.now(), "감기", "식후 30분");
        assertThat(p.getSymptom()).isNull();
    }

    @Test
    @DisplayName("updateMemo_withSymptom — 모든 메타 필드 갱신")
    void updateMemo_withSymptom_updatesAllFields() {
        Prescription p = newPrescription();
        p.updateMemo("새 라벨", "새 메모", "고혈압");
        assertThat(p.getLabel()).isEqualTo("새 라벨");
        assertThat(p.getMemo()).isEqualTo("새 메모");
        assertThat(p.getSymptom()).isEqualTo("고혈압");
    }

    @Test
    @DisplayName("updateMemo_withNullSymptom — symptom null 로 초기화")
    void updateMemo_withNullSymptom_clearsSymptom() {
        Prescription p = Prescription.create(1L, null, LocalDate.now(), "라벨", "메모", "당뇨");
        p.updateMemo("라벨2", "메모2", null);
        assertThat(p.getSymptom()).isNull();
    }

    private PrescribedDrug unmatchedDrug(BigDecimal confidence) {
        return PrescribedDrug.builder()
                .drugId(null)
                .nameRaw("동광나자티딘캡슐150mg")
                .doseAmount(new BigDecimal("150.00"))
                .doseUnit("mg")
                .frequency(2)
                .durationDays(7)
                .confidence(confidence)
                .build();
    }
}
