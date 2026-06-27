package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.dto.LatestPrescriptionWithInsightResponse;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.model.PrescriptionInsight;
import com.pillmate.prescription.domain.model.PrescriptionInsightSeverity;
import com.pillmate.prescription.domain.model.PrescriptionInsightType;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetLatestPrescriptionWithInsightUseCase — 홈 최신 처방전 + insight")
class GetLatestPrescriptionWithInsightUseCaseTest {

    private static final Long OWNER_ID = 7L;
    private static final Long OTHER_ID = 99L;
    private static final Long PRESCRIPTION_ID = 42L;

    @Mock PrescriptionRepository prescriptionRepository;
    @Mock PrescriptionInsightRepository prescriptionInsightRepository;

    private GetLatestPrescriptionWithInsightUseCase sut;

    @BeforeEach
    void setUp() {
        sut = new GetLatestPrescriptionWithInsightUseCase(
                prescriptionRepository, prescriptionInsightRepository, new PatientAccessGuard());
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("본인 최신 처방전 + insight 반환")
    void loadLatest_ownPrescriptionWithInsight_returns() {
        UserContext.set(OWNER_ID);
        Prescription latest = prescription(OWNER_ID, "타이레놀");
        given(prescriptionRepository.findLatestByPatientId(OWNER_ID)).willReturn(Optional.of(latest));
        given(prescriptionInsightRepository.findByPrescriptionId(PRESCRIPTION_ID))
                .willReturn(List.of(insight()));

        LatestPrescriptionWithInsightResponse response = sut.loadLatestForPatient(OWNER_ID);

        assertThat(response.prescriptionId()).isEqualTo(PRESCRIPTION_ID);
        assertThat(response.drugCount()).isEqualTo(1);
        assertThat(response.primaryDrugName()).isEqualTo("타이레놀");
        assertThat(response.insights()).hasSize(1);
        assertThat(response.insights().get(0).source()).isEqualTo("식약처");
    }

    @Test
    @DisplayName("insight 없으면 insights null")
    void loadLatest_noInsight_insightsNull() {
        UserContext.set(OWNER_ID);
        Prescription latest = prescription(OWNER_ID, "타이레놀");
        given(prescriptionRepository.findLatestByPatientId(OWNER_ID)).willReturn(Optional.of(latest));
        given(prescriptionInsightRepository.findByPrescriptionId(PRESCRIPTION_ID)).willReturn(List.of());

        LatestPrescriptionWithInsightResponse response = sut.loadLatestForPatient(OWNER_ID);

        assertThat(response.insights()).isNull();
    }

    @Test
    @DisplayName("처방전 없으면 null 반환")
    void loadLatest_noPrescription_returnsNull() {
        UserContext.set(OWNER_ID);
        given(prescriptionRepository.findLatestByPatientId(OWNER_ID)).willReturn(Optional.empty());

        LatestPrescriptionWithInsightResponse response = sut.loadLatestForPatient(OWNER_ID);

        assertThat(response).isNull();
    }

    @Test
    @DisplayName("타인 patientId 요청 → PATIENT_ACCESS_DENIED (403)")
    void loadLatest_otherPatient_throwsAccessDenied() {
        UserContext.set(OTHER_ID);
        lenient().when(prescriptionRepository.findLatestByPatientId(OWNER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.loadLatestForPatient(OWNER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);

        verify(prescriptionRepository, never()).findLatestByPatientId(OWNER_ID);
    }

    private Prescription prescription(Long patientId, String drugNameRaw) {
        Prescription p = Prescription.create(patientId, null, LocalDate.of(2026, 6, 10));
        ReflectionTestUtils.setField(p, "id", PRESCRIPTION_ID);
        p.addDrug(PrescribedDrug.builder()
                .drugId(101L).nameRaw(drugNameRaw)
                .doseAmount(new BigDecimal("1.00")).doseUnit("정")
                .frequency(3).durationDays(7).confidence(new BigDecimal("0.95"))
                .build());
        return p;
    }

    private PrescriptionInsight insight() {
        return PrescriptionInsight.create(PRESCRIPTION_ID, PrescriptionInsightType.RECOMMENDATION,
                PrescriptionInsightSeverity.INFO, "비타민 B12 영향 가능",
                "장기 복용 시 흡수에 영향을 줄 수 있어요.", "식약처", new BigDecimal("0.90"));
    }
}
