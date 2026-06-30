package com.pillmate.activity.application;

import com.pillmate.activity.application.dto.ActivityFeedItem;
import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private List<ActivityFeedItem> groupFeed(Long viewerId, Long groupId, int limit) {
        if (!membershipRepository.existsByCareGroupIdAndUserId(groupId, viewerId)) {
            throw new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        }
        List<Membership> members = membershipRepository.findByCareGroupId(groupId).stream()
                .filter(m -> !m.getUserId().equals(viewerId))
                .toList();
        List<ActivityFeed> feeds = members.stream()
                .flatMap(m -> activityFeedRepository
                        .findByActorSince(m.getUserId(), m.getJoinedAt(), limit).stream())
                .sorted(Comparator.comparing(ActivityFeed::getOccurredAt).reversed())
                .limit(limit)
                .toList();
        return mapFeeds(feeds, buildNameMap(members.stream().map(Membership::getUserId).toList()));
    }

    private List<ActivityFeedItem> mapFeeds(List<ActivityFeed> feeds, Map<Long, String> nameById) {
        return feeds.stream()
                .map(f -> ActivityFeedItem.from(f, nameById.getOrDefault(f.getActorUserId(), "멤버")))
                .toList();
    }

    private Map<Long, String> buildNameMap(List<Long> userIds) {
        return userIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> userRepository.findById(id).map(u -> u.getName()).orElse("멤버")
                ));
    }
}
