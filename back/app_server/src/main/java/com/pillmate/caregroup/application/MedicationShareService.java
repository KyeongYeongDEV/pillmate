package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.ShareSettingsView;
import com.pillmate.caregroup.application.dto.ShareSettingView;
import com.pillmate.caregroup.application.dto.ShareablePrescriptionView;
import com.pillmate.caregroup.domain.model.MedicationShareGrant;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MedicationShareGrantRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.prescription.application.PrescriptionViewAssembler;
import com.pillmate.prescription.application.PrescriptionViewAssembler.PeriodRange;
import com.pillmate.prescription.application.port.PrescriptionPeriodPort;
import com.pillmate.prescription.application.port.PrescriptionPeriodPort.PeriodStats;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.model.PrescriptionStatus;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 알약 정보(L2) 공유 설정 — 구성원축(누구에게)과 약봉투축(어느 약봉투를) 두 축의 AND 로 최종 열람을 판정한다.
 * L1(복약 여부)은 CareGroupGuard.requirePatientAccessible 로 별도 공개 — 본 서비스와 무관.
 */
@Service
@RequiredArgsConstructor
public class MedicationShareService {

    private final PrescriptionRepository prescriptionRepository;
    private final MembershipRepository membershipRepository;
    private final MedicationShareGrantRepository medicationShareGrantRepository;
    private final UserRepository userRepository;
    private final PrescriptionViewAssembler assembler;
    private final PrescriptionPeriodPort prescriptionPeriodPort;
    private final Clock clock;

    // 공유 설정 화면 한 번의 조회 — 구성원별 공유 여부 + 내 약봉투(ONGOING) 전체의 공유 여부.
    @Transactional(readOnly = true)
    public ShareSettingsView getShareSettings(Long groupId, Long ownerUserId) {
        requireActiveMember(groupId, ownerUserId);
        return new ShareSettingsView(
                toMemberShareSettings(groupId, ownerUserId),
                toShareablePrescriptions(ownerUserId),
                resolveMyColor(ownerUserId));
    }

    private String resolveMyColor(Long ownerUserId) {
        return userRepository.findById(ownerUserId)
                .map(user -> user.getPreferredColor())
                .orElse(null);
    }

    // 구성원축 토글 — owner 가 groupId 안에서 viewer 에게 알약 정보 열람을 허용/회수.
    @Transactional
    public void updateMemberShare(Long groupId, Long ownerUserId, Long viewerUserId, boolean enabled) {
        requireActiveMember(groupId, ownerUserId);
        requireValidViewer(groupId, ownerUserId, viewerUserId);
        if (enabled) {
            grantIfAbsent(groupId, ownerUserId, viewerUserId);
        } else {
            medicationShareGrantRepository.deleteByCareGroupIdAndOwnerUserIdAndViewerUserId(
                    groupId, ownerUserId, viewerUserId);
        }
    }

    // 약봉투축 토글 — 소유자 본인만 확인한다. 그룹 소속 검증은 하지 않는다: 이 약봉투는
    // "공유 대상으로 삼겠다"는 소유자의 의사표시일 뿐이고, 실제 크로스그룹 차단은 구성원축
    // (medication_share_grants 가 care_group_id 로 스코프됨)에서 이미 보장되기 때문이다.
    @Transactional
    public void updatePrescriptionShare(Long groupId, Long ownerUserId, Long prescriptionId, boolean enabled) {
        requireActiveMember(groupId, ownerUserId);
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new PillmateException(ErrorCode.PRESCRIPTION_NOT_FOUND));
        requireOwnPrescription(prescription, ownerUserId);
        if (enabled) {
            prescription.shareWithGroup();
        } else {
            prescription.unshareFromGroup();
        }
        prescriptionRepository.save(prescription);
    }

    // 구성원축 단독 판정 — 본인이거나, owner 가 이 groupId 안에서 viewer 에게 명시적으로 공유를
    // 허용했고 owner·viewer 둘 다 그 그룹 ACTIVE 멤버인 경우만 true(크로스그룹/탈퇴 잔존 차단).
    @Transactional(readOnly = true)
    public boolean isMemberGranted(Long careGroupId, Long ownerUserId, Long viewerUserId) {
        if (ownerUserId == null || viewerUserId == null) {
            return false;
        }
        if (ownerUserId.equals(viewerUserId)) {
            return true;
        }
        if (careGroupId == null || !isActiveMember(careGroupId, ownerUserId) || !isActiveMember(careGroupId, viewerUserId)) {
            return false;
        }
        return medicationShareGrantRepository.existsByCareGroupIdAndOwnerUserIdAndViewerUserId(
                careGroupId, ownerUserId, viewerUserId);
    }

    private List<ShareSettingView> toMemberShareSettings(Long groupId, Long ownerUserId) {
        Set<Long> sharedViewerIds = findSharedViewerIds(groupId, ownerUserId);
        return otherActiveMembers(groupId, ownerUserId).stream()
                .map(member -> toShareSettingView(member, sharedViewerIds))
                .toList();
    }

    private List<ShareablePrescriptionView> toShareablePrescriptions(Long ownerUserId) {
        List<Prescription> mine = prescriptionRepository.findAllByPatientId(ownerUserId);
        Map<Long, PeriodStats> statsMap = prescriptionPeriodPort
                .fetchStatsByPrescriptionIds(mine.stream().map(Prescription::getId).toList());
        LocalDate today = LocalDate.now(clock);
        return mine.stream()
                .map(p -> toShareableView(p, statsMap.get(p.getId()), today))
                .filter(v -> v.status() == PrescriptionStatus.ONGOING)
                .toList();
    }

    private ShareablePrescriptionView toShareableView(Prescription p, PeriodStats stats, LocalDate today) {
        PeriodRange period = assembler.resolvePeriod(p.getPrescribedAt(), stats);
        PrescriptionStatus status = assembler.resolveStatus(today, period.end());
        return new ShareablePrescriptionView(
                p.getId(), p.getLabel(), p.getPrescribedAt(), p.isSharedWithGroup(), status);
    }

    private void requireOwnPrescription(Prescription prescription, Long ownerUserId) {
        if (!prescription.getPatientId().equals(ownerUserId)) {
            throw new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED);
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

    private boolean isActiveMember(Long groupId, Long userId) {
        return membershipRepository.existsByCareGroupIdAndUserId(groupId, userId);
    }

    private void requireActiveMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByCareGroupIdAndUserId(groupId, userId)) {
            throw new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        }
    }
}
