package com.pillmate.drug.infrastructure.cache;

import com.pillmate.drug.application.port.DrugImageUrlCachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
class DrugImageUrlCacheAdapter implements DrugImageUrlCachePort {

    private static final String KEY_PREFIX = "drugimg:";
    // presign TTL(24h) 만료 전 여유 — 만료된 URL 이 캐시에 남지 않도록 23h
    private static final Duration TTL = Duration.ofHours(23);

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<String> get(String imageS3Key) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key(imageS3Key)));
        } catch (Exception e) {
            log.warn("약 이미지 URL 캐시 조회 실패 (fail-open, key={}): {}", imageS3Key, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String imageS3Key, String url) {
        try {
            redisTemplate.opsForValue().set(key(imageS3Key), url, TTL);
        } catch (Exception e) {
            log.warn("약 이미지 URL 캐시 저장 실패 (fail-open, key={}): {}", imageS3Key, e.getMessage());
        }
    }

    private String key(String imageS3Key) {
        return KEY_PREFIX + imageS3Key;
    }
}
