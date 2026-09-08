package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.ShareablePrescriptionView;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.prescription.application.PrescriptionViewAssembler;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.application.port.NutrientDepletionPort;
import com.pillmate.prescription.application.port.PrescriptionPeriodPort;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.model.PrescriptionStatus;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@DisplayName("MedicationShareService — 약봉투(처방전) 단위 그룹 공유 판정/설정 단위 테스트")
@ExtendWith(MockitoExtension.class)
class MedicationShareServiceTest {

    private static final Long GROUP_ID = 1L;
    private static final Long OTHER_GROUP_ID = 2L;
    private static final Long OWNER_ID = 10L;
    private static final Long PRESCRIPTION_ID = 100L;
    // 오늘=2026-08-20
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-20T09:00:00Z"), ZoneOffset.UTC);

    @Mock PrescriptionRepository prescriptionRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock DrugLookupPort drugLookupPort;
    @Mock NutrientDepletionPort nutrientDepletionPort;
    @Mock PrescriptionPeriodPort prescriptionPeriodPort;

    private MedicationShareService sut;

    @BeforeEach
    void setUp() {
        PrescriptionViewAssembler assembler = new PrescriptionViewAssembler(drugLookupPort, nutrientDepletionPort);
        sut = new MedicationShareService(
                prescriptionRepository, membershipRepository, assembler, prescriptionPeriodPort, FIXED_CLOCK);
    }

    // ─── getShareablePrescriptions ──────────────────────────────────────────

    @Test
    @DisplayName("getShareablePrescriptions — 비멤버 요청자는 GROUP_ACCESS_DENIED")
    void getShareablePrescriptions_nonMemberOwner_throws() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(false);

        assertThatThrownBy(() -> sut.getShareablePrescriptions(GROUP_ID, OWNER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);
        then(prescriptionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("getShareablePrescriptions — ONGOING 약봉투만 반환, COMPLETED 는 제외")
    void getShareablePrescriptions_returnsOnlyOngoing() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        Prescription ongoing = prescription(1L, OWNER_ID, GROUP_ID, LocalDate.of(2026, 8, 1), false, "감기약");
        Prescription completed = prescription(2L, OWNER_ID, GROUP_ID, LocalDate.of(2026, 1, 1), true, "지난약");
        given(prescriptionRepository.findAllByPatientIdAndCareGroupId(OWNER_ID, GROUP_ID))
                .willReturn(List.of(ongoing, completed));
        given(prescriptionPeriodPort.fetchStatsByPrescriptionIds(List.of(1L, 2L))).willReturn(Map.of());

        List<ShareablePrescriptionView> result = sut.getShareablePrescriptions(GROUP_ID, OWNER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).prescriptionId()).isEqualTo(1L);
        assertThat(result.get(0).label()).isEqualTo("감기약");
        assertThat(result.get(0).status()).isEqualTo(PrescriptionStatus.ONGOING);
    }

    @Test
    @DisplayName("getShareablePrescriptions — 각 약봉투의 공유 여부(shared) 그대로 노출")
    void getShareablePrescriptions_exposesSharedFlag() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        Prescription shared = prescription(1L, OWNER_ID, GROUP_ID, LocalDate.of(2026, 8, 1), true, null);
        given(prescriptionRepository.findAllByPatientIdAndCareGroupId(OWNER_ID, GROUP_ID))
                .willReturn(List.of(shared));
        given(prescriptionPeriodPort.fetchStatsByPrescriptionIds(List.of(1L))).willReturn(Map.of());

        List<ShareablePrescriptionView> result = sut.getShareablePrescriptions(GROUP_ID, OWNER_ID);

