package com.pillmate.common.security;

import com.pillmate.common.exception.PillmateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtTokenProvider — 발급/파싱/검증")
class JwtTokenProviderTest {

    private JwtTokenProvider sut;

    @BeforeEach
    void setUp() {
        sut = new JwtTokenProvider(
                "test-secret-key-must-be-32-bytes-or-longer-for-hmac",
                14L);
    }

    @Test
    @DisplayName("issue → parseUserId 라운드트립 성공")
    void issue_parseUserId_roundTrip() {
        String token = sut.issue(42L);
        assertThat(sut.parseUserId(token)).isEqualTo(42L);
    }

    @Test
    @DisplayName("위조된 토큰 → INVALID_AUTH_TOKEN")
    void parse_forgedToken_throwsInvalidAuthToken() {
        assertThatThrownBy(() -> sut.parseUserId("forged.token.here"))
                .isInstanceOf(PillmateException.class);
    }

    @Test
    @DisplayName("만료된 토큰(ttl=0) → INVALID_AUTH_TOKEN")
    void parse_expiredToken_throws() {
        JwtTokenProvider expiredProvider = new JwtTokenProvider(
                "test-secret-key-must-be-32-bytes-or-longer-for-hmac", 0L);
        String token = expiredProvider.issue(1L);
        assertThatThrownBy(() -> sut.parseUserId(token))
                .isInstanceOf(PillmateException.class);
    }

    @Test
    @DisplayName("빈 시크릿 → 기동 즉시 IllegalStateException (프로필 무관 fail-closed)")
    void constructor_blankSecret_throwsIllegalState() {
        assertThatThrownBy(() -> new JwtTokenProvider("", 14L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PILLMATE_JWT_SECRET");
    }

    @Test
    @DisplayName("공백만 있는 시크릿 → 기동 즉시 IllegalStateException")
    void constructor_whitespaceSecret_throwsIllegalState() {
        assertThatThrownBy(() -> new JwtTokenProvider("   ", 14L))
                .isInstanceOf(IllegalStateException.class);
    }
}
