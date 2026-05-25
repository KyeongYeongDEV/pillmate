package com.pillmate.drug.application.dto;

import com.pillmate.drug.domain.model.Drug;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DrugDetailResponse — imageUrl 노출")
class DrugDetailResponseTest {

    @Test
    @DisplayName("Drug.itemImage 가 DrugDetailResponse.imageUrl 로 매핑된다")
    void from_includesItemImage() throws Exception {
        Drug drug = Drug.of("200006427", "타이레놀정500밀리그람",
                "아세트아미노펜 500mg", "해열, 진통", "식품의약품안전처");
        setItemImage(drug, "https://nedrug.mfds.go.kr/pbp/cmn/itemImageDownload/147426411393800131");

        DrugDetailResponse response = DrugDetailResponse.from(drug);

        assertThat(response.imageUrl())
                .isEqualTo("https://nedrug.mfds.go.kr/pbp/cmn/itemImageDownload/147426411393800131");
    }

    @Test
    @DisplayName("itemImage null 이면 imageUrl null (BE 는 placeholder 결정 안 함)")
    void from_whenItemImageNull_returnsNullImageUrl() {
        Drug drug = Drug.of("999999999", "약품없음", "성분", "효능", "식품의약품안전처");

        DrugDetailResponse response = DrugDetailResponse.from(drug);

        assertThat(response.imageUrl()).isNull();
    }

    private static void setItemImage(Drug drug, String value) throws Exception {
        Field field = Drug.class.getDeclaredField("itemImage");
        field.setAccessible(true);
        field.set(drug, value);
    }
}
