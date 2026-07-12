package com.pillmate.user.domain;

import com.pillmate.user.domain.model.PushProvider;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.model.UserProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("User 도메인 — push token 등록")
class UserTest {

    @Test
    @DisplayName("registerPushToken 시 token + provider 갱신")
    void registerPushToken_updatesFields() {
        User user = User.dummy("alice");

        user.registerPushToken("ExponentPushToken[xxxxxxxx]", PushProvider.EXPO);

        assertThat(user.getExpoPushToken()).isEqualTo("ExponentPushToken[xxxxxxxx]");
        assertThat(user.getPushProvider()).isEqualTo(PushProvider.EXPO);
    }

    @Test
    @DisplayName("registerPushToken 시 token 이 blank 면 IllegalArgumentException")
    void registerPushToken_whenBlank_throws() {
        User user = User.dummy("alice");

        assertThatThrownBy(() -> user.registerPushToken("  ", PushProvider.EXPO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("withdraw 시 PII 익명화 — withdrawnAt 설정, name='탈퇴한 사용자', email/profile/token null, externalId 유지")
    void withdraw_anonymizesPiiAndKeepsExternalId() {
        User user = User.ofOAuth("kakao-123", UserProvider.KAKAO, "홍길동", "hong@example.com");
        user.registerPushToken("ExponentPushToken[abc]", PushProvider.EXPO);
        Instant now = Instant.parse("2026-07-04T00:00:00Z");

        user.withdraw(now);

        assertThat(user.isWithdrawn()).isTrue();
        assertThat(user.getWithdrawnAt()).isEqualTo(now);
        assertThat(user.getName()).isEqualTo("탈퇴한 사용자");
        assertThat(user.getEmail()).isNull();
        assertThat(user.getProfileUrl()).isNull();
        assertThat(user.getExpoPushToken()).isNull();
        assertThat(user.getExternalId()).isEqualTo("kakao-123");
    }

    @Test
    @DisplayName("활성 계정은 isWithdrawn=false")
    void isWithdrawn_falseWhenActive() {
        User user = User.ofOAuth("kakao-123", UserProvider.KAKAO, "홍길동", "hong@example.com");

        assertThat(user.isWithdrawn()).isFalse();
    }

    @Test
    @DisplayName("reactivate 시 재활성화 — withdrawnAt null + 최신 프로필로 갱신")
    void reactivate_restoresAccountWithFreshProfile() {
        User user = User.ofOAuth("kakao-123", UserProvider.KAKAO, "홍길동", "hong@example.com");
        user.withdraw(Instant.parse("2026-07-04T00:00:00Z"));

        user.reactivate("김철수", "kim@example.com", "https://k.kakaocdn/new.jpg");

        assertThat(user.isWithdrawn()).isFalse();
        assertThat(user.getWithdrawnAt()).isNull();
        assertThat(user.getName()).isEqualTo("김철수");
        assertThat(user.getEmail()).isEqualTo("kim@example.com");
        assertThat(user.getProfileUrl()).isEqualTo("https://k.kakaocdn/new.jpg");
    }

    @Test
    @DisplayName("updateName — 유효한 이름(1~20자)으로 변경")
    void updateName_validName_updates() {
        User user = User.ofOAuth("kakao-123", UserProvider.KAKAO, "홍길동", "hong@example.com");

        user.updateName("새이름");

        assertThat(user.getName()).isEqualTo("새이름");
    }

    @Test
    @DisplayName("updateName — null/blank 이면 IllegalArgumentException")
    void updateName_blank_throws() {
        User user = User.ofOAuth("kakao-123", UserProvider.KAKAO, "홍길동", "hong@example.com");

        assertThatThrownBy(() -> user.updateName("   "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> user.updateName(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updateName — 20자 초과면 IllegalArgumentException")
    void updateName_tooLong_throws() {
        User user = User.ofOAuth("kakao-123", UserProvider.KAKAO, "홍길동", "hong@example.com");
        String tooLong = "가".repeat(21);

        assertThatThrownBy(() -> user.updateName(tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("updateName — 정확히 20자는 허용")
    void updateName_exactlyMaxLength_allowed() {
        User user = User.ofOAuth("kakao-123", UserProvider.KAKAO, "홍길동", "hong@example.com");
        String exactly20 = "가".repeat(20);

        user.updateName(exactly20);

        assertThat(user.getName()).isEqualTo(exactly20);
    }
}
