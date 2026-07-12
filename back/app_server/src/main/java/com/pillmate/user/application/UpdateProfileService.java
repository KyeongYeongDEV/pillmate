package com.pillmate.user.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.user.application.dto.UserProfileResponse;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateProfileService {

    private final UserRepository userRepository;

    @Transactional
    public UserProfileResponse updateName(Long userId, String name) {
        User user = findActiveUser(userId);
        user.updateName(name);
        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    // 탈퇴 계정은 PII 가 익명화된 상태 — 재로그인 없이 이름 변경으로 익명화가 되돌려지는 것을 방지
    private User findActiveUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new PillmateException(ErrorCode.INVALID_AUTH_TOKEN));
        if (user.isWithdrawn()) {
            throw new PillmateException(ErrorCode.ACCOUNT_WITHDRAWN);
        }
        return user;
    }

    private UserProfileResponse toResponse(User user) {
        return new UserProfileResponse(user.getName(), user.getEmail(), user.getProfileUrl());
    }
}
