package com.pillmate.activity.infrastructure.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.activity.application.dto.ActivityFeedItem;
import com.pillmate.activity.application.port.ActivityFeedCachePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 그룹 활동 피드 캐시 — 버전 키 무효화.
 *
 * 피드는 viewer 자신 제외가 있어 viewer 별 내용이 다르므로 데이터 키에 viewerId 포함.
 * 무효화는 그룹 버전 키 INCR 1회 — viewer/limit 조합 열거·와일드카드 삭제(SCAN/KEYS) 없이
 * 그룹 전체 캐시를 즉시 스킵시킨다. 구버전 데이터 키는 15초 TTL 로 자연 소멸 (cost-aware: 영구 키 금지).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisActivityFeedCacheAdapter implements ActivityFeedCachePort {

    static final Duration FEED_TTL = Duration.ofSeconds(15);
    static final Duration VERSION_TTL = Duration.ofHours(1);
    private static final TypeReference<List<ActivityFeedItem>> ITEMS_TYPE = new TypeReference<>() {};

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<List<ActivityFeedItem>> getGroupFeed(Long groupId, Long viewerId, int limit) {
        try {
            String raw = redisTemplate.opsForValue().get(dataKey(groupId, viewerId, limit));
            if (raw == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(raw, ITEMS_TYPE));
        } catch (Exception e) {
            log.warn("feed cache get 실패 — DB fallback: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    @Override
    public void putGroupFeed(Long groupId, Long viewerId, int limit, List<ActivityFeedItem> items) {
        try {
            String raw = objectMapper.writeValueAsString(items);
            redisTemplate.opsForValue().set(dataKey(groupId, viewerId, limit), raw, FEED_TTL);
        } catch (Exception e) {
            log.warn("feed cache put 실패 — skip: {}", e.getClass().getSimpleName());
        }
    }

    @Override
    public void evictGroup(Long groupId) {
        try {
            String versionKey = versionKey(groupId);
            redisTemplate.opsForValue().increment(versionKey);
            redisTemplate.expire(versionKey, VERSION_TTL);
        } catch (Exception e) {
            log.warn("feed cache evict 실패 — TTL(15s) backstop 에 위임: {}", e.getClass().getSimpleName());
        }
    }

    private String dataKey(Long groupId, Long viewerId, int limit) {
        return "feed:group:" + groupId + ":v" + currentVersion(groupId) + ":" + viewerId + ":" + limit;
    }

    private String currentVersion(Long groupId) {
        String version = redisTemplate.opsForValue().get(versionKey(groupId));
        return version != null ? version : "0";
    }

    private String versionKey(Long groupId) {
        return "feed:group:" + groupId + ":ver";
    }
}
