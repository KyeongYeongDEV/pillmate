package com.pillmate.activity.application;

import com.pillmate.activity.application.dto.ActivityFeedItem;
import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@DisplayName("ActivityFeedQueryService — 활동 피드 조회")
@ExtendWith(MockitoExtension.class)
class ActivityFeedQueryServiceTest {

    @Mock ActivityFeedRepository activityFeedRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock UserRepository userRepository;
    @InjectMocks ActivityFeedQueryService sut;

    @Test
    @DisplayName("다른 그룹 멤버 활동 반환 — actorNickname 포함, actorUserId 미포함")
    void query_returnsGroupMemberActivities_withNicknameNotUserId() {
        Long viewerId = 1L;
        Long memberId = 2L;
        ActivityFeed feed = ActivityFeed.create(memberId, ActivityType.DOSE_TAKEN, TimeOfDay.NOON, "점심약 복용", null);

        given(membershipRepository.findGroupMemberUserIds(viewerId)).willReturn(List.of(memberId));
        given(activityFeedRepository.findByActorUserIdIn(anyList(), anyInt())).willReturn(List.of(feed));
        given(userRepository.findById(memberId)).willReturn(Optional.of(dummyUser("할머니")));

        List<ActivityFeedItem> result = sut.query(viewerId, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).actorNickname()).isEqualTo("할머니");
        assertThat(result.get(0).timeSlot()).isEqualTo(TimeOfDay.NOON);
    }

    @Test
    @DisplayName("그룹 멤버가 없으면 빈 목록 반환")
    void query_noGroupMembers_returnsEmpty() {
        given(membershipRepository.findGroupMemberUserIds(1L)).willReturn(List.of());

        List<ActivityFeedItem> result = sut.query(1L, 20);

        assertThat(result).isEmpty();
    }

    // T-HOME-ACTIVITY-FILTER-PLUS-LINK: 그룹 모드 — 멤버별 joinedAt 이후만 (새 그룹 깨끗)
    @Test
    @DisplayName("그룹 모드 — 멤버별 joinedAt 으로 findByActorSince 호출(무필터 findByActorUserIdIn 미사용)")
    void groupMode_filteredByJoinedAt() {
        Long viewerId = 1L;
        Long memberId = 2L;
        Instant joined2 = Instant.parse("2026-06-28T00:00:00Z");
        Membership m1 = Membership.of(10L, viewerId, MemberRole.ADMIN, null);
        Membership m2 = Membership.of(10L, memberId, MemberRole.PATIENT, viewerId);
        ReflectionTestUtils.setField(m2, "joinedAt", joined2);
        ActivityFeed feed = ActivityFeed.create(memberId, ActivityType.DOSE_TAKEN, TimeOfDay.NOON, "점심약 복용", null);

        given(membershipRepository.existsByCareGroupIdAndUserId(10L, viewerId)).willReturn(true);
        given(membershipRepository.findByCareGroupId(10L)).willReturn(List.of(m1, m2));
        given(activityFeedRepository.findByActorSince(eq(memberId), eq(joined2), anyInt()))
                .willReturn(List.of(feed));
        given(userRepository.findById(memberId)).willReturn(Optional.of(dummyUser("할머니")));

        List<ActivityFeedItem> result = sut.query(viewerId, 10L, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).actorNickname()).isEqualTo("할머니");
        then(activityFeedRepository).should().findByActorSince(eq(memberId), eq(joined2), anyInt());
        then(activityFeedRepository).should(never()).findByActorUserIdIn(anyList(), anyInt());
    }

    @Test
    @DisplayName("새 그룹 — 멤버 joinedAt 이후 활동 없음 → 0건 (과거 활동 미노출)")
    void newGroup_zero() {
        Long viewerId = 1L;
        Long memberId = 2L;
        Membership m1 = Membership.of(10L, viewerId, MemberRole.ADMIN, null);
        Membership m2 = Membership.of(10L, memberId, MemberRole.PATIENT, viewerId); // joinedAt = now
        given(membershipRepository.existsByCareGroupIdAndUserId(10L, viewerId)).willReturn(true);
        given(membershipRepository.findByCareGroupId(10L)).willReturn(List.of(m1, m2));
        given(activityFeedRepository.findByActorSince(eq(memberId), any(), anyInt())).willReturn(List.of());

        List<ActivityFeedItem> result = sut.query(viewerId, 10L, 10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("개인 모드(groupId=null) — 기존 무필터 findByActorUserIdIn 유지(findByActorSince 미사용)")
    void personalMode_unchanged() {
        Long viewerId = 1L;
        ActivityFeed feed = ActivityFeed.create(viewerId, ActivityType.DOSE_TAKEN, TimeOfDay.MORNING, "아침약", null);
        given(membershipRepository.findGroupMemberUserIds(viewerId)).willReturn(List.of(viewerId));
        given(activityFeedRepository.findByActorUserIdIn(anyList(), anyInt())).willReturn(List.of(feed));
        given(userRepository.findById(viewerId)).willReturn(Optional.of(dummyUser("나")));

        List<ActivityFeedItem> result = sut.query(viewerId, null, 10);

        assertThat(result).hasSize(1);
        then(activityFeedRepository).should().findByActorUserIdIn(anyList(), anyInt());
        then(activityFeedRepository).should(never()).findByActorSince(anyLong(), any(), anyInt());
    }

    private User dummyUser(String name) {
        return User.dummy(name);
    }
}
