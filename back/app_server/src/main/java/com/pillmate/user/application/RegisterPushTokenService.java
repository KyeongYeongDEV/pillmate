package com.pillmate.user.application;

import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.notification.application.port.RecipientCachePort;
import com.pillmate.user.domain.model.PushProvider;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegisterPushTokenService {

    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final RecipientCachePort recipientCachePort;

    @Transactional
    public void register(Long userId, String token, PushProvider provider) {
        List<Long> dispossessed = clearTokenFromOtherUsers(token, userId);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found: " + userId));
        user.registerPushToken(token, provider);
        userRepository.save(user);
        log.info("DeviceToken saved userId={} provider={}", userId, provider);

        evictUserGroupRecipients(userId);
        dispossessed.forEach(this::evictUserGroupRecipients);
    }

    // 같은 기기 토큰을 이전에 보유한 다른 유저에서 해제 (1기기 1유저). 해제된 userId 목록 반환.
    private List<Long> clearTokenFromOtherUsers(String token, Long keepUserId) {
        List<User> others = userRepository.findByExpoPushTokenAndIdNot(token, keepUserId);
        others.forEach(User::clearPushToken);
        others.forEach(userRepository::save);
        return others.stream().map(User::getId).toList();
    }

    private void evictUserGroupRecipients(Long userId) {
        membershipRepository.findByUserId(userId).stream()
                .map(Membership::getCareGroupId)
                .distinct()
                .forEach(recipientCachePort::evict);
    }
}
