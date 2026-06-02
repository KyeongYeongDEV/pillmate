package com.pillmate.caregroup.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "invite_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "care_group_id", nullable = false)
    private Long careGroupId;

    @Column(unique = true, nullable = false, length = 6)
    private String code;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static InviteCode generate(Long careGroupId, Long createdBy) {
        InviteCode ic = new InviteCode();
        ic.careGroupId = careGroupId;
        ic.createdBy = createdBy;
        ic.code = UUID.randomUUID().toString().replaceAll("[^A-Z0-9]", "")
                      .toUpperCase().substring(0, 6);
        ic.expiresAt = Instant.now().plus(INVITE_CODE_TTL_MINUTES, ChronoUnit.MINUTES);
        ic.createdAt = Instant.now();
        return ic;
    }

    private static final int INVITE_CODE_TTL_MINUTES = 1;

    public static InviteCode ofExpired(String code, Long careGroupId, Long createdBy) {
        InviteCode ic = new InviteCode();
        ic.careGroupId = careGroupId;
        ic.createdBy = createdBy;
        ic.code = code;
        ic.expiresAt = Instant.now().minus(1, ChronoUnit.HOURS);
        ic.createdAt = Instant.now().minus(25, ChronoUnit.HOURS);
        return ic;
    }

    public boolean isExpired() {
        return Instant.now().isAfter(this.expiresAt);
    }

    public boolean isUsable() {
        return !isExpired() && this.usedAt == null;
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }

    public void consume() {
        if (isExpired()) {
            throw new IllegalStateException("InviteCode expired");
        }
        if (this.usedAt != null) {
            throw new IllegalStateException("InviteCode already used");
        }
        this.usedAt = Instant.now();
    }
}
