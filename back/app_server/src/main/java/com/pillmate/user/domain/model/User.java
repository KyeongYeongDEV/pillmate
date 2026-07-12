package com.pillmate.user.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    private static final String WITHDRAWN_NAME = "탈퇴한 사용자";
    private static final int NAME_MIN_LENGTH = 1;
    private static final int NAME_MAX_LENGTH = 20;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", unique = true, length = 100)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private UserProvider provider;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String email;

    @Column(name = "profile_url", length = 500)
    private String profileUrl;

    @Column(name = "expo_push_token", length = 256)
    private String expoPushToken;

    @Enumerated(EnumType.STRING)
    @Column(name = "push_provider", nullable = false, length = 20)
    private PushProvider pushProvider = PushProvider.EXPO;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "withdrawn_at")
    private Instant withdrawnAt;

    public static User dummy(String name) {
        User user = new User();
        user.provider = UserProvider.DUMMY;
        user.name = name;
        user.pushProvider = PushProvider.EXPO;
        user.createdAt = Instant.now();
        user.updatedAt = Instant.now();
        return user;
    }

    public static User ofOAuth(String externalId, UserProvider provider, String name, String email) {
        User user = new User();
        user.externalId = externalId;
        user.provider = provider;
        user.name = name;
        user.email = email;
        user.pushProvider = PushProvider.EXPO;
        user.createdAt = Instant.now();
        user.updatedAt = Instant.now();
        return user;
    }

    public void registerPushToken(String token, PushProvider provider) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token must not be blank");
        }
        this.expoPushToken = token;
        this.pushProvider = provider;
        this.updatedAt = Instant.now();
    }

    // 같은 기기 토큰이 다른 유저로 재로그인될 때 이전 소유자에서 해제 (1기기 1유저 위생)
    public void clearPushToken() {
        this.expoPushToken = null;
        this.updatedAt = Instant.now();
    }

    // 회원탈퇴 — soft delete + PII 익명화(개인정보보호법 보관최소화). externalId 는 재활성화 매칭용 유지.
    public void withdraw(Instant now) {
        this.withdrawnAt = now;
        this.name = WITHDRAWN_NAME;
        this.email = null;
        this.profileUrl = null;
        this.expoPushToken = null;
        this.updatedAt = now;
    }

    public boolean isWithdrawn() {
        return withdrawnAt != null;
    }

    // 닉네임 변경 — public setter 금지, 도메인 메서드로 길이 불변식(1~20자) 보장
    public void updateName(String name) {
        requireValidName(name);
        this.name = name;
        this.updatedAt = Instant.now();
    }

    private void requireValidName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (name.length() < NAME_MIN_LENGTH || name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "name length must be between " + NAME_MIN_LENGTH + " and " + NAME_MAX_LENGTH);
        }
    }

    // 같은 provider+externalId 재로그인 시 계정 부활 — 최신 프로필로 갱신
    public void reactivate(String name, String email, String profileUrl) {
        this.withdrawnAt = null;
        this.name = name;
        this.email = email;
        this.profileUrl = profileUrl;
        this.updatedAt = Instant.now();
    }
}
