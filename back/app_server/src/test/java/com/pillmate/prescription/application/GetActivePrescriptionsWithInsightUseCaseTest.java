package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.dto.LatestPrescriptionWithInsightResponse;
import com.pillmate.prescription.application.port.ActiveMedicationPort;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetActivePrescriptionsWithInsightUseCase — 오늘 복약중 + insight 목록 (순환용)")
class GetActivePrescriptionsWithInsightUseCaseTest {

    private static final Long OWNER_ID = 7L;
    private static final Long OTHER_ID = 99L;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 3);

    @Mock PrescriptionRepository prescriptionRepository;
    @Mock PrescriptionInsightRepository prescriptionInsightRepository;
    @Mock ActiveMedicationPort activeMedicationPort;

    private GetActivePrescriptionsWithInsightUseCase sut;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-03T02:00:00Z"), ZoneId.of("Asia/Seoul"));
        sut = new GetActivePrescriptionsWithInsightUseCase(
                prescriptionRepository, prescriptionInsightRepository,
                activeMedicationPort, new PatientAccessGuard(), clock);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("활성 처방전만 insight 목록으로 반환하고 만료(비활성)는 제외 — prescribedAt DESC")
    void loadActive_returnsOnlyActiveWithInsight_sortedDesc() {
        UserContext.set(OWNER_ID);
        Prescription active1 = prescription(11L, "타이레놀", LocalDate.of(2026, 6, 10));
        Prescription active2 = prescription(12L, "아모잘탄", LocalDate.of(2026, 7, 1));
        Prescription expired = prescription(13L, "옛날약", LocalDate.of(2026, 1, 1));
        given(activeMedicationPort.findActivePrescriptionIds(OWNER_ID, TODAY))
                .willReturn(Set.of(11L, 12L));
        given(prescriptionRepository.findAllByPatientId(OWNER_ID))
                .willReturn(List.of(active1, active2, expired));
        given(prescriptionInsightRepository.findByPrescriptionIds(Set.of(11L, 12L)))
                .willReturn(Map.of(11L, List.of(insight(11L)), 12L, List.of(insight(12L))));

        List<LatestPrescriptionWithInsightResponse> result = sut.loadActiveForPatient(OWNER_ID);

        assertThat(result).extracting(LatestPrescriptionWithInsightResponse::prescriptionId)
                .containsExactly(12L, 11L);
        assertThat(result.get(0).insights()).hasSize(1);
    }

    @Test
    @DisplayName("insight 없는 활성 처방전은 제외")
    void loadActive_excludesActiveWithoutInsight() {
        UserContext.set(OWNER_ID);
        Prescription withInsight = prescription(11L, "타이레놀", LocalDate.of(2026, 6, 10));
        Prescription noInsight = prescription(12L, "무인사이트", LocalDate.of(2026, 7, 1));
        given(activeMedicationPort.findActivePrescriptionIds(OWNER_ID, TODAY))
                .willReturn(Set.of(11L, 12L));
        given(prescriptionRepository.findAllByPatientId(OWNER_ID))
                .willReturn(List.of(withInsight, noInsight));
        given(prescriptionInsightRepository.findByPrescriptionIds(Set.of(11L, 12L)))
                .willReturn(Map.of(11L, List.of(insight(11L))));

        List<LatestPrescriptionWithInsightResponse> result = sut.loadActiveForPatient(OWNER_ID);

        assertThat(result).extracting(LatestPrescriptionWithInsightResponse::prescriptionId)
                .containsExactly(11L);
    }

    @Test
    @DisplayName("복약중 처방전 없으면 빈 목록")
    void loadActive_noActive_returnsEmpty() {
        UserContext.set(OWNER_ID);
        given(activeMedicationPort.findActivePrescriptionIds(OWNER_ID, TODAY)).willReturn(Set.of());

        List<LatestPrescriptionWithInsightResponse> result = sut.loadActiveForPatient(OWNER_ID);

        assertThat(result).isEmpty();
        verify(prescriptionRepository, never()).findAllByPatientId(OWNER_ID);
    }

    @Test
    @DisplayName("타인 patientId 요청 → PATIENT_ACCESS_DENIED (403)")
    void loadActive_otherPatient_throwsAccessDenied() {
        UserContext.set(OTHER_ID);

        assertThatThrownBy(() -> sut.loadActiveForPatient(OWNER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);

        verify(activeMedicationPort, never()).findActivePrescriptionIds(OWNER_ID, TODAY);
    }

    private Prescription prescription(Long id, String drugNameRaw, LocalDate prescribedAt) {
        Prescription p = Prescription.create(OWNER_ID, null, prescribedAt);
        ReflectionTestUtils.setField(p, "id", id);
        p.addDrug(PrescribedDrug.builder()
                .drugId(101L).nameRaw(drugNameRaw)
                .doseAmount(new BigDecimal("1.00")).doseUnit("정")
                .frequency(3).durationDays(7).confidence(new BigDecimal("0.95"))
                .build());
        return p;
    }

    private PrescriptionInsight insight(Long prescriptionId) {
        return PrescriptionInsight.create(prescriptionId, PrescriptionInsightType.RECOMMENDATION,
                PrescriptionInsightSeverity.INFO, "비타민 B12 영향 가능",
                "장기 복용 시 흡수에 영향을 줄 수 있어요.", "식약처", new BigDecimal("0.90"));
    }
}
