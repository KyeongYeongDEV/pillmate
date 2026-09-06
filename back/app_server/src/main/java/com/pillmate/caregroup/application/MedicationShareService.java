package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.ShareSettingUpdateResponse;
import com.pillmate.caregroup.application.dto.ShareSettingView;
import com.pillmate.caregroup.domain.model.MedicationShareGrant;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MedicationShareGrantRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 알약 정보(L2) 공유 권한 판정/설정.
 * L1(복약 여부)은 CareGroupGuard.requirePatientAccessible 로 별도 공개 — 본 서비스와 무관.
 */
@Service
@RequiredArgsConstructor
public class MedicationShareService {

    private final MembershipRepository membershipRepository;
    private final MedicationShareGrantRepository medicationShareGrantRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<ShareSettingView> getShareSettings(Long groupId, Long ownerUserId) {
        requireActiveMember(groupId, ownerUserId);
        Set<Long> sharedViewerIds = findSharedViewerIds(groupId, ownerUserId);
        return otherActiveMembers(groupId, ownerUserId).stream()
                .map(member -> toShareSettingView(member, sharedViewerIds))
                .toList();
    }

    @Transactional
    public ShareSettingUpdateResponse updateShareSetting(
            Long groupId, Long ownerUserId, Long viewerUserId, boolean enabled) {
        requireActiveMember(groupId, ownerUserId);
        requireValidViewer(groupId, ownerUserId, viewerUserId);

        if (enabled) {
            grantIfAbsent(groupId, ownerUserId, viewerUserId);
        } else {
            medicationShareGrantRepository.deleteByCareGroupIdAndOwnerUserIdAndViewerUserId(
                    groupId, ownerUserId, viewerUserId);
        }
        return new ShareSettingUpdateResponse(viewerUserId, enabled);
    }

    /**
     * L2(알약 정보) 열람 가능 여부 — 본인이거나 owner 가 명시적으로 공유를 허용한 경우만 true.
     */
    @Transactional(readOnly = true)
    public boolean canViewMedicationDetail(Long ownerUserId, Long viewerUserId) {
        if (ownerUserId == null || viewerUserId == null) {
            return false;
        }
        if (ownerUserId.equals(viewerUserId)) {
            return true;
        }
        return medicationShareGrantRepository.existsByOwnerUserIdAndViewerUserId(ownerUserId, viewerUserId);
    }

    private void requireActiveMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByCareGroupIdAndUserId(groupId, userId)) {
            throw new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        }
    }

    private void requireValidViewer(Long groupId, Long ownerUserId, Long viewerUserId) {
        if (ownerUserId.equals(viewerUserId) || !membershipRepository.existsByCareGroupIdAndUserId(groupId, viewerUserId)) {
            throw new PillmateException(ErrorCode.MEDICATION_SHARE_INVALID_TARGET);
        }
    }

    private void grantIfAbsent(Long groupId, Long ownerUserId, Long viewerUserId) {
        boolean alreadyGranted = medicationShareGrantRepository
                .findByCareGroupIdAndOwnerUserIdAndViewerUserId(groupId, ownerUserId, viewerUserId)
                .isPresent();
        if (!alreadyGranted) {
            medicationShareGrantRepository.save(MedicationShareGrant.of(groupId, ownerUserId, viewerUserId, clock));
        }
    }

    private List<Membership> otherActiveMembers(Long groupId, Long ownerUserId) {
        return membershipRepository.findByCareGroupId(groupId).stream()
                .filter(member -> !member.getUserId().equals(ownerUserId))
                .toList();
    }

    private Set<Long> findSharedViewerIds(Long groupId, Long ownerUserId) {
        return medicationShareGrantRepository.findByCareGroupIdAndOwnerUserId(groupId, ownerUserId).stream()
                .map(MedicationShareGrant::getViewerUserId)
                .collect(Collectors.toSet());
    }

    private ShareSettingView toShareSettingView(Membership member, Set<Long> sharedViewerIds) {
        String name = userRepository.findById(member.getUserId())
                .map(user -> user.getName())
                .orElse("멤버");
        return new ShareSettingView(
                member.getUserId(), name, member.getRole().name(), sharedViewerIds.contains(member.getUserId()));
    }
}
