package com.pillmate.user.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.JwtTokenProvider;
import com.pillmate.user.application.dto.AuthResult;
import com.pillmate.user.application.port.KakaoOAuthPort;
import com.pillmate.user.application.port.KakaoProfile;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.model.UserProvider;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoLoginService {

    private static final long DEV_FALLBACK_USER_ID = 1L;

    private final UserRepository userRepository;
    private final KakaoOAuthPort kakaoOAuthPort;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${pillmate.auth.dev-fallback-enabled:false}")
    private boolean devFallbackEnabled;

    @Transactional
    public AuthResult login(String code, String redirectUri) {
        if (kakaoOAuthPort.isConfigured() && code != null && !code.isBlank()) {
            return loginWithKakao(code, redirectUri);
        }
        return devFallback();
    }

    private AuthResult loginWithKakao(String code, String redirectUri) {
        KakaoProfile profile = kakaoOAuthPort.exchange(code, redirectUri);
        boolean existing = userRepository.findByProviderAndExternalId(UserProvider.KAKAO, profile.kakaoId()).isPresent();
        User user = upsertUser(profile);
        return toAuthResult(user, !existing, profile);
    }

    private User upsertUser(KakaoProfile profile) {
        return userRepository.findByProviderAndExternalId(UserProvider.KAKAO, profile.kakaoId())
                .orElseGet(() -> userRepository.save(
                        User.ofOAuth(profile.kakaoId(), UserProvider.KAKAO, profile.nickname(), profile.email())));
    }

    private AuthResult devFallback() {
        if (!devFallbackEnabled) {
            throw new PillmateException(ErrorCode.KAKAO_AUTH_FAILED);
        }
        log.warn("DEV kakao fallback → userId=1");
        User user = userRepository.findById(DEV_FALLBACK_USER_ID)
                .orElseGet(() -> userRepository.save(User.dummy("dev-seed")));
        return toAuthResult(user, false, null);
    }

    private AuthResult toAuthResult(User user, boolean isNewUser, KakaoProfile profile) {
        String token = jwtTokenProvider.issue(user.getId());
        AuthResult.ProfileInfo profileInfo = new AuthResult.ProfileInfo(
                user.getName(), user.getEmail(), profile != null ? profile.profileImageUrl() : null);
        return new AuthResult(token, user.getId(), isNewUser, profileInfo);
    }
}
