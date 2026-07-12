package com.pillmate.drug.presentation;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.drug.application.GetDrugDetailUseCase;
import com.pillmate.drug.application.SearchDrugUseCase;
import com.pillmate.drug.application.dto.DrugDetailResponse;
import com.pillmate.drug.application.dto.DrugSearchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("DrugController")
@WebMvcTest(DrugController.class)
class DrugControllerTest {

    private static final String SAMPLE_KD_CODE = "200006427";
    private static final String SAMPLE_NAME = "타이레놀정500밀리그람";

    @Autowired MockMvc mockMvc;
    @MockitoBean SearchDrugUseCase searchDrugUseCase;
    @MockitoBean GetDrugDetailUseCase getDrugDetailUseCase;

    @Test
    @DisplayName("GET /drugs/search?q=타이레놀 → 200 + 결과 반환")
    void search_returns200() throws Exception {
        given(searchDrugUseCase.search("타이레놀"))
                .willReturn(List.of(new DrugSearchResult(
                        1L, SAMPLE_KD_CODE, SAMPLE_NAME, "아세트아미노펜 500mg", "해열, 진통", "정",
                        "https://nedrug.mfds.go.kr/pbp/cmn/itemImageDownload/147426411393800131")));

        mockMvc.perform(get("/drugs/search").param("q", "타이레놀"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value(SAMPLE_NAME));
    }

    @Test
    @DisplayName("GET /drugs/search?q= (빈 검색어) → 400")
    void search_emptyQuery_returns400() throws Exception {
        given(searchDrugUseCase.search(""))
                .willThrow(new PillmateException(ErrorCode.DRUG_SEARCH_EMPTY_QUERY));

        mockMvc.perform(get("/drugs/search").param("q", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PILL_002"));
    }

    @Test
    @DisplayName("GET /drugs/{kdCode} 존재하는 약 → 200")
    void getDetail_returns200() throws Exception {
        given(getDrugDetailUseCase.getDetail(SAMPLE_KD_CODE))
                .willReturn(new DrugDetailResponse(1L, SAMPLE_KD_CODE, SAMPLE_NAME,
                        "아세트아미노펜 500mg", "해열, 진통", "1회 1-2정, 1일 3-4회",
                        "간 손상 주의", "정", "한국얀센", "식품의약품안전처",
                        "https://nedrug.mfds.go.kr/pbp/cmn/itemImageDownload/147426411393800131",
                        "해열·진통·소염제"));

        mockMvc.perform(get("/drugs/" + SAMPLE_KD_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kdCode").value(SAMPLE_KD_CODE));
    }

    @Test
    @DisplayName("GET /drugs/{kdCode} 존재하지 않는 약 → 404")
    void getDetail_notFound_returns404() throws Exception {
        given(getDrugDetailUseCase.getDetail("999999999"))
                .willThrow(new PillmateException(ErrorCode.DRUG_NOT_FOUND));

        mockMvc.perform(get("/drugs/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PILL_001"));
    }
}
