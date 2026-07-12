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

    // T-BE-DRUG-DETAIL-SURFACE-FIELDS — main_ingr 우선, className 노출
    @Test
    @DisplayName("main_ingr 있으면 ingredient 로 main_ingr 를 사용 (legacy ingredient 컬럼보다 우선)")
    void from_prefersMainIngrOverIngredient() throws Exception {
        Drug drug = Drug.of("200006427", "타이레놀", "", "해열", "식품의약품안전처");
        setField(drug, "mainIngr", "아세트아미노펜");

        DrugDetailResponse response = DrugDetailResponse.from(drug);

        assertThat(response.ingredient()).isEqualTo("아세트아미노펜");
    }

    @Test
    @DisplayName("main_ingr 비어 있으면 legacy ingredient 컬럼으로 fallback")
    void from_fallsBackToIngredientWhenMainIngrBlank() throws Exception {
        Drug drug = Drug.of("200006427", "타이레놀", "레거시성분", "해열", "식품의약품안전처");
        setField(drug, "mainIngr", "   ");

        DrugDetailResponse response = DrugDetailResponse.from(drug);

        assertThat(response.ingredient()).isEqualTo("레거시성분");
    }

    @Test
    @DisplayName("class_name 이 className 필드로 매핑된다 (분류 표시)")
    void from_includesClassName() throws Exception {
        Drug drug = Drug.of("200006427", "타이레놀", "성분", "해열", "식품의약품안전처");
        setField(drug, "className", "해열·진통·소염제");

        DrugDetailResponse response = DrugDetailResponse.from(drug);

        assertThat(response.className()).isEqualTo("해열·진통·소염제");
    }

    private static void setItemImage(Drug drug, String value) throws Exception {
        setField(drug, "itemImage", value);
    }

    private static void setField(Drug drug, String fieldName, String value) throws Exception {
        Field field = Drug.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(drug, value);
    }
}
