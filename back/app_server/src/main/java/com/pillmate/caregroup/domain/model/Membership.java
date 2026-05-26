package com.pillmate.caregroup.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "memberships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "care_group_id", nullable = false)
    private Long careGroupId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Column(name = "invited_by")
    private Long invitedBy;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    public static Membership of(Long careGroupId, Long userId, MemberRole role, Long invitedBy) {
        Membership m = new Membership();
        m.careGroupId = careGroupId;
        m.userId = userId;
        m.role = role;
        m.invitedBy = invitedBy;
        m.joinedAt = Instant.now();
        return m;
    }

    public boolean isAdmin() {
        return this.role == MemberRole.ADMIN;
    }
}
