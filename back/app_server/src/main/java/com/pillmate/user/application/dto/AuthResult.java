package com.pillmate.user.application.dto;

public record AuthResult(String token, Long userId, boolean isNewUser, ProfileInfo profile) {
    public record ProfileInfo(String name, String email, String profileUrl) {}
}
