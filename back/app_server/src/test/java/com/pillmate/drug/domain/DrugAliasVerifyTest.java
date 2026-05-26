package com.pillmate.drug.domain;

import com.pillmate.drug.domain.model.AliasSource;
import com.pillmate.drug.domain.model.DrugAlias;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DrugAlias — verify() 도메인 메서드")
class DrugAliasVerifyTest {

    @Test
    @DisplayName("user alias 생성 시 is_verified=false 초기값")
    void create_withUserSource_isVerifiedFalse() {
        // given / when
        DrugAlias alias = DrugAlias.create("동광나자티딘", null, "200500823", AliasSource.USER, 70);

        // then
        assertThat(alias.isVerified()).isFalse();
    }

    @Test
    @DisplayName("verify() 호출 시 is_verified=true, confidence=100 으로 변경된다")
    void verify_setsVerifiedTrueAndConfidence100() {
        // given
        DrugAlias alias = DrugAlias.create("동광나자티딘", null, "200500823", AliasSource.USER, 70);

        // when
        alias.verify();

        // then
        assertThat(alias.isVerified()).isTrue();
        assertThat(alias.getConfidence()).isEqualTo(100);
    }
}
