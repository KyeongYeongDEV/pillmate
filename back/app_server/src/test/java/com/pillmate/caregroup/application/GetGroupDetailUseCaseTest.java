package com.pillmate.caregroup.application;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivitySeverity;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import com.pillmate.caregroup.application.dto.GroupDetailResponse;
import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.model.InviteCode;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.CareGroupRepository;
import com.pillmate.caregroup.domain.repository.InviteCodeRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

@DisplayName("GetGroupDetailUseCase — 단위 테스트")
@ExtendWith(MockitoExtension.class)
class GetGroupDetailUseCaseTest {

    @Mock CareGroupRepository careGroupRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock UserRepository userRepository;
    @Mock InviteCodeRepository inviteCodeRepository;
    @Mock ActivityFeedRepository activityFeedRepository;
    @InjectMocks GetGroupDetailService sut;

    private static final Long GROUP_ID = 10L;
    private static final Long USER_ID  = 1L;
    private static final Long MEMBER_2 = 2L;

    @Test
    @DisplayName("그룹 상세 — 멤버 + 초대코드 + 최근 활동")
    void detail_returnsMembersAndInviteCodeAndActivities() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, USER_ID)).willReturn(true);
        given(careGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group("우리가족")));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(
                List.of(
                        Membership.of(GROUP_ID, USER_ID,  MemberRole.ADMIN,   null),
                        Membership.of(GROUP_ID, MEMBER_2, MemberRole.PATIENT, USER_ID)
                ));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(User.dummy("어머니")));
        given(userRepository.findById(MEMBER_2)).willReturn(Optional.of(User.dummy("아버지")));
        given(inviteCodeRepository.findActiveByCareGroupId(GROUP_ID)).willReturn(
                Optional.of(InviteCode.generate(GROUP_ID, USER_ID)));
        given(activityFeedRepository.findByActorUserIdIn(anyList(), anyInt())).willReturn(
                List.of(activityFeed(MEMBER_2)));

        GroupDetailResponse response = sut.detail(GROUP_ID, USER_ID);

        assertThat(response.groupId()).isEqualTo(GROUP_ID);
        assertThat(response.name()).isEqualTo("우리가족");
        assertThat(response.memberCount()).isEqualTo(2);
        assertThat(response.members()).hasSize(2);
        assertThat(response.inviteCode()).isNotNull();
        assertThat(response.recentActivities()).hasSize(1);
    }

    @Test
    @DisplayName("비멤버 접근 시 GROUP_ACCESS_DENIED 예외")
    void detail_nonMember_throws() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, USER_ID)).willReturn(false);

        assertThatThrownBy(() -> sut.detail(GROUP_ID, USER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);
    }

    @Test
    @DisplayName("초대코드 없으면 null 반환")
    void detail_noInviteCode_inviteCodeNull() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, USER_ID)).willReturn(true);
        given(careGroupRepository.findById(GROUP_ID)).willReturn(Optional.of(group("우리가족")));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(
                List.of(Membership.of(GROUP_ID, USER_ID, MemberRole.ADMIN, null)));
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(User.dummy("어머니")));
        given(inviteCodeRepository.findActiveByCareGroupId(GROUP_ID)).willReturn(Optional.empty());
        given(activityFeedRepository.findByActorUserIdIn(anyList(), anyInt())).willReturn(List.of());

        GroupDetailResponse response = sut.detail(GROUP_ID, USER_ID);

        assertThat(response.inviteCode()).isNull();
    }

    private CareGroup group(String name) {
        return CareGroup.create(name, USER_ID);
    }

    private ActivityFeed activityFeed(Long actorId) {
        return ActivityFeed.create(actorId, ActivityType.DOSE_TAKEN,
                TimeOfDay.MORNING, "아침약 복용", ActivitySeverity.INFO);
    }
}
