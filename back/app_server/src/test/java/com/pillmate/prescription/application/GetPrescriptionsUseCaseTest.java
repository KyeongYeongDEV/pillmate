package com.pillmate.prescription.application;

import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.dto.PrescriptionSummary;
import com.pillmate.prescription.application.port.PrescriptionPeriodPort;
import com.pillmate.prescription.application.port.PrescriptionPeriodPort.PeriodStats;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.model.PrescriptionStatus;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetPrescriptionsUseCase — 처방전 목록")
class GetPrescriptionsUseCaseTest {

    private static final Long PATIENT_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 24);
    private final AtomicLong idSeq = new AtomicLong(1);

    @Mock PrescriptionRepository prescriptionRepository;
    @Mock PrescriptionPeriodPort prescriptionPeriodPort;
    @Mock Clock clock;

    private GetPrescriptionsUseCase sut;

    @BeforeEach
    void setUp() {
        UserContext.set(PATIENT_ID);
        given(clock.instant()).willReturn(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant());
        given(clock.getZone()).willReturn(ZoneOffset.UTC);
        sut = new GetPrescriptionsUseCase(prescriptionRepository, prescriptionPeriodPort, clock);
    }

    @AfterEach
    void tearDown() { UserContext.clear(); }

    @Test
    @DisplayName("본인(UserContext) 처방전만 조회 위임")
    void list_queriesOnlyOwnPrescriptions() {
        given(prescriptionRepository.findAllByPatientId(PATIENT_ID)).willReturn(List.of());
        given(prescriptionPeriodPort.fetchStatsByPrescriptionIds(anyList())).willReturn(Map.of());

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
        given(prescriptionPeriodPort.fetchStatsByPrescriptionIds(anyList())).willReturn(Map.of());

        List<PrescriptionSummary> result = sut.list();

        assertThat(result).extracting(PrescriptionSummary::prescribedAt)
                .containsExactly(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 5, 1));
    }

    @Test
    @DisplayName("요약 항목 — drugCount + 앞 3개 약품명 요약")
    void list_summarizesDrugCountAndNames() {
        Prescription p = prescription(LocalDate.of(2026, 6, 1), "타이레놀", "아스피린", "이부프로펜", "오메프라졸");
        given(prescriptionRepository.findAllByPatientId(PATIENT_ID)).willReturn(List.of(p));
        given(prescriptionPeriodPort.fetchStatsByPrescriptionIds(anyList())).willReturn(Map.of());

        PrescriptionSummary summary = sut.list().get(0);

        assertThat(summary.drugCount()).isEqualTo(4);
        assertThat(summary.drugNames()).isEqualTo("타이레놀, 아스피린, 이부프로펜");
    }

    @Test
    @DisplayName("periodEnd >= today → ONGOING, daysRemaining = periodEnd - today")
    void list_ongoingWhenPeriodEndNotPast() {
        LocalDate periodStart = TODAY.minusDays(10);
        LocalDate periodEnd = TODAY.plusDays(5);
        Prescription p = prescription(LocalDate.of(2026, 6, 14));
        given(prescriptionRepository.findAllByPatientId(PATIENT_ID)).willReturn(List.of(p));
        given(prescriptionPeriodPort.fetchStatsByPrescriptionIds(anyList()))
                .willReturn(Map.of(p.getId(), new PeriodStats(periodStart, periodEnd, 0L, 0L)));

        PrescriptionSummary summary = sut.list().get(0);

        assertThat(summary.status()).isEqualTo(PrescriptionStatus.ONGOING);
        assertThat(summary.daysRemaining()).isEqualTo(5);
    }

    @Test
    @DisplayName("periodEnd < today → COMPLETED, daysRemaining null, progressRate=1.0")
    void list_completedWhenPeriodEndPast() {
        LocalDate periodStart = TODAY.minusDays(20);
        LocalDate periodEnd = TODAY.minusDays(1);
        Prescription p = prescription(LocalDate.of(2026, 6, 4));
        given(prescriptionRepository.findAllByPatientId(PATIENT_ID)).willReturn(List.of(p));
        given(prescriptionPeriodPort.fetchStatsByPrescriptionIds(anyList()))
                .willReturn(Map.of(p.getId(), new PeriodStats(periodStart, periodEnd, 0L, 0L)));

        PrescriptionSummary summary = sut.list().get(0);

        assertThat(summary.status()).isEqualTo(PrescriptionStatus.COMPLETED);
        assertThat(summary.daysRemaining()).isNull();
        assertThat(summary.progressRate()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("오늘이 마지막 날(periodEnd=today) → ONGOING + daysRemaining=0")
    void list_daysRemainingZeroOnLastDay() {
        LocalDate periodStart = TODAY.minusDays(6);
        LocalDate periodEnd = TODAY;
        Prescription p = prescription(LocalDate.of(2026, 6, 18));
        given(prescriptionRepository.findAllByPatientId(PATIENT_ID)).willReturn(List.of(p));
        given(prescriptionPeriodPort.fetchStatsByPrescriptionIds(anyList()))
                .willReturn(Map.of(p.getId(), new PeriodStats(periodStart, periodEnd, 0L, 0L)));

        PrescriptionSummary summary = sut.list().get(0);

        assertThat(summary.status()).isEqualTo(PrescriptionStatus.ONGOING);
        assertThat(summary.daysRemaining()).isEqualTo(0);
    }

    @Test
    @DisplayName("adherenceRate null when totalDoses=0")
    void list_adherenceRateNullWhenNoDoses() {
        Prescription p = prescription(LocalDate.of(2026, 6, 10));
        given(prescriptionRepository.findAllByPatientId(PATIENT_ID)).willReturn(List.of(p));
        given(prescriptionPeriodPort.fetchStatsByPrescriptionIds(anyList()))
                .willReturn(Map.of(p.getId(),
                        new PeriodStats(TODAY.minusDays(5), TODAY.plusDays(2), 0L, 0L)));

        PrescriptionSummary summary = sut.list().get(0);

        assertThat(summary.adherenceRate()).isNull();
    }

    @Test
    @DisplayName("adherenceRate = taken/total")
    void list_adherenceRateComputedFromDoses() {
        Prescription p = prescription(LocalDate.of(2026, 6, 10));
        given(prescriptionRepository.findAllByPatientId(PATIENT_ID)).willReturn(List.of(p));
        given(prescriptionPeriodPort.fetchStatsByPrescriptionIds(anyList()))
                .willReturn(Map.of(p.getId(),
                        new PeriodStats(TODAY.minusDays(5), TODAY.plusDays(2), 10L, 7L)));

        PrescriptionSummary summary = sut.list().get(0);

        assertThat(summary.adherenceRate()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("label/memo가 summary에 반영됨")
    void list_labelAndMemoInSummary() {
        Prescription p = Prescription.create(PATIENT_ID, "img.jpg",
                LocalDate.of(2026, 6, 10), "내과 처방", "감기약");
        ReflectionTestUtils.setField(p, "id", idSeq.getAndIncrement());
        given(prescriptionRepository.findAllByPatientId(PATIENT_ID)).willReturn(List.of(p));
        given(prescriptionPeriodPort.fetchStatsByPrescriptionIds(anyList())).willReturn(Map.of());

        PrescriptionSummary summary = sut.list().get(0);

        assertThat(summary.label()).isEqualTo("내과 처방");
        assertThat(summary.memo()).isEqualTo("감기약");
    }

    @Test
    @DisplayName("schedules 없으면 fallback: prescribedAt + 29일")
    void list_fallbackPeriodWhenNoSchedules() {
        LocalDate prescribedAt = LocalDate.of(2026, 6, 10);
        Prescription p = prescription(prescribedAt);
        given(prescriptionRepository.findAllByPatientId(PATIENT_ID)).willReturn(List.of(p));
        given(prescriptionPeriodPort.fetchStatsByPrescriptionIds(anyList())).willReturn(Map.of());

        PrescriptionSummary summary = sut.list().get(0);

        assertThat(summary.periodStart()).isEqualTo(prescribedAt);
        assertThat(summary.periodEnd()).isEqualTo(prescribedAt.plusDays(29));
    }

    private Prescription prescription(LocalDate prescribedAt, String... drugNames) {
        Prescription p = Prescription.create(PATIENT_ID, "prescriptions/uuid.jpg", prescribedAt);
        ReflectionTestUtils.setField(p, "id", idSeq.getAndIncrement());
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
