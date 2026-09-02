package com.pillmate.notification.presentation;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.notification.application.SendDoseNudgeService;
import com.pillmate.notification.application.SendGroupDoseNotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("NotificationController — POST /dose-logs/{doseLogId}/nudge")
@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean SendGroupDoseNotificationService sendGroupDoseNotificationService;
    @MockitoBean SendDoseNudgeService sendDoseNudgeService;

    @Test
    @DisplayName("정상 요청 → 200")
    void nudge_valid_returns200() throws Exception {
        mockMvc.perform(post("/dose-logs/5/nudge").header("X-User-Id", "2"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("비 ACTIVE 그룹원 → 403")
    void nudge_notGroupMember_returns403() throws Exception {
        willThrow(new PillmateException(ErrorCode.GROUP_ACCESS_DENIED))
                .given(sendDoseNudgeService).nudge(5L, 2L);

        mockMvc.perform(post("/dose-logs/5/nudge").header("X-User-Id", "2"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("이미 처리된 복약 기록 → 409")
    void nudge_alreadyChecked_returns409() throws Exception {
        willThrow(new PillmateException(ErrorCode.DOSE_LOG_ALREADY_CHECKED))
                .given(sendDoseNudgeService).nudge(5L, 2L);

        mockMvc.perform(post("/dose-logs/5/nudge").header("X-User-Id", "2"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("쿨다운 중 → 429")
    void nudge_cooldownActive_returns429() throws Exception {
        willThrow(new PillmateException(ErrorCode.NUDGE_COOLDOWN_ACTIVE))
                .given(sendDoseNudgeService).nudge(5L, 2L);

        mockMvc.perform(post("/dose-logs/5/nudge").header("X-User-Id", "2"))
                .andExpect(status().isTooManyRequests());
    }
}
