package com.pillmate.activity.application;

import com.pillmate.activity.application.dto.ActivityFeedItem;
import com.pillmate.activity.application.port.ActivityFeedCachePort;
import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityFeedQueryService {

    private final ActivityFeedRepository activityFeedRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final ActivityFeedCachePort activityFeedCachePort;

    public List<ActivityFeedItem> query(Long viewerId, int limit) {
        return query(viewerId, null, limit);
    }

    public List<ActivityFeedItem> query(Long viewerId, Long groupId, int limit) {
        if (groupId == null) {
            return personalFeed(viewerId, limit);
        }
        return groupFeed(viewerId, groupId, limit);
    }

    // 개인 피드 — 기존 무필터 유지 (viewer 자신 + 그룹원 활동 전체)
    private List<ActivityFeedItem> personalFeed(Long viewerId, int limit) {
        List<Long> memberIds = membershipRepository.findGroupMemberUserIds(viewerId);
        if (memberIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ActivityFeed> feeds = activityFeedRepository.findByActorUserIdIn(memberIds, limit);
        return mapFeeds(feeds, buildNameMap(memberIds));
    }

    // 그룹 피드 — 멤버별 가입(joinedAt) 시점 이후만 (새 그룹은 과거 활동 미노출). viewer 자신 제외(기존 동작 유지)
    // 30초 폴링 hot path: 멤버 N회 쿼리 대신 단일 IN over-fetch 후 인메모리 joinedAt 필터 (T-BE-ACTIVITY-FEED-BATCH 안 A)
    private List<ActivityFeedItem> groupFeed(Long viewerId, Long groupId, int limit) {
        if (!membershipRepository.existsByCareGroupIdAndUserId(groupId, viewerId)) {
            throw new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        }
        return activityFeedCachePort.getGroupFeed(groupId, viewerId, limit)
                .orElseGet(() -> loadAndCacheGroupFeed(viewerId, groupId, limit));
    }

    private List<ActivityFeedItem> loadAndCacheGroupFeed(Long viewerId, Long groupId, int limit) {
        List<Membership> members = membershipRepository.findByCareGroupId(groupId).stream()
                .filter(m -> !m.getUserId().equals(viewerId))
                .toList();
        if (members.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> memberIds = members.stream().map(Membership::getUserId).toList();
        List<ActivityFeed> feeds = fetchFeedsAfterJoin(members, memberIds, limit);
        List<ActivityFeedItem> items = mapFeeds(feeds, buildNameMap(memberIds));
        activityFeedCachePort.putGroupFeed(groupId, viewerId, limit, items);
        return items;
    }

    private List<ActivityFeed> fetchFeedsAfterJoin(List<Membership> members, List<Long> memberIds, int limit) {
        Map<Long, Instant> joinedAtByUserId = members.stream()
                .collect(Collectors.toMap(Membership::getUserId, Membership::getJoinedAt));
        return activityFeedRepository.findByActorUserIdIn(memberIds, limit * members.size()).stream()
                .filter(feed -> isAfterJoin(feed, joinedAtByUserId))
                .sorted(Comparator.comparing(ActivityFeed::getOccurredAt).reversed())
                .limit(limit)
                .toList();
    }

    private boolean isAfterJoin(ActivityFeed feed, Map<Long, Instant> joinedAtByUserId) {
        Instant joinedAt = joinedAtByUserId.get(feed.getActorUserId());
        return joinedAt == null || !feed.getOccurredAt().isBefore(joinedAt);
    }

    private List<ActivityFeedItem> mapFeeds(List<ActivityFeed> feeds, Map<Long, String> nameById) {
        return feeds.stream()
                .map(f -> ActivityFeedItem.from(f, nameById.getOrDefault(f.getActorUserId(), "멤버")))
                .toList();
    }

    private Map<Long, String> buildNameMap(List<Long> userIds) {
        return userRepository.findAllByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, User::getName));
    }
}
