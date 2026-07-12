package com.pillmate.activity.application;

import com.pillmate.activity.application.dto.ActivityFeedItem;
import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.activity.application.port.ActivityFeedCachePort;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@DisplayName("ActivityFeedQueryService — 활동 피드 조회 (배치 쿼리)")
@ExtendWith(MockitoExtension.class)
class ActivityFeedQueryServiceTest {

    @Mock ActivityFeedRepository activityFeedRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock UserRepository userRepository;
    @Mock ActivityFeedCachePort activityFeedCachePort;
    @InjectMocks ActivityFeedQueryService sut;

    @Test
    @DisplayName("다른 그룹 멤버 활동 반환 — actorNickname 포함, actorUserId 미포함")
    void query_returnsGroupMemberActivities_withNicknameNotUserId() {
        Long viewerId = 1L;
        Long memberId = 2L;
        ActivityFeed feed = ActivityFeed.create(memberId, ActivityType.DOSE_TAKEN, TimeOfDay.NOON, "점심약 복용", null);

        given(membershipRepository.findGroupMemberUserIds(viewerId)).willReturn(List.of(memberId));
        given(activityFeedRepository.findByActorUserIdIn(anyList(), anyInt())).willReturn(List.of(feed));
        given(userRepository.findAllByIdIn(List.of(memberId))).willReturn(List.of(userOf(memberId, "할머니")));

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

    // T-BE-ACTIVITY-FEED-BATCH 안 A: 그룹 모드 — 멤버 N회 findByActorSince → 단일 IN + 인메모리 joinedAt 필터
    @Test
    @DisplayName("그룹 모드 — 단일 findByActorUserIdIn 1회 + joinedAt 이전 활동 인메모리 제외 (N+1 제거, 격리 시맨틱 유지)")
    void groupMode_singleQueryFiltersByJoinedAtInMemory() {
        Long viewerId = 1L;
        Long memberId = 2L;
        Instant joined = Instant.parse("2026-06-28T00:00:00Z");
        Membership m1 = Membership.of(10L, viewerId, MemberRole.ADMIN, null);
        Membership m2 = Membership.of(10L, memberId, MemberRole.PATIENT, viewerId);
        ReflectionTestUtils.setField(m2, "joinedAt", joined);

        ActivityFeed beforeJoin = feedAt(memberId, "가입 전 활동", joined.minusSeconds(3600));
        ActivityFeed afterJoin = feedAt(memberId, "가입 후 활동", joined.plusSeconds(3600));

        given(membershipRepository.existsByCareGroupIdAndUserId(10L, viewerId)).willReturn(true);
        given(membershipRepository.findByCareGroupId(10L)).willReturn(List.of(m1, m2));
        given(activityFeedRepository.findByActorUserIdIn(eq(List.of(memberId)), anyInt()))
                .willReturn(List.of(afterJoin, beforeJoin));
        given(userRepository.findAllByIdIn(List.of(memberId))).willReturn(List.of(userOf(memberId, "할머니")));

        List<ActivityFeedItem> result = sut.query(viewerId, 10L, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).summary()).isEqualTo("가입 후 활동");
        assertThat(result.get(0).actorNickname()).isEqualTo("할머니");
        then(activityFeedRepository).should(times(1)).findByActorUserIdIn(anyList(), anyInt());
        then(activityFeedRepository).should(never()).findByActorSince(anyLong(), any(), anyInt());
    }

