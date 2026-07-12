package com.pillmate.notification.infrastructure.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.notification.application.port.RecipientCachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

// 그룹 수신자+토큰 캐시 — 멤버십/토큰은 저빈도 변경, join/leave/토큰등록 시 명시 evict + 5분 TTL backstop
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRecipientCacheAdapter implements RecipientCachePort {

    static final Duration RECIPIENTS_TTL = Duration.ofMinutes(5);
    private static final TypeReference<List<CachedRecipient>> RECIPIENTS_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<List<CachedRecipient>> get(Long groupId) {
        try {
            String raw = redisTemplate.opsForValue().get(key(groupId));
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, RECIPIENTS_TYPE));
        } catch (Exception e) {
            log.warn("recipient cache get 실패 — DB fallback: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void put(Long groupId, List<CachedRecipient> recipients) {
        try {
            redisTemplate.opsForValue().set(key(groupId), objectMapper.writeValueAsString(recipients), RECIPIENTS_TTL);
        } catch (Exception e) {
            log.warn("recipient cache put 실패 — skip: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void evict(Long groupId) {
        try {
            redisTemplate.delete(key(groupId));
        } catch (Exception e) {
            log.warn("recipient cache evict 실패 — TTL(5m) backstop 에 위임: {}", e.getClass().getSimpleName());
        }
    }

    private String key(Long groupId) {
        return "group:" + groupId + ":recipients";
    }
}
