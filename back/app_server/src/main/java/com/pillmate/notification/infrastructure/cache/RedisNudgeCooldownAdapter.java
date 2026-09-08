package com.pillmate.notification.infrastructure.cache;

import com.pillmate.notification.application.port.NudgeCooldownPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

// SETNX 기반 쿨다운 — doseLogId+fromUserId 쌍 당 TTL 동안 재요청 차단 (넛지 스팸 방지)
@Component
@RequiredArgsConstructor
public class RedisNudgeCooldownAdapter implements NudgeCooldownPort {

    private static final String KEY_PREFIX = "nudge:";
    private static final String RECIPIENT_CAP_KEY_PREFIX = "nudge-recipient:";
    private static final String GENERAL_KEY_PREFIX = "nudge-general:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public boolean tryAcquire(Long doseLogId, Long fromUserId, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(doseLogId, fromUserId), "1", ttl);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public boolean acquireRecipientCap(Long patientId, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(recipientCapKey(patientId), "1", ttl);
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public boolean tryAcquireGeneral(Long recipientUserId, Long fromUserId, Duration ttl) {
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(generalKey(recipientUserId, fromUserId), "1", ttl);
        return Boolean.TRUE.equals(acquired);
    }

    private String key(Long doseLogId, Long fromUserId) {
        return KEY_PREFIX + doseLogId + ":" + fromUserId;
    }

    private String recipientCapKey(Long patientId) {
        return RECIPIENT_CAP_KEY_PREFIX + patientId;
    }

    private String generalKey(Long recipientUserId, Long fromUserId) {
        return GENERAL_KEY_PREFIX + recipientUserId + ":" + fromUserId;
    }
}
