package com.pillmate.user.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.JwtTokenProvider;
import com.pillmate.user.application.dto.RefreshTokenResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("RefreshTokenService — 세션 슬라이딩 갱신")
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock JwtTokenProvider jwtTokenProvider;
    @InjectMocks RefreshTokenService sut;

    @Test
    @DisplayName("인증된 userId → 새 JWT 발급")
    void refresh_authenticatedUser_issuesNewToken() {
        given(jwtTokenProvider.issue(5L)).willReturn("new.jwt.token");

        RefreshTokenResponse result = sut.refresh(5L);

        assertThat(result.token()).isEqualTo("new.jwt.token");
        verify(jwtTokenProvider).issue(5L);
    }

    @Test
    @DisplayName("userId null(미인증) → INVALID_AUTH_TOKEN(401), 토큰 발급 없음")
    void refresh_noUserId_throwsInvalidAuthToken() {
        assertThatThrownBy(() -> sut.refresh(null))
                .isInstanceOf(PillmateException.class)
                .satisfies(e -> assertThat(((PillmateException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_AUTH_TOKEN));

        verify(jwtTokenProvider, never()).issue(org.mockito.ArgumentMatchers.any());
    }
}
