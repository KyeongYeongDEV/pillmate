package com.pillmate.drug.application;

import com.pillmate.drug.application.port.DrugImageStoragePort;
import com.pillmate.drug.domain.model.Drug;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@DisplayName("DrugImageUrlResolver — S3 presigned URL 또는 식약처 fallback")
@ExtendWith(MockitoExtension.class)
class DrugImageUrlResolverTest {

    @Mock DrugImageStoragePort drugImageStoragePort;
    @InjectMocks DrugImageUrlResolver sut;

    @Test
    @DisplayName("imageS3Key 있으면 S3 presigned URL 반환")
    void resolve_whenS3KeyPresent_returnsPresignedUrl() throws Exception {
        // given
        Drug drug = Drug.of("200006427", "타이레놀정500밀리그람", null, null, "식품의약품안전처");
        setField(drug, "imageS3Key", "drugs/images/200006427.jpg");
        given(drugImageStoragePort.issueViewUrl(eq("drugs/images/200006427.jpg"), any(Duration.class)))
                .willReturn("https://pillmate-prescriptions.s3.ap-northeast-2.amazonaws.com/drugs/images/200006427.jpg?X-Amz-Test=1");

        // when
        String result = sut.resolve(drug);

        // then
        assertThat(result).startsWith("https://pillmate-prescriptions.s3");
    }

    @Test
    @DisplayName("imageS3Key 없으면 식약처 item_image fallback 반환")
    void resolve_whenNoS3Key_returnsFallback() throws Exception {
        // given
        Drug drug = Drug.of("200006427", "타이레놀정500밀리그람", null, null, "식품의약품안전처");
        setField(drug, "itemImage", "https://nedrug.mfds.go.kr/pbp/cmn/itemImageDownload/test123");

        // when
        String result = sut.resolve(drug);

        // then
        assertThat(result).isEqualTo("https://nedrug.mfds.go.kr/pbp/cmn/itemImageDownload/test123");
        verifyNoInteractions(drugImageStoragePort);
    }

    @Test
    @DisplayName("imageS3Key 도 item_image 도 없으면 null 반환")
    void resolve_whenBothNull_returnsNull() {
        // given
        Drug drug = Drug.of("200006427", "타이레놀정500밀리그람", null, null, "식품의약품안전처");

        // when
        String result = sut.resolve(drug);

        // then
        assertThat(result).isNull();
        verifyNoInteractions(drugImageStoragePort);
    }

    private static void setField(Object target, String fieldName, String value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
