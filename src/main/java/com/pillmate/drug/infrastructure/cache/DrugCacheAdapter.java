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

    private static final String KEY_PREFIX = "DRUG:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<DrugDetailResponse> get(Long drugId) {
        String value = redisTemplate.opsForValue().get(key(drugId));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, DrugDetailResponse.class));
        } catch (Exception e) {
            log.warn("Redis 역직렬화 실패 (drugId={}): {}", drugId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(Long drugId, DrugDetailResponse response) {
        try {
            String value = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key(drugId), value, TTL);
        } catch (Exception e) {
            log.warn("Redis 직렬화 실패 (drugId={}): {}", drugId, e.getMessage());
        }
    }

    @Override
    public void evict(Long drugId) {
        redisTemplate.delete(key(drugId));
    }

    private String key(Long drugId) {
        return KEY_PREFIX + drugId;
    }
}
