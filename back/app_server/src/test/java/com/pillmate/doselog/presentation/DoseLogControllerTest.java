package com.pillmate.doselog.presentation;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.doselog.application.BulkCheckDoseUseCase;
import com.pillmate.doselog.application.CheckDoseUseCase;
import com.pillmate.doselog.application.GetDoseHistoryUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("DoseLogController — GET /dose-logs 접근 가드 슬라이스 테스트 (P0 유출 취약점 회귀 방지)")
@WebMvcTest(DoseLogController.class)
class DoseLogControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean CheckDoseUseCase checkDoseUseCase;
    @MockitoBean BulkCheckDoseUseCase bulkCheckDoseUseCase;
    @MockitoBean GetDoseHistoryUseCase getDoseHistoryUseCase;

    @Test
    @DisplayName("GET /dose-logs 비그룹원 patientId → 403 PILL_011 (GROUP_ACCESS_DENIED)")
    void history_otherPatient_returns403() throws Exception {
        given(getDoseHistoryUseCase.getHistory(eq(999L), any(), any()))
                .willThrow(new PillmateException(ErrorCode.GROUP_ACCESS_DENIED));

        mockMvc.perform(get("/dose-logs")
                        .param("patientId", "999")
                        .param("from", "2026-08-01T00:00:00Z")
                        .param("to", "2026-08-31T23:59:59Z")
                        .header("X-User-Id", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_011"));
    }
}
