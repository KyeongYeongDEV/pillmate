package com.pillmate.drug.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.drug.application.dto.DrugDetailResponse;
import com.pillmate.drug.application.port.DrugCachePort;
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

@Tag("integration")
@Testcontainers
@DisplayName("DrugCacheAdapter — 약 상세 캐시 (v2 키)")
class DrugCacheAdapterTest {

    private static final String KD_CODE = "200006427";

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    private LettuceConnectionFactory connectionFactory;
    private StringRedisTemplate template;
    private DrugCachePort adapter;

    @BeforeEach
    void setUp() {
        connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        template = new StringRedisTemplate(connectionFactory);
        template.afterPropertiesSet();
        template.getConnectionFactory().getConnection().serverCommands().flushDb();
        adapter = new DrugCacheAdapter(template, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        connectionFactory.destroy();
    }

    private DrugDetailResponse response() {
        return new DrugDetailResponse(1L, KD_CODE, "타이레놀", "아세트아미노펜", "해열",
                "1일 3회", "졸음", "정제", "한국얀센", "식품의약품안전처", null, "해열·진통·소염제");
    }

    @Test
    @DisplayName("put 후 get — className 포함 round-trip")
    void putThenGet_roundTripsWithClassName() {
        adapter.put(KD_CODE, response());

        Optional<DrugDetailResponse> cached = adapter.get(KD_CODE);

        assertThat(cached).isPresent();
        assertThat(cached.get().ingredient()).isEqualTo("아세트아미노펜");
        assertThat(cached.get().className()).isEqualTo("해열·진통·소염제");
    }

    @Test
    @DisplayName("v2 키 사용 — DRUG:v2:{kdCode}, TTL 55분 이하 (옛 DRUG:{kdCode} 아님)")
    void usesV2Key() {
        adapter.put(KD_CODE, response());

        assertThat(template.hasKey("DRUG:v2:" + KD_CODE)).isTrue();
        assertThat(template.hasKey("DRUG:" + KD_CODE)).isFalse();
        Long ttl = template.getExpire("DRUG:v2:" + KD_CODE);
        assertThat(ttl).isPositive();
        assertThat(ttl).isLessThanOrEqualTo(55 * 60L);
    }

    @Test
    @DisplayName("옛 v1 키(DRUG:{kdCode})는 v2 get 에서 miss — stale 스키마 서빙 방지")
    void oldV1Key_isMissedByV2Get() {
        template.opsForValue().set("DRUG:" + KD_CODE, "{\"kdCode\":\"" + KD_CODE + "\"}");

        assertThat(adapter.get(KD_CODE)).isEmpty();
    }

    @Test
    @DisplayName("evict — v2 키 삭제")
    void evict_removesV2Key() {
        adapter.put(KD_CODE, response());

        adapter.evict(KD_CODE);

        assertThat(adapter.get(KD_CODE)).isEmpty();
        assertThat(template.hasKey("DRUG:v2:" + KD_CODE)).isFalse();
    }
}
