package com.pillmate.user.domain;

import com.pillmate.user.domain.model.PushProvider;
import com.pillmate.user.domain.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
