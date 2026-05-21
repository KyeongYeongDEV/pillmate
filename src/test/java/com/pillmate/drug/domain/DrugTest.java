package com.pillmate.drug.domain;

import com.pillmate.drug.domain.model.Drug;
import com.pillmate.drug.domain.model.DrugStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Drug 도메인 엔티티")
class DrugTest {

    @Test
    @DisplayName("팩토리 메서드로 생성된 Drug는 ACTIVE 상태")
    void create_shouldBeActive() {
        Drug drug = Drug.of("200006427", "타이레놀정500밀리그람", "아세트아미노펜 500mg", "해열, 진통", "식품의약품안전처");

        assertThat(drug.getStatus()).isEqualTo(DrugStatus.ACTIVE);
        assertThat(drug.isActive()).isTrue();
    }

    @Test
    @DisplayName("REVOKED 약은 isActive()가 false")
    void revoke_shouldBeInactive() {
        Drug drug = Drug.of("200006427", "타이레놀정500밀리그람", "아세트아미노펜 500mg", "해열, 진통", "식품의약품안전처");
        drug.revoke();

        assertThat(drug.isActive()).isFalse();
        assertThat(drug.getStatus()).isEqualTo(DrugStatus.REVOKED);
    }

    @Test
    @DisplayName("kdCode와 name이 같으면 동등 비교 성립")
    void equalsByKdCodeAndName() {
        Drug drug1 = Drug.of("200006427", "타이레놀정500밀리그람", null, null, "식품의약품안전처");
        Drug drug2 = Drug.of("200006427", "타이레놀정500밀리그람", null, null, "식품의약품안전처");

        assertThat(drug1.getKdCode()).isEqualTo(drug2.getKdCode());
        assertThat(drug1.getName()).isEqualTo(drug2.getName());
    }

    @Test
    @DisplayName("출처는 항상 식품의약품안전처")
    void source_shouldBeMfds() {
        Drug drug = Drug.of("200006427", "타이레놀정500밀리그람", null, null, "식품의약품안전처");

        assertThat(drug.getSource()).isEqualTo("식품의약품안전처");
    }
}
