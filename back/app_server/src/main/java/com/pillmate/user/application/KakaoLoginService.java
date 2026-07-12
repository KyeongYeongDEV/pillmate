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
        return login(code, redirectUri, null);
    }

    @Transactional
    public AuthResult login(String code, String redirectUri, Long devUserId) {
        if (kakaoOAuthPort.isConfigured() && code != null && !code.isBlank()) {
            return loginWithKakao(code, redirectUri);
        }
        return devFallback(devUserId);
    }

    private AuthResult loginWithKakao(String code, String redirectUri) {
        KakaoProfile profile = kakaoOAuthPort.exchange(code, redirectUri);
        boolean existing = userRepository.findByProviderAndExternalId(UserProvider.KAKAO, profile.kakaoId()).isPresent();
        User user = upsertUser(profile);
        return toAuthResult(user, !existing, profile);
    }

    private User upsertUser(KakaoProfile profile) {
        return userRepository.findByProviderAndExternalId(UserProvider.KAKAO, profile.kakaoId())
                .map(existing -> reactivateIfWithdrawn(existing, profile))
                .orElseGet(() -> userRepository.save(
                        User.ofOAuth(profile.kakaoId(), UserProvider.KAKAO, profile.nickname(), profile.email())));
    }

    // 탈퇴 계정 재로그인 → 최신 카카오 프로필로 재활성화 후 로그인
    private User reactivateIfWithdrawn(User user, KakaoProfile profile) {
        if (!user.isWithdrawn()) {
            return user;
        }
        user.reactivate(profile.nickname(), profile.email(), profile.profileImageUrl());
        return userRepository.save(user);
    }

    // prod(devFallbackEnabled=false)에서는 devUserId 헤더를 보기 전에 즉시 차단 → 헤더 위조 무력화 (P0)
    private AuthResult devFallback(Long devUserId) {
        if (!devFallbackEnabled) {
            throw new PillmateException(ErrorCode.KAKAO_AUTH_FAILED);
        }
        User user = resolveDevUser(devUserId);
        log.warn("DEV kakao fallback → userId={}", user.getId());
        return toAuthResult(user, false, null);
    }

    // dev 모드에서만: 헤더로 요청된 seed user 가 실재하면 그 user, 아니면 기본 seed(1)
    private User resolveDevUser(Long requestedUserId) {
        if (requestedUserId != null) {
            User requested = userRepository.findById(requestedUserId).orElse(null);
            if (requested != null) {
                return requested;
            }
        }
        return userRepository.findById(DEV_FALLBACK_USER_ID)
                .orElseGet(() -> userRepository.save(User.dummy("dev-seed")));
    }

    private AuthResult toAuthResult(User user, boolean isNewUser, KakaoProfile profile) {
        String token = jwtTokenProvider.issue(user.getId());
        AuthResult.ProfileInfo profileInfo = new AuthResult.ProfileInfo(
                user.getName(), user.getEmail(), profile != null ? profile.profileImageUrl() : null);
        return new AuthResult(token, user.getId(), isNewUser, profileInfo);
    }
}
