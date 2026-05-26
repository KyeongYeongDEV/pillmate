package com.pillmate.drug.application;

import com.pillmate.common.exception.PillmateException;
import com.pillmate.drug.application.dto.DrugSearchResult;
import com.pillmate.drug.domain.model.Drug;
import com.pillmate.drug.domain.repository.DrugRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;

@DisplayName("SearchDrugUseCase — 약 검색 유스케이스")
@ExtendWith(MockitoExtension.class)
class SearchDrugUseCaseTest {

    @Mock DrugRepository drugRepository;
    @InjectMocks SearchDrugUseCase sut;

    @Test
    @DisplayName("검색어로 약을 조회하면 결과 목록을 반환한다")
    void search_returnsResults() {
        Drug drug = Drug.of("200006427", "타이레놀정500밀리그람", "아세트아미노펜 500mg", "해열, 진통", "식품의약품안전처");
        given(drugRepository.searchByKeyword(eq("타이레놀"), anyInt())).willReturn(List.of(drug));

        List<DrugSearchResult> results = sut.search("타이레놀");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).contains("타이레놀");
    }

    @Test
    @DisplayName("빈 검색어는 PillmateException 발생")
    void emptyQuery_throwsException() {
        assertThatThrownBy(() -> sut.search(""))
                .isInstanceOf(PillmateException.class);
    }

    @Test
    @DisplayName("공백만 있는 검색어도 예외 발생")
    void blankQuery_throwsException() {
        assertThatThrownBy(() -> sut.search("   "))
                .isInstanceOf(PillmateException.class);
    }

    @Test
    @DisplayName("결과가 없으면 빈 리스트 반환")
    void noResults_returnsEmptyList() {
        given(drugRepository.searchByKeyword(eq("없는약"), anyInt())).willReturn(List.of());

        List<DrugSearchResult> results = sut.search("없는약");

        assertThat(results).isEmpty();
    }
}
