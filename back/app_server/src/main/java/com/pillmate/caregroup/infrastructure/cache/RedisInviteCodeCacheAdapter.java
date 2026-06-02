package com.pillmate.caregroup.infrastructure.cache;

import com.pillmate.caregroup.application.port.InviteCodeCachePort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class RedisInviteCodeCacheAdapter implements InviteCodeCachePort {

    private static final String KEY_PREFIX = "invite_code:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void put(String code, Long groupId, Duration ttl) {
        redisTemplate.opsForValue().set(key(code), String.valueOf(groupId), ttl);
    }

    @Override
    public Optional<Long> findGroupId(String code) {
        String value = redisTemplate.opsForValue().get(key(code));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(value));
    }

    private String key(String code) {
        return KEY_PREFIX + code;
    }
}
