package com.pillmate.drug.infrastructure.persistence;

import com.pillmate.drug.domain.model.DrugInteraction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Tag("integration")
@DisplayName("DrugInteractionJpaRepository — findByKdCodes 병용금기 쌍 조회")
class DrugInteractionRepositoryTest {

    @Autowired
    DrugInteractionJpaRepository repository;

    @Test
    @DisplayName("처방된 kdCode 목록에 포함된 쌍만 반환한다")
    void findByKdCodes_returnsAll() {
        // given — 직접 저장 (bulk import 없이)
        DrugInteraction i1 = buildInteraction("A001", "B001", "CRITICAL", "테스트 금기");
        DrugInteraction i2 = buildInteraction("A001", "C001", "HIGH", "테스트 주의");
        DrugInteraction outsider = buildInteraction("X999", "Y999", "MEDIUM", "무관한 쌍");
        repository.saveAll(List.of(i1, i2, outsider));

        // when
        List<DrugInteraction> result = repository.findByKdCodes(List.of("A001", "B001", "C001"));

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allSatisfy(di ->
                assertThat(List.of("A001")).containsAnyOf(di.getDrugCodeA()));
        assertThat(result).extracting(DrugInteraction::getDrugCodeA)
                .doesNotContain("X999");
    }

    private DrugInteraction buildInteraction(String codeA, String codeB,
                                              String severity, String desc) {
        return DrugInteraction.create(codeA, codeB, "DRUG_DRUG", severity, desc,
                "식품의약품안전처", Instant.now());
    }
}
