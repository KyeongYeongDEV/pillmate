package com.pillmate.schedule.presentation;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.schedule.application.AddPrescriptionSlotUseCase;
import com.pillmate.schedule.application.CreateScheduleUseCase;
import com.pillmate.schedule.application.DeactivateScheduleUseCase;
import com.pillmate.schedule.application.GetDayScheduleUseCase;
import com.pillmate.schedule.application.GetMonthScheduleUseCase;
import com.pillmate.schedule.application.GetPrescriptionSlotsUseCase;
import com.pillmate.schedule.application.ListSchedulesUseCase;
import com.pillmate.schedule.application.RemovePrescriptionSlotUseCase;
import com.pillmate.schedule.application.UpdateScheduleUseCase;
import com.pillmate.schedule.application.dto.SlotEditView;
import com.pillmate.schedule.domain.model.TimeOfDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("ScheduleController — GET /schedules/prescriptions/{id}/slots")
@WebMvcTest(ScheduleController.class)
class ScheduleControllerTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean CreateScheduleUseCase createScheduleUseCase;
    @MockitoBean UpdateScheduleUseCase updateScheduleUseCase;
    @MockitoBean DeactivateScheduleUseCase deactivateScheduleUseCase;
    @MockitoBean ListSchedulesUseCase listSchedulesUseCase;
    @MockitoBean GetDayScheduleUseCase getDayScheduleUseCase;
    @MockitoBean GetMonthScheduleUseCase getMonthScheduleUseCase;
    @MockitoBean AddPrescriptionSlotUseCase addPrescriptionSlotUseCase;
    @MockitoBean RemovePrescriptionSlotUseCase removePrescriptionSlotUseCase;
    @MockitoBean GetPrescriptionSlotsUseCase getPrescriptionSlotsUseCase;

    @Test
    @DisplayName("GET /schedules/prescriptions/{id}/slots → 200 + SlotEditView 목록")
    void getPrescriptionSlots_returns200WithSlotList() throws Exception {
        given(getPrescriptionSlotsUseCase.execute(100L)).willReturn(List.of(
                new SlotEditView(1L, TimeOfDay.MORNING, "08:00", LocalDate.of(2026, 12, 31), true),
                new SlotEditView(2L, TimeOfDay.EVENING, "19:00", null, true)
        ));

        mockMvc.perform(get("/schedules/prescriptions/100/slots")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].scheduleId").value(1))
                .andExpect(jsonPath("$.data[0].timeOfDay").value("MORNING"))
                .andExpect(jsonPath("$.data[0].time").value("08:00"))
                .andExpect(jsonPath("$.data[0].endDate").value("2026-12-31"))
                .andExpect(jsonPath("$.data[0].editable").value(true))
                .andExpect(jsonPath("$.data[1].endDate").doesNotExist())
                .andExpect(jsonPath("$.data[1].editable").value(true));
    }

    @Test
    @DisplayName("GET /schedules/prescriptions/{id}/slots 타인 처방전 → 403 PATIENT_ACCESS_DENIED")
    void getPrescriptionSlots_otherPatient_returns403() throws Exception {
        given(getPrescriptionSlotsUseCase.execute(100L))
                .willThrow(new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED));

        mockMvc.perform(get("/schedules/prescriptions/100/slots")
                        .header("X-User-Id", "99"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_016"));
    }

    @Test
    @DisplayName("GET /schedules/prescriptions/{id}/slots 슬롯 없음 → 200 + 빈 배열")
    void getPrescriptionSlots_noSlots_returnsEmptyArray() throws Exception {
        given(getPrescriptionSlotsUseCase.execute(200L)).willReturn(List.of());

        mockMvc.perform(get("/schedules/prescriptions/200/slots")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }
}
