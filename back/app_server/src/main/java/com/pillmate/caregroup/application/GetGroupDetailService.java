package com.pillmate.caregroup.application;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import com.pillmate.caregroup.application.dto.ActivityView;
import com.pillmate.caregroup.application.dto.GroupDetailResponse;
import com.pillmate.caregroup.application.dto.InviteCodeView;
import com.pillmate.caregroup.application.dto.MemberView;
import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.model.InviteCode;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.CareGroupRepository;
import com.pillmate.caregroup.domain.repository.InviteCodeRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
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
public class GetGroupDetailService implements GetGroupDetailUseCase {

    private static final int RECENT_ACTIVITY_LIMIT = 20;

    private final CareGroupRepository careGroupRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final ActivityFeedRepository activityFeedRepository;

    @Override
    @Transactional(readOnly = true)
    public GroupDetailResponse detail(Long groupId, Long userId) {
        requireMember(groupId, userId);
        CareGroup group = loadGroup(groupId);
        List<Membership> memberships = membershipRepository.findByCareGroupId(groupId);
        Map<Long, String> nameById = loadNameMap(memberships);
        return new GroupDetailResponse(
                groupId,
                group.getName(),
                memberships.size(),
                toMemberViews(memberships, nameById),
                toInviteCodeView(groupId),
                toRecentActivities(memberships, nameById)
        );
    }

    private void requireMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByCareGroupIdAndUserId(groupId, userId)) {
            throw new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        }
    }

    private CareGroup loadGroup(Long groupId) {
        return careGroupRepository.findById(groupId)
                .orElseThrow(() -> new PillmateException(ErrorCode.GROUP_NOT_FOUND));
    }

    private Map<Long, String> loadNameMap(List<Membership> memberships) {
        return memberships.stream()
                .map(Membership::getUserId)
                .collect(Collectors.toMap(
                        Function.identity(),
                        id -> userRepository.findById(id).map(u -> u.getName()).orElse("멤버"),
                        (a, b) -> a));
    }

    private List<MemberView> toMemberViews(List<Membership> memberships, Map<Long, String> nameById) {
        return memberships.stream()
                .map(m -> new MemberView(
                        m.getUserId(),
                        nameById.getOrDefault(m.getUserId(), "멤버"),
                        m.getRole().name()))
                .toList();
    }

    private InviteCodeView toInviteCodeView(Long groupId) {
        return inviteCodeRepository.findActiveByCareGroupId(groupId)
                .map(this::toInviteCodeView)
                .orElse(null);
    }

    private InviteCodeView toInviteCodeView(InviteCode code) {
        return new InviteCodeView(code.getCode(), code.getExpiresAt());
    }

    private List<ActivityView> toRecentActivities(List<Membership> memberships, Map<Long, String> nameById) {
        List<Long> memberIds = memberships.stream().map(Membership::getUserId).toList();
        List<ActivityFeed> feeds = activityFeedRepository.findByActorUserIdIn(memberIds, RECENT_ACTIVITY_LIMIT);
        return feeds.stream()
                .map(f -> new ActivityView(
                        nameById.getOrDefault(f.getActorUserId(), "멤버"),
                        f.getActivityType().name(),
                        f.getSummary(),
                        f.getOccurredAt()))
                .toList();
    }
}
