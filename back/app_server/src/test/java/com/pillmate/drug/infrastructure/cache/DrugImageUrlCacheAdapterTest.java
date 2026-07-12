package com.pillmate.drug.infrastructure.cache;

import com.pillmate.drug.application.port.DrugImageUrlCachePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("integration")
@Testcontainers
@DisplayName("DrugImageUrlCacheAdapter — 약 이미지 presigned URL 캐시 (drugimg:{s3key})")
class DrugImageUrlCacheAdapterTest {

    private static final String S3_KEY = "drugs/images/200006427.jpg";
    private static final String URL = "https://s3/drugs/images/200006427.jpg?X-Amz-Signature=fixed";

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate template;
    private DrugImageUrlCachePort adapter;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        template.getConnectionFactory().getConnection().serverCommands().flushDb();
        adapter = new DrugImageUrlCacheAdapter(template);
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    @Test
    @DisplayName("put 후 get — 동일 URL round-trip, drugimg: 키 사용, TTL≈23h")
    void putThenGet_roundTrips() {
        adapter.put(S3_KEY, URL);

        assertThat(adapter.get(S3_KEY)).contains(URL);
        assertThat(template.hasKey("drugimg:" + S3_KEY)).isTrue();
        Long ttl = template.getExpire("drugimg:" + S3_KEY);
        assertThat(ttl).isGreaterThan(22 * 3600L).isLessThanOrEqualTo(23 * 3600L);
    }

    @Test
    @DisplayName("같은 imageS3Key 는 유저 무관 동일 URL 공유")
    void sharedKey_returnsSameUrl() {
        adapter.put(S3_KEY, URL);

        Optional<String> forDrugA = adapter.get(S3_KEY);
        Optional<String> forDrugB = adapter.get(S3_KEY);

        assertThat(forDrugA).isEqualTo(forDrugB).contains(URL);
    }

    @Test
    @DisplayName("미저장 키는 empty")
    void get_whenAbsent_returnsEmpty() {
        assertThat(adapter.get("drugs/images/absent.jpg")).isEmpty();
    }

    @Test
    @DisplayName("Redis 장애 시 get 은 empty(fail-open), put 은 예외 삼킴 — SPOF 방지")
    void redisDown_failsOpen() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.opsForValue()).thenThrow(new RuntimeException("connection refused"));
        DrugImageUrlCachePort brokenAdapter = new DrugImageUrlCacheAdapter(broken);

        assertThat(brokenAdapter.get(S3_KEY)).isEmpty();
        assertThatCode(() -> brokenAdapter.put(S3_KEY, URL)).doesNotThrowAnyException();
    }
}
