package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.dto.PrescriptionDetailResponse;
import com.pillmate.prescription.application.dto.NutrientNote;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.application.port.DrugLookupPort.DrugSummary;
import com.pillmate.prescription.application.port.FileStoragePort;
import com.pillmate.prescription.application.port.NutrientDepletionPort;
import com.pillmate.prescription.application.port.PrescriptionPeriodPort;
import com.pillmate.prescription.application.port.PrescriptionPeriodPort.PeriodStats;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.model.PrescriptionInsight;
import com.pillmate.prescription.domain.model.PrescriptionInsightSeverity;
import com.pillmate.prescription.domain.model.PrescriptionInsightType;
import com.pillmate.prescription.domain.model.PrescriptionStatus;
import com.pillmate.prescription.domain.repository.PrescriptionInsightRepository;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetPrescriptionDetailUseCase — 처방전 상세 (본인 격리)")
class GetPrescriptionDetailUseCaseTest {

    private static final Long OWNER_ID = 7L;
    private static final Long OTHER_ID = 99L;
    private static final Long PRESCRIPTION_ID = 1L;

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneId.of("UTC"));

    @Mock PrescriptionRepository prescriptionRepository;
    @Mock DrugLookupPort drugLookupPort;
    @Mock FileStoragePort fileStoragePort;
    @Mock PrescriptionPeriodPort prescriptionPeriodPort;
    @Mock NutrientDepletionPort nutrientDepletionPort;
    @Mock PrescriptionInsightRepository prescriptionInsightRepository;

    private GetPrescriptionDetailUseCase sut;

    @BeforeEach
    void setUp() {
        lenient().when(prescriptionPeriodPort.fetchStatsByPrescriptionIds(List.of(PRESCRIPTION_ID)))
                .thenReturn(Map.of());
        lenient().when(drugLookupPort.findByIds(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of());
        lenient().when(nutrientDepletionPort.findByDrugIds(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.of());
        lenient().when(prescriptionInsightRepository.findByPrescriptionId(org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(List.of());
        sut = new GetPrescriptionDetailUseCase(
                prescriptionRepository, drugLookupPort, fileStoragePort,
                new PatientAccessGuard(), prescriptionPeriodPort, nutrientDepletionPort,
                prescriptionInsightRepository, FIXED_CLOCK);
    }

    @AfterEach void tearDown() { UserContext.clear(); }

    @Test
    @DisplayName("본인 처방전 상세 — drugs 매핑 + presigned imageUrl")
    void detail_ownPrescription_returnsMappedDetail() {
        UserContext.set(OWNER_ID);
        Prescription p = prescription(OWNER_ID, "prescriptions/uuid.jpg");
        p.addDrug(matchedDrug(101L, "타이레놀정"));
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));
        given(drugLookupPort.findByIds(List.of(101L)))
                .willReturn(Map.of(101L, new DrugSummary(101L, "KD-001", "타이레놀정500밀리그램", "https://img.test/t.png")));
        given(fileStoragePort.generateGetUrl("prescriptions/uuid.jpg"))
                .willReturn("https://s3.test/presigned?sig=x");

        PrescriptionDetailResponse detail = sut.detail(PRESCRIPTION_ID);

        assertThat(detail.imageUrl()).isEqualTo("https://s3.test/presigned?sig=x");
        assertThat(detail.drugs()).hasSize(1);
        assertThat(detail.drugs().get(0).nameRaw()).isEqualTo("타이레놀정");
        assertThat(detail.drugs().get(0).matchedDrugName()).isEqualTo("타이레놀정500밀리그램");
        assertThat(detail.drugs().get(0).imageUrl()).isEqualTo("https://img.test/t.png");
    }

    @Test
    @DisplayName("타인 처방전 조회 — PATIENT_ACCESS_DENIED (403)")
    void detail_otherPatient_throwsAccessDenied() {
        UserContext.set(OTHER_ID);
        Prescription p = prescription(OWNER_ID, "prescriptions/uuid.jpg");
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> sut.detail(PRESCRIPTION_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);

        verify(fileStoragePort, never()).generateGetUrl(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("매칭 약품 — matchedKdCode 채워짐")
    void detail_matchedDrug_matchedKdCodeFilled() {
        UserContext.set(OWNER_ID);
        Prescription p = prescription(OWNER_ID, null);
        p.addDrug(matchedDrug(101L, "타이레놀정"));
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));
        given(drugLookupPort.findByIds(List.of(101L)))
                .willReturn(Map.of(101L, new DrugSummary(101L, "KD-001", "타이레놀정500밀리그램", "https://img.test/t.png")));

        PrescriptionDetailResponse detail = sut.detail(PRESCRIPTION_ID);

        assertThat(detail.drugs().get(0).matchedKdCode()).isEqualTo("KD-001");
        assertThat(detail.drugs().get(0).matchedDrugName()).isEqualTo("타이레놀정500밀리그램");
    }

    @Test
    @DisplayName("미매칭 약품(drugId null) — matchedDrugName null, matchedKdCode null")
    void detail_unmatchedDrug_matchedNameNull() {
        UserContext.set(OWNER_ID);
        Prescription p = prescription(OWNER_ID, "prescriptions/uuid.jpg");
        p.addDrug(unmatchedDrug("동광나자티딘캡슐"));
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));
        lenient().when(fileStoragePort.generateGetUrl("prescriptions/uuid.jpg"))
                .thenReturn("https://s3.test/x");

        PrescriptionDetailResponse detail = sut.detail(PRESCRIPTION_ID);

        assertThat(detail.drugs().get(0).matchedDrugName()).isNull();
        assertThat(detail.drugs().get(0).matchedKdCode()).isNull();
        assertThat(detail.drugs().get(0).imageUrl()).isNull();
    }

    @Test
    @DisplayName("imageKey 없으면 imageUrl null — presigned 발급 안 함")
    void detail_noImageKey_imageUrlNull() {
        UserContext.set(OWNER_ID);
        Prescription p = prescription(OWNER_ID, null);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));

        PrescriptionDetailResponse detail = sut.detail(PRESCRIPTION_ID);

        assertThat(detail.imageUrl()).isNull();
        verify(fileStoragePort, never()).generateGetUrl(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("존재하지 않는 처방전 — PRESCRIPTION_NOT_FOUND")
    void detail_notFound_throws() {
        UserContext.set(OWNER_ID);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.detail(PRESCRIPTION_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRESCRIPTION_NOT_FOUND);
    }

    @Test
    @DisplayName("label/memo 필드 — 반환 확인")
    void detail_labelAndMemo_returnedInResponse() {
        UserContext.set(OWNER_ID);
        Prescription p = prescription(OWNER_ID, null);
        ReflectionTestUtils.setField(p, "label", "복약 A");
        ReflectionTestUtils.setField(p, "memo", "식후 30분");
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));

        PrescriptionDetailResponse detail = sut.detail(PRESCRIPTION_ID);

        assertThat(detail.label()).isEqualTo("복약 A");
        assertThat(detail.memo()).isEqualTo("식후 30분");
    }

    @Test
    @DisplayName("PeriodStats 있으면 adherenceRate + status 계산")
    void detail_withPeriodStats_computesAdherenceAndStatus() {
        UserContext.set(OWNER_ID);
        LocalDate start = LocalDate.of(2026, 6, 1);
        LocalDate end   = LocalDate.of(2026, 6, 30);
        Prescription p = prescription(OWNER_ID, null);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));
        PeriodStats stats = new PeriodStats(start, end, 90L, 63L);
        given(prescriptionPeriodPort.fetchStatsByPrescriptionIds(List.of(PRESCRIPTION_ID)))
                .willReturn(Map.of(PRESCRIPTION_ID, stats));

        PrescriptionDetailResponse detail = sut.detail(PRESCRIPTION_ID);

        assertThat(detail.status()).isEqualTo(PrescriptionStatus.ONGOING);
        assertThat(detail.adherenceRate()).isEqualTo(63.0 / 90.0);
        assertThat(detail.periodStart()).isEqualTo(start);
        assertThat(detail.periodEnd()).isEqualTo(end);
    }

    @Test
    @DisplayName("symptom 필드 — 상세 응답에 포함")
    void detail_withSymptom_returnedInResponse() {
        UserContext.set(OWNER_ID);
        Prescription p = prescription(OWNER_ID, null);
        ReflectionTestUtils.setField(p, "symptom", "고혈압");
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));

        PrescriptionDetailResponse detail = sut.detail(PRESCRIPTION_ID);

        assertThat(detail.symptom()).isEqualTo("고혈압");
    }

    @Test
    @DisplayName("매칭 약품에 DIND 있으면 DrugDetail.nutrientNotes 포함")
    void detail_withNutrientNotes_populatesInDrugDetail() {
        UserContext.set(OWNER_ID);
        Prescription p = prescription(OWNER_ID, null);
        p.addDrug(matchedDrug(101L, "메트포르민정"));
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));
        given(drugLookupPort.findByIds(List.of(101L)))
                .willReturn(Map.of(101L, new DrugSummary(101L, "KD-999", "메트포르민정", null)));
        given(nutrientDepletionPort.findByDrugIds(List.of(101L)))
                .willReturn(Map.of(101L, List.of(
                        new NutrientNote("비타민 B12",
                                "장기 복용 시 비타민 B12 흡수에 영향을 줄 수 있어요.",
                                "식품의약품안전처 의약품정보"))));

        PrescriptionDetailResponse detail = sut.detail(PRESCRIPTION_ID);

        assertThat(detail.drugs().get(0).nutrientNotes()).hasSize(1);
        assertThat(detail.drugs().get(0).nutrientNotes().get(0).nutrient()).isEqualTo("비타민 B12");
    }

    @Test
    @DisplayName("DIND 없는 약품 — DrugDetail.nutrientNotes null")
    void detail_withoutNutrientNotes_nullInDrugDetail() {
        UserContext.set(OWNER_ID);
        Prescription p = prescription(OWNER_ID, null);
        p.addDrug(matchedDrug(101L, "타이레놀정"));
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));
        given(drugLookupPort.findByIds(List.of(101L)))
                .willReturn(Map.of(101L, new DrugSummary(101L, "KD-001", "타이레놀정", null)));

        PrescriptionDetailResponse detail = sut.detail(PRESCRIPTION_ID);

        assertThat(detail.drugs().get(0).nutrientNotes()).isNull();
    }

    @Test
    @DisplayName("insight 있으면 상세 응답에 inline 포함")
    void detail_withInsights_inlinedInResponse() {
        UserContext.set(OWNER_ID);
        Prescription p = prescription(OWNER_ID, null);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));
        given(prescriptionInsightRepository.findByPrescriptionId(PRESCRIPTION_ID))
                .willReturn(List.of(PrescriptionInsight.create(
                        PRESCRIPTION_ID, PrescriptionInsightType.RECOMMENDATION,
                        PrescriptionInsightSeverity.INFO, "비타민 B12 영향 가능",
                        "장기 복용 시 흡수에 영향을 줄 수 있어요.", "식약처", new BigDecimal("0.90"))));

        PrescriptionDetailResponse detail = sut.detail(PRESCRIPTION_ID);

        assertThat(detail.insights()).hasSize(1);
        assertThat(detail.insights().get(0).title()).isEqualTo("비타민 B12 영향 가능");
        assertThat(detail.insights().get(0).source()).isEqualTo("식약처");
    }

    @Test
    @DisplayName("insight 없으면 상세 응답 insights null")
    void detail_withoutInsights_nullInResponse() {
        UserContext.set(OWNER_ID);
        Prescription p = prescription(OWNER_ID, null);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));

        PrescriptionDetailResponse detail = sut.detail(PRESCRIPTION_ID);

        assertThat(detail.insights()).isNull();
    }

    private Prescription prescription(Long patientId, String imageKey) {
        Prescription p = Prescription.create(patientId, imageKey, LocalDate.of(2026, 6, 1));
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
