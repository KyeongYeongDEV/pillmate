package com.pillmate.user.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.JwtTokenProvider;
import com.pillmate.user.application.dto.RefreshTokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 세션 슬라이딩 갱신 — UserContextInterceptor 를 통과한 인증 사용자만 새 토큰 발급
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final JwtTokenProvider jwtTokenProvider;

    public RefreshTokenResponse refresh(Long userId) {
        if (userId == null) {
            throw new PillmateException(ErrorCode.INVALID_AUTH_TOKEN);
        }
        return new RefreshTokenResponse(jwtTokenProvider.issue(userId));
    }
}
