package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.ShareSettingsView;
import com.pillmate.caregroup.application.dto.ShareSettingView;
import com.pillmate.caregroup.application.dto.ShareablePrescriptionView;
import com.pillmate.caregroup.domain.model.MedicationShareGrant;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MedicationShareGrantRepository;
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
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@DisplayName("MedicationShareService — 2축(구성원/약봉투) 공유 설정 및 판정 단위 테스트")
@ExtendWith(MockitoExtension.class)
class MedicationShareServiceTest {

    private static final Long GROUP_ID = 1L;
    private static final Long OTHER_GROUP_ID = 2L;
    private static final Long OWNER_ID = 10L;
    private static final Long VIEWER_ID = 20L;
    private static final Long PRESCRIPTION_ID = 100L;
    // 오늘=2026-08-20
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-20T09:00:00Z"), ZoneOffset.UTC);

    @Mock PrescriptionRepository prescriptionRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock MedicationShareGrantRepository medicationShareGrantRepository;
    @Mock UserRepository userRepository;
    @Mock DrugLookupPort drugLookupPort;
    @Mock NutrientDepletionPort nutrientDepletionPort;
    @Mock PrescriptionPeriodPort prescriptionPeriodPort;

    private MedicationShareService sut;

    @BeforeEach
    void setUp() {
        PrescriptionViewAssembler assembler = new PrescriptionViewAssembler(drugLookupPort, nutrientDepletionPort);
        sut = new MedicationShareService(
                prescriptionRepository, membershipRepository, medicationShareGrantRepository, userRepository,
                assembler, prescriptionPeriodPort, FIXED_CLOCK);
    }

    // ─── getShareSettings ───────────────────────────────────────────────────

    @Test
    @DisplayName("getShareSettings — 비멤버 요청자는 GROUP_ACCESS_DENIED")
    void getShareSettings_nonMemberOwner_throws() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(false);

        assertThatThrownBy(() -> sut.getShareSettings(GROUP_ID, OWNER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);
        then(prescriptionRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("getShareSettings — 구성원(본인 제외) 목록 + 각 공유 여부")
    void getShareSettings_returnsMembersExcludingSelf() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                Membership.of(GROUP_ID, OWNER_ID, MemberRole.GUARDIAN, null),
                Membership.of(GROUP_ID, VIEWER_ID, MemberRole.PATIENT, null)));
        given(medicationShareGrantRepository.findByCareGroupIdAndOwnerUserId(GROUP_ID, OWNER_ID))
                .willReturn(List.of(MedicationShareGrant.of(GROUP_ID, OWNER_ID, VIEWER_ID, FIXED_CLOCK)));
        given(userRepository.findById(VIEWER_ID)).willReturn(Optional.of(user(VIEWER_ID, "아버지")));
        given(prescriptionRepository.findAllByPatientId(OWNER_ID)).willReturn(List.of());

        ShareSettingsView result = sut.getShareSettings(GROUP_ID, OWNER_ID);

