package com.pillmate.drug.infrastructure.persistence;

import com.pillmate.drug.domain.model.Drug;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
@DisplayName("DrugJpaRepository — 한국어/영문 부분 매칭 검색")
class DrugJpaRepositoryKoreanSearchIT {

    @Autowired
    DrugJpaRepository repository;

    @Test
    @DisplayName("한국어 부분 매칭 — '타이레놀'로 검색하면 적재된 타이레놀 시리즈가 반환된다")
    void searchByKeyword_koreanSubstring_returnsMatches() {
        List<Drug> results = repository.searchByKeyword("타이레놀", 20);

        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(drug ->
                assertThat(drug.getName()).containsIgnoringCase("타이레놀"));
    }

    @Test
    @DisplayName("영문 성분명 — 'Acetaminophen' 으로 검색하면 main_ingr 또는 ingredient 에서 매칭된다")
    void searchByKeyword_englishIngredient_returnsMatches() {
        List<Drug> results = repository.searchByKeyword("Acetaminophen", 20);

        assertThat(results).isNotEmpty();
    }
}
