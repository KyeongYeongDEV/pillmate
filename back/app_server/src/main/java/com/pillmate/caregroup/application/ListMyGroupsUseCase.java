package com.pillmate.caregroup.application;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import com.pillmate.caregroup.application.dto.MyGroupSummary;
import com.pillmate.caregroup.application.dto.MyGroupSummary.LastActivitySummary;
import com.pillmate.caregroup.application.dto.MyGroupSummary.MemberPreview;
import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.CareGroupRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
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
        List<MemberPreview> preview = membersPreview(allMembers);
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

    // userId 오름차순으로 정렬한 뒤 앞에서 자른다 — 프론트의 고유색 배정이 "전체 구성원을 userId
    // 오름차순 정렬한 순위"를 쓰므로, 가장 작은 userId 들만 미리보기에 담아야 목록의 색이
    // 상세 화면의 색과 정확히 일치한다(임의 순서로 자르면 순위가 어긋나 색이 달라진다).
    private List<MemberPreview> membersPreview(List<Membership> members) {
        return members.stream()
                .sorted(Comparator.comparing(Membership::getUserId))
                .limit(MEMBERS_PREVIEW_LIMIT)
                .map(m -> {
                    MemberInfo info = resolveMemberInfo(m.getUserId());
                    return new MemberPreview(m.getUserId(), info.name(), info.color());
                })
                .toList();
    }

    private MemberInfo resolveMemberInfo(Long userId) {
        return userRepository.findById(userId)
                .map(u -> new MemberInfo(u.getName(), u.getPreferredColor()))
                .orElse(new MemberInfo("멤버", null));
    }

    private record MemberInfo(String name, String color) {}

    private LastActivitySummary latestActivity(List<Membership> members) {
        // 멤버별 가입(joinedAt) 시점 이후 최신 1건 — 새 그룹은 과거 활동 미노출
        ActivityFeed first = members.stream()
                .flatMap(m -> activityFeedRepository
                        .findByActorSince(m.getUserId(), m.getJoinedAt(), 1).stream())
                .max(java.util.Comparator.comparing(ActivityFeed::getOccurredAt))
                .orElse(null);
        if (first == null) {
            return null;
        }
        return new LastActivitySummary(
                first.getSummary(),
                first.getActivityType().name(),
                first.getSeverity().name(),
                first.getOccurredAt());
    }
}
