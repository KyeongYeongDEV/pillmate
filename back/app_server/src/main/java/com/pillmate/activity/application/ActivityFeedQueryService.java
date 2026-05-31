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
        List<Long> memberIds = resolveMemberIds(viewerId, groupId);
        if (memberIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<ActivityFeed> feeds = activityFeedRepository.findByActorUserIdIn(memberIds, limit);
        Map<Long, String> nameById = buildNameMap(memberIds);
        return feeds.stream()
                .map(f -> ActivityFeedItem.from(f, nameById.getOrDefault(f.getActorUserId(), "멤버")))
                .toList();
    }

    private List<Long> resolveMemberIds(Long viewerId, Long groupId) {
        if (groupId == null) {
            return membershipRepository.findGroupMemberUserIds(viewerId);
        }
        if (!membershipRepository.existsByCareGroupIdAndUserId(groupId, viewerId)) {
            throw new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        }
        return membershipRepository.findByCareGroupId(groupId).stream()
                .map(Membership::getUserId)
                .filter(id -> !id.equals(viewerId))
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
