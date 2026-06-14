package com.pillmate.notification.infrastructure.fcm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("LazyFirebaseMessagingProvider — 자격증명 미준비 graceful")
class LazyFirebaseMessagingProviderTest {

    @Test
    @DisplayName("JSON/Path 둘 다 미설정 → 빈 Optional, 예외/crash 없음")
    void get_noCredentials_returnsEmpty() {
        LazyFirebaseMessagingProvider provider = new LazyFirebaseMessagingProvider("", "");

        assertThatCode(() -> {
            assertThat(provider.get()).isEmpty();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("존재하지 않는 credentials path → 빈 Optional graceful (앱 안 죽음)")
    void get_invalidPath_returnsEmptyGracefully() {
        LazyFirebaseMessagingProvider provider =
                new LazyFirebaseMessagingProvider("", "/nonexistent/firebase-service-account.json");

        assertThat(provider.get()).isEmpty();
    }

    @Test
    @DisplayName("잘못된 JSON 자격증명 → 빈 Optional graceful (초기화 실패 흡수)")
    void get_malformedJson_returnsEmptyGracefully() {
        LazyFirebaseMessagingProvider provider =
                new LazyFirebaseMessagingProvider("{not-a-valid-service-account}", "");

        assertThat(provider.get()).isEmpty();
    }
}
