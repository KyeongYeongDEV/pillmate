package com.pillmate.drug.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.drug.application.dto.DrugDetailResponse;
import com.pillmate.drug.application.port.DrugCachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
class DrugCacheAdapter implements DrugCachePort {

    // 응답 스키마 변경 시 bump — 옛 캐시(stale 필드) 자동 miss 후 TTL 자연 소멸 (v2: main_ingr/className 추가)
    private static final String CACHE_VERSION = "v2";
    private static final String KEY_PREFIX = "DRUG:" + CACHE_VERSION + ":";
    private static final Duration TTL = Duration.ofMinutes(55);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<DrugDetailResponse> get(String kdCode) {
        String value = redisTemplate.opsForValue().get(key(kdCode));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, DrugDetailResponse.class));
        } catch (Exception e) {
            log.warn("Redis 역직렬화 실패 (kdCode={}): {}", kdCode, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String kdCode, DrugDetailResponse response) {
        try {
            String value = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key(kdCode), value, TTL);
        } catch (Exception e) {
            log.warn("Redis 직렬화 실패 (kdCode={}): {}", kdCode, e.getMessage());
        }
    }

    @Override
    public void evict(String kdCode) {
        redisTemplate.delete(key(kdCode));
    }

    private String key(String kdCode) {
        return KEY_PREFIX + kdCode;
    }
}
