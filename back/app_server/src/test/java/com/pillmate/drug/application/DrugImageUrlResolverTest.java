package com.pillmate.drug.application;

import com.pillmate.drug.application.port.DrugImageStoragePort;
import com.pillmate.drug.application.port.DrugImageUrlCachePort;
import com.pillmate.drug.domain.model.Drug;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("DrugImageUrlResolver — presigned URL Redis 캐시 + 식약처 fallback")
@ExtendWith(MockitoExtension.class)
class DrugImageUrlResolverTest {

    private static final String S3_KEY = "drugs/images/200006427.jpg";

    @Mock DrugImageStoragePort drugImageStoragePort;
    @Mock DrugImageUrlCachePort drugImageUrlCachePort;
    @InjectMocks DrugImageUrlResolver sut;

    @Test
    @DisplayName("캐시 MISS 시 presign(24h) 후 캐시에 저장하고 반환")
    void resolve_cacheMiss_presignsAndCaches() throws Exception {
        Drug drug = drugWithS3Key();
        given(drugImageUrlCachePort.get(S3_KEY)).willReturn(Optional.empty());
        given(drugImageStoragePort.issueViewUrl(eq(S3_KEY), eq(Duration.ofHours(24))))
                .willReturn("https://s3/drugs/images/200006427.jpg?X-Amz-Signature=abc");

        String result = sut.resolve(drug);

        assertThat(result).isEqualTo("https://s3/drugs/images/200006427.jpg?X-Amz-Signature=abc");
        verify(drugImageUrlCachePort).put(S3_KEY, result);
    }

    @Test
    @DisplayName("캐시 HIT 시 동일 URL 반환 — presign 미호출 (URL churn 제거)")
    void resolve_cacheHit_returnsCachedWithoutPresign() throws Exception {
        Drug drug = drugWithS3Key();
        given(drugImageUrlCachePort.get(S3_KEY))
                .willReturn(Optional.of("https://s3/cached.jpg?X-Amz-Signature=fixed"));

        String result = sut.resolve(drug);

        assertThat(result).isEqualTo("https://s3/cached.jpg?X-Amz-Signature=fixed");
        verifyNoInteractions(drugImageStoragePort);
        verify(drugImageUrlCachePort, never()).put(any(), any());
    }

    @Test
    @DisplayName("Redis 장애(캐시 miss 로 fail-open) 시 presign 으로 정상 반환")
    void resolve_redisDown_failsOpenToPresign() throws Exception {
        Drug drug = drugWithS3Key();
        given(drugImageUrlCachePort.get(S3_KEY)).willReturn(Optional.empty());
        given(drugImageStoragePort.issueViewUrl(eq(S3_KEY), any(Duration.class)))
                .willReturn("https://s3/fresh.jpg?X-Amz-Signature=new");

        String result = sut.resolve(drug);

        assertThat(result).isEqualTo("https://s3/fresh.jpg?X-Amz-Signature=new");
    }

    @Test
    @DisplayName("imageS3Key 없으면 식약처 item_image fallback — 캐시/presign 미접근")
    void resolve_whenNoS3Key_returnsFallback() throws Exception {
        Drug drug = Drug.of("200006427", "타이레놀정500밀리그람", null, null, "식품의약품안전처");
        setField(drug, "itemImage", "https://nedrug.mfds.go.kr/pbp/cmn/itemImageDownload/test123");

        String result = sut.resolve(drug);

        assertThat(result).isEqualTo("https://nedrug.mfds.go.kr/pbp/cmn/itemImageDownload/test123");
        verifyNoInteractions(drugImageStoragePort);
        verifyNoInteractions(drugImageUrlCachePort);
    }

    @Test
    @DisplayName("imageS3Key 도 item_image 도 없으면 null 반환")
    void resolve_whenBothNull_returnsNull() {
        Drug drug = Drug.of("200006427", "타이레놀정500밀리그람", null, null, "식품의약품안전처");

        String result = sut.resolve(drug);

        assertThat(result).isNull();
        verifyNoInteractions(drugImageStoragePort);
        verifyNoInteractions(drugImageUrlCachePort);
    }

    private Drug drugWithS3Key() throws Exception {
        Drug drug = Drug.of("200006427", "타이레놀정500밀리그람", null, null, "식품의약품안전처");
        setField(drug, "imageS3Key", S3_KEY);
        return drug;
    }

    private static void setField(Object target, String fieldName, String value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
