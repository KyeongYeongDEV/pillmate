package com.pillmate.caregroup.application;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import com.pillmate.activity.domain.repository.ActivityPraiseRepository;
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
import java.util.Set;
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
    private final ActivityPraiseRepository activityPraiseRepository;

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
                toRecentActivities(userId, memberships, userInfoById)
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

    // 그룹별 별명(Membership.nickname) 이 설정돼 있으면 실제 이름 대신 그걸로 표시 — 멤버 목록·활동 피드 공통.
    private Map<Long, UserInfo> loadUserInfoMap(List<Membership> memberships) {
        return memberships.stream()
                .collect(Collectors.toMap(
                        Membership::getUserId,
                        m -> userRepository.findById(m.getUserId())
                                .map(u -> new UserInfo(resolveDisplayName(m, u.getName()), u.getPreferredColor()))
                                .orElse(new UserInfo(resolveDisplayName(m, "멤버"), null)),
                        (a, b) -> a));
    }

    private String resolveDisplayName(Membership membership, String fallbackName) {
        String nickname = membership.getNickname();
        return (nickname != null && !nickname.isBlank()) ? nickname : fallbackName;
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

    private List<ActivityView> toRecentActivities(Long viewerId, List<Membership> memberships, Map<Long, UserInfo> userInfoById) {
        // 멤버별 가입 시점(joinedAt) 이후 활동만 합집합 → 새 그룹은 과거 활동 미노출
        List<ActivityFeed> feeds = memberships.stream()
                .flatMap(m -> activityFeedRepository
                        .findByActorSince(m.getUserId(), m.getJoinedAt(), RECENT_ACTIVITY_LIMIT).stream())
                .sorted(Comparator.comparing(ActivityFeed::getOccurredAt).reversed())
                .limit(RECENT_ACTIVITY_LIMIT)
                .toList();
        // 칭찬 여부가 서버 진실源이라 어느 화면(홈/그룹상세/전체보기)에서 조회해도 "칭찬함" 이 일관되게 보여야 함
        Set<Long> praisedIds = activityPraiseRepository.findPraisedActivityFeedIds(
                viewerId, feeds.stream().map(ActivityFeed::getId).filter(java.util.Objects::nonNull).toList());
        return feeds.stream()
                .map(f -> new ActivityView(
                        f.getId(),
                        userInfoById.getOrDefault(f.getActorUserId(), new UserInfo("멤버", null)).name(),
                        f.getActivityType().name(),
                        f.getSummary(),
                        f.getOccurredAt(),
                        f.getId() != null && praisedIds.contains(f.getId())))
                .toList();
    }

    private record UserInfo(String name, String color) {}
}
