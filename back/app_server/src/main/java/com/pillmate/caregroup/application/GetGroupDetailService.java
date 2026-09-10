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

import java.util.Comparator;
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
        Map<Long, UserInfo> userInfoById = loadUserInfoMap(memberships);
        return new GroupDetailResponse(
                groupId,
                group.getName(),
                memberships.size(),
                toMemberViews(memberships, userInfoById),
                toInviteCodeView(groupId),
                toRecentActivities(memberships, userInfoById)
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

    private Map<Long, UserInfo> loadUserInfoMap(List<Membership> memberships) {
        return memberships.stream()
                .map(Membership::getUserId)
                .collect(Collectors.toMap(
                        Function.identity(),
                        id -> userRepository.findById(id)
                                .map(u -> new UserInfo(u.getName(), u.getPreferredColor()))
                                .orElse(new UserInfo("멤버", null)),
                        (a, b) -> a));
    }

    private List<MemberView> toMemberViews(List<Membership> memberships, Map<Long, UserInfo> userInfoById) {
        return memberships.stream()
                .map(m -> {
                    UserInfo info = userInfoById.getOrDefault(m.getUserId(), new UserInfo("멤버", null));
                    return new MemberView(m.getUserId(), info.name(), m.getRole().name(), info.color());
                })
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

    private List<ActivityView> toRecentActivities(List<Membership> memberships, Map<Long, UserInfo> userInfoById) {
        // 멤버별 가입 시점(joinedAt) 이후 활동만 합집합 → 새 그룹은 과거 활동 미노출
        List<ActivityFeed> feeds = memberships.stream()
                .flatMap(m -> activityFeedRepository
                        .findByActorSince(m.getUserId(), m.getJoinedAt(), RECENT_ACTIVITY_LIMIT).stream())
                .sorted(Comparator.comparing(ActivityFeed::getOccurredAt).reversed())
                .limit(RECENT_ACTIVITY_LIMIT)
                .toList();
        return feeds.stream()
                .map(f -> new ActivityView(
                        userInfoById.getOrDefault(f.getActorUserId(), new UserInfo("멤버", null)).name(),
                        f.getActivityType().name(),
                        f.getSummary(),
                        f.getOccurredAt()))
                .toList();
    }

    private record UserInfo(String name, String color) {}
}
