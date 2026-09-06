package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.ShareSettingUpdateResponse;
import com.pillmate.caregroup.application.dto.ShareSettingView;
import com.pillmate.caregroup.domain.model.MedicationShareGrant;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MedicationShareGrantRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@DisplayName("MedicationShareService — 알약 정보(L2) 공유 판정/설정 단위 테스트")
@ExtendWith(MockitoExtension.class)
class MedicationShareServiceTest {

    private static final Long GROUP_ID = 1L;
    private static final Long OWNER_ID = 10L;
    private static final Long VIEWER_ID = 20L;
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-20T09:00:00Z"), ZoneOffset.UTC);

    @Mock MembershipRepository membershipRepository;
    @Mock MedicationShareGrantRepository medicationShareGrantRepository;
    @Mock UserRepository userRepository;

    private MedicationShareService sut;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        sut = new MedicationShareService(membershipRepository, medicationShareGrantRepository, userRepository, FIXED_CLOCK);
    }

    @Test
    @DisplayName("getShareSettings — 비멤버 요청자는 GROUP_ACCESS_DENIED")
    void getShareSettings_nonMemberOwner_throws() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(false);

        assertThatThrownBy(() -> sut.getShareSettings(GROUP_ID, OWNER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);
    }

    @Test
    @DisplayName("getShareSettings — 본인 제외 그룹 멤버 목록 + 공유 여부 표기")
    void getShareSettings_returnsOtherMembersWithSharedFlag() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                Membership.of(GROUP_ID, OWNER_ID, MemberRole.ADMIN, null),
                Membership.of(GROUP_ID, VIEWER_ID, MemberRole.PATIENT, OWNER_ID)
        ));
        given(medicationShareGrantRepository.findByCareGroupIdAndOwnerUserId(GROUP_ID, OWNER_ID))
                .willReturn(List.of(MedicationShareGrant.of(GROUP_ID, OWNER_ID, VIEWER_ID, FIXED_CLOCK)));
        given(userRepository.findById(VIEWER_ID)).willReturn(Optional.of(User.dummy("아버지")));

        List<ShareSettingView> result = sut.getShareSettings(GROUP_ID, OWNER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).userId()).isEqualTo(VIEWER_ID);
        assertThat(result.get(0).name()).isEqualTo("아버지");
        assertThat(result.get(0).role()).isEqualTo("PATIENT");
        assertThat(result.get(0).shared()).isTrue();
    }

    @Test
    @DisplayName("getShareSettings — 공유 안 한 멤버는 shared=false")
    void getShareSettings_notShared_falseFlag() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                Membership.of(GROUP_ID, OWNER_ID, MemberRole.ADMIN, null),
                Membership.of(GROUP_ID, VIEWER_ID, MemberRole.PATIENT, OWNER_ID)
        ));
        given(medicationShareGrantRepository.findByCareGroupIdAndOwnerUserId(GROUP_ID, OWNER_ID))
                .willReturn(List.of());
        given(userRepository.findById(VIEWER_ID)).willReturn(Optional.of(User.dummy("아버지")));

        List<ShareSettingView> result = sut.getShareSettings(GROUP_ID, OWNER_ID);

        assertThat(result.get(0).shared()).isFalse();
    }

    @Test
    @DisplayName("updateShareSetting — enabled=true, grant 없으면 새로 저장")
    void updateShareSetting_enableWhenAbsent_saves() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, VIEWER_ID)).willReturn(true);
        given(medicationShareGrantRepository
                .findByCareGroupIdAndOwnerUserIdAndViewerUserId(GROUP_ID, OWNER_ID, VIEWER_ID))
                .willReturn(Optional.empty());

        ShareSettingUpdateResponse response = sut.updateShareSetting(GROUP_ID, OWNER_ID, VIEWER_ID, true);

        assertThat(response.userId()).isEqualTo(VIEWER_ID);
        assertThat(response.shared()).isTrue();
        ArgumentCaptor<MedicationShareGrant> captor = ArgumentCaptor.forClass(MedicationShareGrant.class);
        then(medicationShareGrantRepository).should().save(captor.capture());
        assertThat(captor.getValue().getCareGroupId()).isEqualTo(GROUP_ID);
        assertThat(captor.getValue().getOwnerUserId()).isEqualTo(OWNER_ID);
        assertThat(captor.getValue().getViewerUserId()).isEqualTo(VIEWER_ID);
    }

    @Test
    @DisplayName("updateShareSetting — enabled=true, 이미 grant 있으면 멱등(중복 저장 없음)")
    void updateShareSetting_enableWhenAlreadyGranted_isIdempotent() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, VIEWER_ID)).willReturn(true);
        given(medicationShareGrantRepository
                .findByCareGroupIdAndOwnerUserIdAndViewerUserId(GROUP_ID, OWNER_ID, VIEWER_ID))
                .willReturn(Optional.of(MedicationShareGrant.of(GROUP_ID, OWNER_ID, VIEWER_ID, FIXED_CLOCK)));

        sut.updateShareSetting(GROUP_ID, OWNER_ID, VIEWER_ID, true);

        then(medicationShareGrantRepository).should(never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("updateShareSetting — enabled=false, 요청자 owner 조건으로만 삭제 (남의 grant 삭제 불가 구조)")
    void updateShareSetting_disable_deletesWithOwnerCondition() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, VIEWER_ID)).willReturn(true);

        ShareSettingUpdateResponse response = sut.updateShareSetting(GROUP_ID, OWNER_ID, VIEWER_ID, false);

        assertThat(response.shared()).isFalse();
        then(medicationShareGrantRepository).should()
                .deleteByCareGroupIdAndOwnerUserIdAndViewerUserId(GROUP_ID, OWNER_ID, VIEWER_ID);
    }

    @Test
    @DisplayName("updateShareSetting — 비멤버 요청자는 GROUP_ACCESS_DENIED, grant 미변경")
    void updateShareSetting_nonMemberOwner_throws() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(false);

        assertThatThrownBy(() -> sut.updateShareSetting(GROUP_ID, OWNER_ID, VIEWER_ID, true))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);
        then(medicationShareGrantRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("updateShareSetting — 자기 자신을 대상으로 하면 MEDICATION_SHARE_INVALID_TARGET")
    void updateShareSetting_targetSelf_throws() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);

        assertThatThrownBy(() -> sut.updateShareSetting(GROUP_ID, OWNER_ID, OWNER_ID, true))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDICATION_SHARE_INVALID_TARGET);
        then(medicationShareGrantRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("updateShareSetting — viewer 가 그룹 비멤버면 MEDICATION_SHARE_INVALID_TARGET")
    void updateShareSetting_viewerNotMember_throws() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, VIEWER_ID)).willReturn(false);

        assertThatThrownBy(() -> sut.updateShareSetting(GROUP_ID, OWNER_ID, VIEWER_ID, true))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDICATION_SHARE_INVALID_TARGET);
        then(medicationShareGrantRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("canViewMedicationDetail — 본인이면 조회 없이 true")
    void canViewMedicationDetail_self_true() {
        boolean result = sut.canViewMedicationDetail(OWNER_ID, OWNER_ID);

        assertThat(result).isTrue();
        then(medicationShareGrantRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("canViewMedicationDetail — grant 존재하면 true")
    void canViewMedicationDetail_grantExists_true() {
        given(medicationShareGrantRepository.existsByOwnerUserIdAndViewerUserId(OWNER_ID, VIEWER_ID))
                .willReturn(true);

        assertThat(sut.canViewMedicationDetail(OWNER_ID, VIEWER_ID)).isTrue();
    }

    @Test
    @DisplayName("canViewMedicationDetail — grant 없으면 false")
    void canViewMedicationDetail_noGrant_false() {
        given(medicationShareGrantRepository.existsByOwnerUserIdAndViewerUserId(OWNER_ID, VIEWER_ID))
                .willReturn(false);

        assertThat(sut.canViewMedicationDetail(OWNER_ID, VIEWER_ID)).isFalse();
    }
}
