package com.pillmate.drug.domain;

import com.pillmate.drug.domain.model.Drug;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Drug — imageS3Key 도메인 메서드")
class DrugImageS3Test {

    @Test
    @DisplayName("imageS3Key 가 null 이면 hasS3CachedImage() false")
    void hasS3CachedImage_whenNull_returnsFalse() {
        Drug drug = Drug.of("200006427", "타이레놀정500밀리그람", null, null, "식품의약품안전처");

        assertThat(drug.hasS3CachedImage()).isFalse();
    }

    @Test
    @DisplayName("imageS3Key 가 공백이면 hasS3CachedImage() false")
    void hasS3CachedImage_whenBlank_returnsFalse() throws Exception {
        Drug drug = Drug.of("200006427", "타이레놀정500밀리그람", null, null, "식품의약품안전처");
        setField(drug, "imageS3Key", "   ");

        assertThat(drug.hasS3CachedImage()).isFalse();
    }

    @Test
    @DisplayName("imageS3Key 에 유효한 S3 키가 있으면 hasS3CachedImage() true")
    void hasS3CachedImage_whenValidKey_returnsTrue() throws Exception {
        Drug drug = Drug.of("200006427", "타이레놀정500밀리그람", null, null, "식품의약품안전처");
        setField(drug, "imageS3Key", "drugs/images/200006427.jpg");

        assertThat(drug.hasS3CachedImage()).isTrue();
    }

    private static void setField(Object target, String fieldName, String value) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, value);
    }
}
