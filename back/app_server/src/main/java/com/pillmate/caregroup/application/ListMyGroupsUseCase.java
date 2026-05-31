package com.pillmate.caregroup.application;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import com.pillmate.caregroup.application.dto.MyGroupSummary;
import com.pillmate.caregroup.application.dto.MyGroupSummary.LastActivitySummary;
import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.CareGroupRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListMyGroupsUseCase {

    private static final int MEMBERS_PREVIEW_LIMIT = 3;

    private final MembershipRepository membershipRepository;
    private final CareGroupRepository careGroupRepository;
    private final UserRepository userRepository;
    private final ActivityFeedRepository activityFeedRepository;

    @Transactional(readOnly = true)
    public List<MyGroupSummary> listMyGroups(Long userId) {
        List<Membership> myMemberships = membershipRepository.findByUserId(userId);
        if (myMemberships.isEmpty()) {
            return List.of();
        }
        Map<Long, CareGroup> groups = loadGroupsKeyedById(myMemberships);
        return myMemberships.stream()
                .map(m -> toSummary(m, groups.get(m.getCareGroupId())))
                .toList();
    }

    private Map<Long, CareGroup> loadGroupsKeyedById(List<Membership> memberships) {
        List<Long> ids = memberships.stream().map(Membership::getCareGroupId).toList();
        return careGroupRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(CareGroup::getId, Function.identity()));
    }

    private MyGroupSummary toSummary(Membership my, CareGroup group) {
        Long groupId = my.getCareGroupId();
        List<Membership> allMembers = membershipRepository.findByCareGroupId(groupId);
        List<String> preview = membersPreview(allMembers);
        return new MyGroupSummary(
                groupId,
                group == null ? "(unknown)" : group.getName(),
                my.getRole().name(),
                allMembers.size(),
                preview,
                latestActivity(allMembers),
                0,
                my.isPinned()
        );
    }

    private List<String> membersPreview(List<Membership> members) {
        return members.stream()
                .limit(MEMBERS_PREVIEW_LIMIT)
                .map(m -> userRepository.findById(m.getUserId()).map(u -> u.getName()).orElse("멤버"))
                .toList();
    }

    private LastActivitySummary latestActivity(List<Membership> members) {
        List<Long> memberIds = members.stream().map(Membership::getUserId).toList();
        List<ActivityFeed> recent = activityFeedRepository.findByActorUserIdIn(memberIds, 1);
        if (recent.isEmpty()) {
            return null;
        }
        ActivityFeed first = recent.get(0);
        return new LastActivitySummary(
                first.getSummary(),
                first.getActivityType().name(),
                first.getSeverity().name(),
                first.getOccurredAt());
    }
}
