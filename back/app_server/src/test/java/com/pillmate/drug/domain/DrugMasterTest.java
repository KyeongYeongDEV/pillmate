package com.pillmate.drug.domain;

import com.pillmate.drug.domain.model.AliasSource;
import com.pillmate.drug.domain.model.DrugAlias;
import com.pillmate.drug.domain.model.DrugMaster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DrugMaster — 도메인 단위")
class DrugMasterTest {

    @Test
    @DisplayName("create() 호출 시 모든 필드가 올바르게 설정된다")
    void create_setsAllFields() {
        // given / when
        DrugMaster master = DrugMaster.create(
                "200001234", "타이레놀500mg정", "A001", "아세트아미노펜",
                new BigDecimal("500"), "mg", "정", "한국얀센",
                "https://img.mfds.go.kr/img.jpg", "mfds", 101L);

        // then
        assertThat(master.getItemSeq()).isEqualTo("200001234");
        assertThat(master.getProductName()).isEqualTo("타이레놀500mg정");
        assertThat(master.getIngredientCode()).isEqualTo("A001");
        assertThat(master.getIngredientName()).isEqualTo("아세트아미노펜");
        assertThat(master.getDoseAmount()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(master.getDoseUnit()).isEqualTo("mg");
        assertThat(master.getForm()).isEqualTo("정");
        assertThat(master.getCompany()).isEqualTo("한국얀센");
        assertThat(master.getLegacyDrugId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("nullable 필드 생략해도 예외 없다")
    void create_withNullableFieldsOmitted_noException() {
        // given / when
        DrugMaster master = DrugMaster.create(
                "200001234", "타이레놀500mg정", null, null,
                null, null, null, null, null, "mfds", null);

        // then
        assertThat(master.getItemSeq()).isEqualTo("200001234");
        assertThat(master.getProductName()).isEqualTo("타이레놀500mg정");
        assertThat(master.getLegacyDrugId()).isNull();
    }

    @Test
    @DisplayName("DrugAlias.create() — alias/source/confidence 올바르게 설정")
    void drugAlias_create_setsFields() {
        // given / when
        DrugAlias alias = DrugAlias.create("타이레놀", "ㅌㅏㅇㅣㄹㅔㄴㅗㄹ", "200001234", AliasSource.PRODUCT, 100);

        // then
        assertThat(alias.getAlias()).isEqualTo("타이레놀");
        assertThat(alias.getAliasJamo()).isEqualTo("ㅌㅏㅇㅣㄹㅔㄴㅗㄹ");
        assertThat(alias.getItemSeq()).isEqualTo("200001234");
        assertThat(alias.getSource()).isEqualTo(AliasSource.PRODUCT);
        assertThat(alias.getConfidence()).isEqualTo(100);
    }
}
