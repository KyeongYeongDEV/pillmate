package com.pillmate.caregroup.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "memberships")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Membership {

    private static final int NICKNAME_MAX_LENGTH = 20;

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

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MembershipStatus status;

    @Column(name = "left_at")
    private Instant leftAt;

    // 그룹별 별명 — null 이면 표시 시 본인 실제 이름(User.name)으로 폴백 (그룹마다 다르게 설정 가능)
    @Column(length = 20)
    private String nickname;

    public static Membership of(Long careGroupId, Long userId, MemberRole role, Long invitedBy) {
        Membership m = new Membership();
        m.careGroupId = careGroupId;
        m.userId = userId;
        m.role = role;
        m.invitedBy = invitedBy;
        m.joinedAt = Instant.now();
        m.pinned = false;
        m.status = MembershipStatus.ACTIVE;
        m.leftAt = null;
        return m;
    }

    public boolean isAdmin() {
        return this.role == MemberRole.ADMIN;
    }

    public boolean isPinned() {
        return this.pinned;
    }

    public boolean isActive() {
        return this.status == MembershipStatus.ACTIVE;
    }

    public boolean hasLeft() {
        return this.status == MembershipStatus.LEFT;
    }

    public void pin() {
        this.pinned = true;
    }

    public void unpin() {
        this.pinned = false;
    }

    public void leave(Clock clock) {
        if (hasLeft()) {
            return;
        }
        this.status = MembershipStatus.LEFT;
        this.leftAt = Instant.now(clock);
        this.pinned = false;
    }

    // 공백/null 이면 별명을 리셋해 기본값(본인 실제 이름)으로 되돌린다.
    public void updateNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            this.nickname = null;
            return;
        }
        String trimmed = nickname.trim();
        if (trimmed.length() > NICKNAME_MAX_LENGTH) {
            throw new IllegalArgumentException("nickname must be at most " + NICKNAME_MAX_LENGTH + " chars");
        }
        this.nickname = trimmed;
    }
}