    @Test
    @DisplayName("그룹 모드 — joinedAt 정확히 같은 시각 활동은 포함 (findByActorSince GreaterThanEqual 시맨틱 보존)")
    void groupMode_feedAtExactJoinInstant_included() {
        Long viewerId = 1L;
        Long memberId = 2L;
        Instant joined = Instant.parse("2026-06-28T00:00:00Z");
        Membership m1 = Membership.of(10L, viewerId, MemberRole.ADMIN, null);
        Membership m2 = Membership.of(10L, memberId, MemberRole.PATIENT, viewerId);
        ReflectionTestUtils.setField(m2, "joinedAt", joined);

        given(membershipRepository.existsByCareGroupIdAndUserId(10L, viewerId)).willReturn(true);
        given(membershipRepository.findByCareGroupId(10L)).willReturn(List.of(m1, m2));
        given(activityFeedRepository.findByActorUserIdIn(anyList(), anyInt()))
                .willReturn(List.of(feedAt(memberId, "가입 순간 활동", joined)));
        given(userRepository.findAllByIdIn(List.of(memberId))).willReturn(List.of(userOf(memberId, "할머니")));

        List<ActivityFeedItem> result = sut.query(viewerId, 10L, 10);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("그룹 모드 — 멤버별 다른 joinedAt cutoff 각각 적용 + 최신순 limit")
    void groupMode_perMemberCutoffAndSortLimit() {
        Long viewerId = 1L;
        Long earlyMember = 2L;
        Long lateMember = 3L;
        Instant earlyJoin = Instant.parse("2026-06-01T00:00:00Z");
        Instant lateJoin = Instant.parse("2026-06-28T00:00:00Z");
        Membership m1 = Membership.of(10L, viewerId, MemberRole.ADMIN, null);
        Membership m2 = Membership.of(10L, earlyMember, MemberRole.PATIENT, viewerId);
        Membership m3 = Membership.of(10L, lateMember, MemberRole.GUARDIAN, viewerId);
        ReflectionTestUtils.setField(m2, "joinedAt", earlyJoin);
        ReflectionTestUtils.setField(m3, "joinedAt", lateJoin);

        ActivityFeed earlyOld = feedAt(earlyMember, "early-6/10", Instant.parse("2026-06-10T00:00:00Z"));
        ActivityFeed lateBeforeJoin = feedAt(lateMember, "late-6/10(가입전)", Instant.parse("2026-06-10T01:00:00Z"));
        ActivityFeed lateNew = feedAt(lateMember, "late-6/29", Instant.parse("2026-06-29T00:00:00Z"));

        given(membershipRepository.existsByCareGroupIdAndUserId(10L, viewerId)).willReturn(true);
        given(membershipRepository.findByCareGroupId(10L)).willReturn(List.of(m1, m2, m3));
        given(activityFeedRepository.findByActorUserIdIn(eq(List.of(earlyMember, lateMember)), eq(20)))
                .willReturn(List.of(lateNew, lateBeforeJoin, earlyOld));
        given(userRepository.findAllByIdIn(List.of(earlyMember, lateMember)))
                .willReturn(List.of(userOf(earlyMember, "할머니"), userOf(lateMember, "보호자")));

        List<ActivityFeedItem> result = sut.query(viewerId, 10L, 10);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).summary()).isEqualTo("late-6/29");
        assertThat(result.get(1).summary()).isEqualTo("early-6/10");
    }

    @Test
    @DisplayName("새 그룹 — 멤버 joinedAt 이후 활동 없음 → 0건 (과거 활동 미노출)")
    void newGroup_zero() {
        Long viewerId = 1L;
        Long memberId = 2L;
        Membership m1 = Membership.of(10L, viewerId, MemberRole.ADMIN, null);
        Membership m2 = Membership.of(10L, memberId, MemberRole.PATIENT, viewerId); // joinedAt = now
        ActivityFeed pastFeed = feedAt(memberId, "과거 활동", Instant.parse("2026-01-01T00:00:00Z"));
        given(membershipRepository.existsByCareGroupIdAndUserId(10L, viewerId)).willReturn(true);
        given(membershipRepository.findByCareGroupId(10L)).willReturn(List.of(m1, m2));
        given(activityFeedRepository.findByActorUserIdIn(anyList(), anyInt())).willReturn(List.of(pastFeed));
        given(userRepository.findAllByIdIn(anyList())).willReturn(List.of(userOf(memberId, "할머니")));

        List<ActivityFeedItem> result = sut.query(viewerId, 10L, 10);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("그룹 모드 — 이름 조회는 findAllByIdIn 1회 배치 (findById 미사용), 누락 유저 '멤버' fallback")
    void groupMode_nameLookupBatched_missingUserFallback() {
        Long viewerId = 1L;
        Long memberId = 2L;
        Instant joined = Instant.parse("2026-06-01T00:00:00Z");
        Membership m1 = Membership.of(10L, viewerId, MemberRole.ADMIN, null);
        Membership m2 = Membership.of(10L, memberId, MemberRole.PATIENT, viewerId);
        ReflectionTestUtils.setField(m2, "joinedAt", joined);

        given(membershipRepository.existsByCareGroupIdAndUserId(10L, viewerId)).willReturn(true);
        given(membershipRepository.findByCareGroupId(10L)).willReturn(List.of(m1, m2));
        given(activityFeedRepository.findByActorUserIdIn(anyList(), anyInt()))
                .willReturn(List.of(feedAt(memberId, "활동", joined.plusSeconds(60))));
        given(userRepository.findAllByIdIn(List.of(memberId))).willReturn(List.of());

        List<ActivityFeedItem> result = sut.query(viewerId, 10L, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).actorNickname()).isEqualTo("멤버");
        then(userRepository).should(times(1)).findAllByIdIn(anyList());
        then(userRepository).should(never()).findById(anyLong());
    }

    @Test
    @DisplayName("개인 모드(groupId=null) — 기존 무필터 findByActorUserIdIn 유지(findByActorSince 미사용)")
    void personalMode_unchanged() {
        Long viewerId = 1L;
        ActivityFeed feed = ActivityFeed.create(viewerId, ActivityType.DOSE_TAKEN, TimeOfDay.MORNING, "아침약", null);
        given(membershipRepository.findGroupMemberUserIds(viewerId)).willReturn(List.of(viewerId));
        given(activityFeedRepository.findByActorUserIdIn(anyList(), anyInt())).willReturn(List.of(feed));
        given(userRepository.findAllByIdIn(List.of(viewerId))).willReturn(List.of(userOf(viewerId, "나")));

        List<ActivityFeedItem> result = sut.query(viewerId, null, 10);

        assertThat(result).hasSize(1);
        then(activityFeedRepository).should().findByActorUserIdIn(anyList(), anyInt());
        then(activityFeedRepository).should(never()).findByActorSince(anyLong(), any(), anyInt());
    }

    // T-BE-REDIS-ACTIVITY-FEED-CACHE — 캐시 hit/miss
    @Test
    @DisplayName("그룹 모드 캐시 hit — DB(멤버십 목록/피드/이름) 미조회, 캐시 결과 반환")
    void groupMode_cacheHit_skipsDb() {
        Long viewerId = 1L;
        List<ActivityFeedItem> cached = List.of(new ActivityFeedItem(
                "할머니", ActivityType.DOSE_TAKEN, TimeOfDay.NOON, "점심약 복용",
                null, Instant.parse("2026-07-01T03:00:00Z")));
        given(membershipRepository.existsByCareGroupIdAndUserId(10L, viewerId)).willReturn(true);
        given(activityFeedCachePort.getGroupFeed(10L, viewerId, 10)).willReturn(java.util.Optional.of(cached));

        List<ActivityFeedItem> result = sut.query(viewerId, 10L, 10);

        assertThat(result).isEqualTo(cached);
        then(membershipRepository).should(never()).findByCareGroupId(anyLong());
        then(activityFeedRepository).should(never()).findByActorUserIdIn(anyList(), anyInt());
        then(userRepository).should(never()).findAllByIdIn(anyList());
    }

    @Test
    @DisplayName("그룹 모드 캐시 miss — DB 계산 후 putGroupFeed 저장")
    void groupMode_cacheMiss_computesAndStores() {
        Long viewerId = 1L;
        Long memberId = 2L;
        Instant joined = Instant.parse("2026-06-01T00:00:00Z");
        Membership m1 = Membership.of(10L, viewerId, MemberRole.ADMIN, null);
        Membership m2 = Membership.of(10L, memberId, MemberRole.PATIENT, viewerId);
        ReflectionTestUtils.setField(m2, "joinedAt", joined);

        given(membershipRepository.existsByCareGroupIdAndUserId(10L, viewerId)).willReturn(true);
        given(membershipRepository.findByCareGroupId(10L)).willReturn(List.of(m1, m2));
        given(activityFeedRepository.findByActorUserIdIn(anyList(), anyInt()))
                .willReturn(List.of(feedAt(memberId, "활동", joined.plusSeconds(60))));
        given(userRepository.findAllByIdIn(List.of(memberId))).willReturn(List.of(userOf(memberId, "할머니")));

        List<ActivityFeedItem> result = sut.query(viewerId, 10L, 10);

        assertThat(result).hasSize(1);
        then(activityFeedCachePort).should().putGroupFeed(eq(10L), eq(viewerId), eq(10), anyList());
    }

    @Test
    @DisplayName("접근 권한 없는 viewer — 캐시 조회 전에 GROUP_ACCESS_DENIED (권한 우회 불가)")
    void groupMode_accessDeniedBeforeCache() {
        given(membershipRepository.existsByCareGroupIdAndUserId(10L, 99L)).willReturn(false);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> sut.query(99L, 10L, 10))
                .isInstanceOf(com.pillmate.common.exception.PillmateException.class);
        then(activityFeedCachePort).should(never()).getGroupFeed(anyLong(), anyLong(), anyInt());
    }

    private ActivityFeed feedAt(Long actorUserId, String summary, Instant occurredAt) {
        ActivityFeed feed = ActivityFeed.create(actorUserId, ActivityType.DOSE_TAKEN, TimeOfDay.NOON, summary, null);
        ReflectionTestUtils.setField(feed, "occurredAt", occurredAt);
        return feed;
    }

    private User userOf(Long id, String name) {
        User user = User.dummy(name);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
