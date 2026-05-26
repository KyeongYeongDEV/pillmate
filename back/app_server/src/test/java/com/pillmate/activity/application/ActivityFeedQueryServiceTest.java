package com.pillmate.activity.application;

import com.pillmate.activity.application.dto.ActivityFeedItem;
import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

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

    private User dummyUser(String name) {
        return User.dummy(name);
    }
}