        assertThat(result.members()).hasSize(1);
        ShareSettingView member = result.members().get(0);
        assertThat(member.userId()).isEqualTo(VIEWER_ID);
        assertThat(member.name()).isEqualTo("아버지");
        assertThat(member.shared()).isTrue();
    }

    @Test
    @DisplayName("getShareSettings — myColor 는 설정 전엔 null, 설정 후엔 그 값")
    void getShareSettings_myColor_reflectsOwnerPreferredColor() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of());
        given(prescriptionRepository.findAllByPatientId(OWNER_ID)).willReturn(List.of());
        given(userRepository.findById(OWNER_ID)).willReturn(Optional.of(user(OWNER_ID, "나")));

        ShareSettingsView withoutColor = sut.getShareSettings(GROUP_ID, OWNER_ID);
        assertThat(withoutColor.myColor()).isNull();

        User colored = user(OWNER_ID, "나");
        ReflectionTestUtils.setField(colored, "preferredColor", "#5C6BC0");
        given(userRepository.findById(OWNER_ID)).willReturn(Optional.of(colored));

        ShareSettingsView withColor = sut.getShareSettings(GROUP_ID, OWNER_ID);
        assertThat(withColor.myColor()).isEqualTo("#5C6BC0");
    }

    @Test
    @DisplayName("getShareSettings — care_group_id 가 NULL 인 내 약봉투도 반드시 포함(회귀 방지)")
    void getShareSettings_includesPrescriptionsWithNullCareGroupId() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of());
        Prescription nullGroupPrescription = prescription(1L, OWNER_ID, null, LocalDate.of(2026, 8, 1), false, "감기약");
        given(prescriptionRepository.findAllByPatientId(OWNER_ID)).willReturn(List.of(nullGroupPrescription));
        given(prescriptionPeriodPort.fetchStatsByPrescriptionIds(List.of(1L))).willReturn(Map.of());

        ShareSettingsView result = sut.getShareSettings(GROUP_ID, OWNER_ID);

        assertThat(result.prescriptions()).hasSize(1);
        ShareablePrescriptionView view = result.prescriptions().get(0);
        assertThat(view.prescriptionId()).isEqualTo(1L);
        assertThat(view.label()).isEqualTo("감기약");
        assertThat(view.status()).isEqualTo(PrescriptionStatus.ONGOING);
    }

    @Test
    @DisplayName("getShareSettings — 내 약봉투는 ONGOING 만, COMPLETED 는 제외")
    void getShareSettings_prescriptions_returnsOnlyOngoing() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of());
        Prescription ongoing = prescription(1L, OWNER_ID, null, LocalDate.of(2026, 8, 1), false, "감기약");
        Prescription completed = prescription(2L, OWNER_ID, null, LocalDate.of(2026, 1, 1), true, "지난약");
        given(prescriptionRepository.findAllByPatientId(OWNER_ID)).willReturn(List.of(ongoing, completed));
        given(prescriptionPeriodPort.fetchStatsByPrescriptionIds(List.of(1L, 2L))).willReturn(Map.of());

        ShareSettingsView result = sut.getShareSettings(GROUP_ID, OWNER_ID);

        assertThat(result.prescriptions()).hasSize(1);
        assertThat(result.prescriptions().get(0).prescriptionId()).isEqualTo(1L);
    }

    // ─── updateMemberShare ──────────────────────────────────────────────────

    @Test
    @DisplayName("updateMemberShare — enabled=true, 신규 grant 생성")
    void updateMemberShare_enable_grants() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, VIEWER_ID)).willReturn(true);
        given(medicationShareGrantRepository.findByCareGroupIdAndOwnerUserIdAndViewerUserId(GROUP_ID, OWNER_ID, VIEWER_ID))
                .willReturn(Optional.empty());

        sut.updateMemberShare(GROUP_ID, OWNER_ID, VIEWER_ID, true);

        ArgumentCaptor<MedicationShareGrant> captor = ArgumentCaptor.forClass(MedicationShareGrant.class);
        then(medicationShareGrantRepository).should().save(captor.capture());
        assertThat(captor.getValue().getCareGroupId()).isEqualTo(GROUP_ID);
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(OWNER_ID);
        assertThat(captor.getValue().getViewerUserId()).isEqualTo(VIEWER_ID);
    }

    @Test
    @DisplayName("updateMemberShare — enabled=false, grant 삭제")
    void updateMemberShare_disable_deletes() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, VIEWER_ID)).willReturn(true);

        sut.updateMemberShare(GROUP_ID, OWNER_ID, VIEWER_ID, false);

        then(medicationShareGrantRepository).should()
                .deleteByCareGroupIdAndOwnerUserIdAndViewerUserId(GROUP_ID, OWNER_ID, VIEWER_ID);
    }

    @Test
    @DisplayName("updateMemberShare — 대상이 자기자신이면 MEDICATION_SHARE_INVALID_TARGET")
    void updateMemberShare_targetIsSelf_throws() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);

        assertThatThrownBy(() -> sut.updateMemberShare(GROUP_ID, OWNER_ID, OWNER_ID, true))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDICATION_SHARE_INVALID_TARGET);
        then(medicationShareGrantRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("updateMemberShare — 대상이 그룹 비멤버면 MEDICATION_SHARE_INVALID_TARGET")
    void updateMemberShare_targetNotMember_throws() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, VIEWER_ID)).willReturn(false);

        assertThatThrownBy(() -> sut.updateMemberShare(GROUP_ID, OWNER_ID, VIEWER_ID, true))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDICATION_SHARE_INVALID_TARGET);
    }

    @Test
    @DisplayName("updateMemberShare — 비멤버 owner 는 GROUP_ACCESS_DENIED")
    void updateMemberShare_nonMemberOwner_throws() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(false);

        assertThatThrownBy(() -> sut.updateMemberShare(GROUP_ID, OWNER_ID, VIEWER_ID, true))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);
    }

    // ─── updatePrescriptionShare ─────────────────────────────────────────────

    @Test
    @DisplayName("updatePrescriptionShare — 소유자 본인이면 care_group_id 가 NULL 이어도 성공(회귀 방지)")
    void updatePrescriptionShare_ownPrescriptionWithNullCareGroupId_succeeds() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        Prescription prescription = prescription(PRESCRIPTION_ID, OWNER_ID, null, LocalDate.of(2026, 8, 1), false, null);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(prescription));

        sut.updatePrescriptionShare(GROUP_ID, OWNER_ID, PRESCRIPTION_ID, true);

        ArgumentCaptor<Prescription> captor = ArgumentCaptor.forClass(Prescription.class);
        then(prescriptionRepository).should().save(captor.capture());
        assertThat(captor.getValue().isSharedWithGroup()).isTrue();
    }

    @Test
    @DisplayName("updatePrescriptionShare — enabled=false, 공유 끄고 저장")
    void updatePrescriptionShare_disable_saves() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        Prescription prescription = prescription(PRESCRIPTION_ID, OWNER_ID, null, LocalDate.of(2026, 8, 1), true, null);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(prescription));

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
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.empty());

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
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(othersPrescription));

        assertThatThrownBy(() -> sut.updatePrescriptionShare(GROUP_ID, OWNER_ID, PRESCRIPTION_ID, true))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);
        then(prescriptionRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }

    // ─── isMemberGranted ────────────────────────────────────────────────────

    @Test
    @DisplayName("isMemberGranted — 본인(owner==viewer)이면 항상 true, 조회 없음")
    void isMemberGranted_self_returnsTrue() {
        boolean result = sut.isMemberGranted(GROUP_ID, OWNER_ID, OWNER_ID);

        assertThat(result).isTrue();
        then(membershipRepository).shouldHaveNoInteractions();
        then(medicationShareGrantRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("isMemberGranted — grant 있고 owner·viewer 둘 다 그 그룹 ACTIVE 멤버면 true")
    void isMemberGranted_grantedAndBothActiveMembers_returnsTrue() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, VIEWER_ID)).willReturn(true);
        given(medicationShareGrantRepository.existsByCareGroupIdAndOwnerUserIdAndViewerUserId(GROUP_ID, OWNER_ID, VIEWER_ID))
                .willReturn(true);

        assertThat(sut.isMemberGranted(GROUP_ID, OWNER_ID, VIEWER_ID)).isTrue();
    }

    @Test
    @DisplayName("isMemberGranted — grant 없으면 false")
    void isMemberGranted_noGrant_returnsFalse() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, VIEWER_ID)).willReturn(true);
        given(medicationShareGrantRepository.existsByCareGroupIdAndOwnerUserIdAndViewerUserId(GROUP_ID, OWNER_ID, VIEWER_ID))
                .willReturn(false);

        assertThat(sut.isMemberGranted(GROUP_ID, OWNER_ID, VIEWER_ID)).isFalse();
    }

    @Test
    @DisplayName("isMemberGranted — 다른 그룹에서만 grant 되어 있으면 false(크로스그룹 차단)")
    void isMemberGranted_grantedInDifferentGroup_returnsFalse() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, VIEWER_ID)).willReturn(true);
        // OTHER_GROUP_ID 에는 grant 가 있어도 조회 대상 groupId(GROUP_ID) 로만 스코프 조회하므로 미스텁 = 반영 안 됨
        given(medicationShareGrantRepository.existsByCareGroupIdAndOwnerUserIdAndViewerUserId(GROUP_ID, OWNER_ID, VIEWER_ID))
                .willReturn(false);
        lenient().when(medicationShareGrantRepository.existsByCareGroupIdAndOwnerUserIdAndViewerUserId(OTHER_GROUP_ID, OWNER_ID, VIEWER_ID))
                .thenReturn(true);

        assertThat(sut.isMemberGranted(GROUP_ID, OWNER_ID, VIEWER_ID)).isFalse();
    }

    @Test
    @DisplayName("isMemberGranted — viewer 가 그룹 비멤버(탈퇴 포함)면 false, grant 조회 자체 안 함")
    void isMemberGranted_viewerNotActiveMember_returnsFalse() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, VIEWER_ID)).willReturn(false);

        assertThat(sut.isMemberGranted(GROUP_ID, OWNER_ID, VIEWER_ID)).isFalse();
        then(medicationShareGrantRepository).shouldHaveNoInteractions();
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

    private User user(Long id, String name) {
        User user = User.dummy(name);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
