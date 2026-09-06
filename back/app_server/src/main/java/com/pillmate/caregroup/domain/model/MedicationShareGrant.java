package com.pillmate.caregroup.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.Instant;

/**
 * owner 가 특정 케어그룹 안에서 viewer 에게 알약 정보(L2) 열람을 허용한 관계.
 * L1(복약 여부)은 본 엔티티와 무관 — CareGroupGuard.requirePatientAccessible 로 별도 공개.
 */
@Entity
@Table(name = "medication_share_grants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MedicationShareGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "care_group_id", nullable = false)
    private Long careGroupId;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "viewer_user_id", nullable = false)
    private Long viewerUserId;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    public static MedicationShareGrant of(Long careGroupId, Long ownerUserId, Long viewerUserId, Clock clock) {
        MedicationShareGrant grant = new MedicationShareGrant();
        grant.careGroupId = careGroupId;
        grant.ownerUserId = ownerUserId;
        grant.viewerUserId = viewerUserId;
        grant.grantedAt = Instant.now(clock);
        return grant;
    }
}
