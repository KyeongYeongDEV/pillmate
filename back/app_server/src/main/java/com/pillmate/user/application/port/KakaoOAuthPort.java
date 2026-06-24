package com.pillmate.user.application.port;

public interface KakaoOAuthPort {
    boolean isConfigured();
    KakaoProfile exchange(String code, String redirectUri);
}