        assertThat(result.get(0).shared()).isTrue();
    }

    @Test
    @DisplayName("getShareablePrescriptions — 이 그룹 소속 약봉투만 조회(repository 에 groupId 그대로 위임)")
    void getShareablePrescriptions_queriesOnlyThisGroup() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(prescriptionRepository.findAllByPatientIdAndCareGroupId(OWNER_ID, GROUP_ID)).willReturn(List.of());

        sut.getShareablePrescriptions(GROUP_ID, OWNER_ID);

        then(prescriptionRepository).should().findAllByPatientIdAndCareGroupId(OWNER_ID, GROUP_ID);
        then(prescriptionRepository).should(never()).findAllByPatientIdAndCareGroupId(OWNER_ID, OTHER_GROUP_ID);
    }

    // ─── updatePrescriptionShare ─────────────────────────────────────────────

    @Test
    @DisplayName("updatePrescriptionShare — enabled=true, 소유자 본인+이 그룹 소속 약봉투면 공유 켜고 저장")
    void updatePrescriptionShare_enable_saves() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        Prescription prescription = prescription(PRESCRIPTION_ID, OWNER_ID, GROUP_ID, LocalDate.of(2026, 8, 1), false, null);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(java.util.Optional.of(prescription));

        sut.updatePrescriptionShare(GROUP_ID, OWNER_ID, PRESCRIPTION_ID, true);

        ArgumentCaptor<Prescription> captor = ArgumentCaptor.forClass(Prescription.class);
        then(prescriptionRepository).should().save(captor.capture());
        assertThat(captor.getValue().isSharedWithGroup()).isTrue();
    }

    @Test
    @DisplayName("updatePrescriptionShare — enabled=false, 공유 끄고 저장")
    void updatePrescriptionShare_disable_saves() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        Prescription prescription = prescription(PRESCRIPTION_ID, OWNER_ID, GROUP_ID, LocalDate.of(2026, 8, 1), true, null);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(java.util.Optional.of(prescription));

        sut.updatePrescriptionShare(GROUP_ID, OWNER_ID, PRESCRIPTION_ID, false);

        ArgumentCaptor<Prescription> captor = ArgumentCaptor.forClass(Prescription.class);
        then(prescriptionRepository).should().save(captor.capture());
        assertThat(captor.getValue().isSharedWithGroup()).isFalse();
    }

    @Test
    @DisplayName("updatePrescriptionShare — 비멤버 요청자는 GROUP_ACCESS_DENIED, 약봉투 조회 자체 미실행")
    void updatePrescriptionShare_nonMemberOwner_throws() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(false);

        assertThatThrownBy(() -> sut.updatePrescriptionShare(GROUP_ID, OWNER_ID, PRESCRIPTION_ID, true))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);
        then(prescriptionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("updatePrescriptionShare — 존재하지 않는 약봉투는 PRESCRIPTION_NOT_FOUND")
    void updatePrescriptionShare_notFound_throws() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> sut.updatePrescriptionShare(GROUP_ID, OWNER_ID, PRESCRIPTION_ID, true))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRESCRIPTION_NOT_FOUND);
        then(prescriptionRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("updatePrescriptionShare — 남의 약봉투를 토글하려 하면 PATIENT_ACCESS_DENIED, 저장 없음")
    void updatePrescriptionShare_notOwner_throws() {
        Long strangerOwnerId = 999L;
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        Prescription othersPrescription =
                prescription(PRESCRIPTION_ID, strangerOwnerId, GROUP_ID, LocalDate.of(2026, 8, 1), false, null);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(java.util.Optional.of(othersPrescription));

        assertThatThrownBy(() -> sut.updatePrescriptionShare(GROUP_ID, OWNER_ID, PRESCRIPTION_ID, true))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);
        then(prescriptionRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("updatePrescriptionShare — 크로스그룹 차단: 다른 그룹 소속 약봉투를 이 groupId 로 토글 시도하면 PATIENT_ACCESS_DENIED")
    void updatePrescriptionShare_crossGroupPrescription_throws() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        Prescription otherGroupPrescription =
                prescription(PRESCRIPTION_ID, OWNER_ID, OTHER_GROUP_ID, LocalDate.of(2026, 8, 1), false, null);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(java.util.Optional.of(otherGroupPrescription));

        assertThatThrownBy(() -> sut.updatePrescriptionShare(GROUP_ID, OWNER_ID, PRESCRIPTION_ID, true))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);
        then(prescriptionRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private Prescription prescription(Long id, Long patientId, Long careGroupId,
                                       LocalDate prescribedAt, boolean sharedWithGroup, String label) {
        Prescription p = Prescription.create(patientId, "prescriptions/uuid.jpg", prescribedAt, label, null);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "careGroupId", careGroupId);
        if (sharedWithGroup) {
            p.shareWithGroup();
        }
        return p;
    }
}
