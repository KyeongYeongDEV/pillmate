package com.pillmate.user.application;

import com.pillmate.user.domain.model.PushProvider;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterPushTokenService {

    private final UserRepository userRepository;

    @Transactional
    public void register(Long userId, String token, PushProvider provider) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        user.registerPushToken(token, provider);
        userRepository.save(user);
    }
}
