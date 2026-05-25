package com.pillmate.drug.application;

import com.pillmate.drug.application.dto.DrugMasterCandidate;
import com.pillmate.drug.domain.model.AliasSource;
import com.pillmate.drug.domain.model.DrugAlias;
import com.pillmate.drug.domain.model.DrugMaster;
import com.pillmate.drug.domain.repository.DrugAliasRepository;
import com.pillmate.drug.domain.repository.DrugMasterRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@DisplayName("SearchByAliasUseCase — alias 기반 drug_master 검색")
@ExtendWith(MockitoExtension.class)
class SearchByAliasUseCaseTest {

    @Mock DrugAliasRepository drugAliasRepository;
    @Mock DrugMasterRepository drugMasterRepository;
    @InjectMocks SearchByAliasService sut;

    @Test
    @DisplayName("alias 정확 매칭 시 drug_master 후보 목록 반환")
    void searchByAlias_whenAliasFound_returnsCandidates() {
        // given
        DrugAlias alias = DrugAlias.create("타이레놀", null, "200001234", AliasSource.PRODUCT, 100);
        DrugMaster master = DrugMaster.create(
                "200001234", "타이레놀500mg정", null, "아세트아미노펜",
                new BigDecimal("500"), "mg", "정", "한국얀센", null, "mfds", 101L);
        given(drugAliasRepository.findByAlias("타이레놀")).willReturn(List.of(alias));
        given(drugMasterRepository.findByItemSeq("200001234")).willReturn(Optional.of(master));

        // when
        List<DrugMasterCandidate> results = sut.searchByAlias("타이레놀");

        // then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).itemSeq()).isEqualTo("200001234");
        assertThat(results.get(0).productName()).isEqualTo("타이레놀500mg정");
        assertThat(results.get(0).legacyDrugId()).isEqualTo(101L);
        assertThat(results.get(0).confidence()).isEqualTo(100);
    }

    @Test
    @DisplayName("alias 없으면 빈 목록 반환")
    void searchByAlias_whenNotFound_returnsEmpty() {
        // given
        given(drugAliasRepository.findByAlias("없는약")).willReturn(List.of());

        // when
        List<DrugMasterCandidate> results = sut.searchByAlias("없는약");

        // then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("alias는 있으나 drug_master에 없으면 결과에서 제외")
    void searchByAlias_whenMasterMissing_skipsCandidate() {
        // given
        DrugAlias alias = DrugAlias.create("고아약", null, "ORPHAN-001", AliasSource.PRODUCT, 100);
        given(drugAliasRepository.findByAlias("고아약")).willReturn(List.of(alias));
        given(drugMasterRepository.findByItemSeq("ORPHAN-001")).willReturn(Optional.empty());

        // when
        List<DrugMasterCandidate> results = sut.searchByAlias("고아약");

        // then
        assertThat(results).isEmpty();
    }
}
