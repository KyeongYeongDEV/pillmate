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
import com.pillmate.schedule.application.UpdatePrescriptionPeriodUseCase;
import com.pillmate.schedule.application.UpdateScheduleUseCase;
import com.pillmate.schedule.application.dto.DayScheduleResponse;
import com.pillmate.schedule.application.dto.MonthScheduleResponse;
import com.pillmate.schedule.application.dto.ScheduleResponse;
import com.pillmate.schedule.application.dto.SlotEditView;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
    @MockitoBean UpdatePrescriptionPeriodUseCase updatePrescriptionPeriodUseCase;

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

    @Test
    @DisplayName("PATCH /schedules/{id} timeOfDay 만 부분 갱신 → 200 (다른 필드는 null 허용)")
    void update_partialTimeOfDayOnly_returns200() throws Exception {
        given(updateScheduleUseCase.update(eq(1L), any())).willReturn(
                new ScheduleResponse(1L, null, 5L, 9L, TimeOfDay.EVENING, null,
                        LocalDate.of(2026, 1, 1), null, true));

        mockMvc.perform(patch("/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timeOfDay\":\"EVENING\"}")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.timeOfDay").value("EVENING"));
    }

    @Test
    @DisplayName("PATCH /schedules/{id} 빈 바디({}) → 200 (모든 필드 null, no-op 부분 갱신 허용)")
    void update_emptyBody_returns200() throws Exception {
        given(updateScheduleUseCase.update(eq(1L), any())).willReturn(
                new ScheduleResponse(1L, null, 5L, 9L, TimeOfDay.MORNING, null,
                        LocalDate.of(2026, 1, 1), null, true));

        mockMvc.perform(patch("/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /schedules/{id} customTime 만 부분 갱신 → 200")
    void update_partialCustomTimeOnly_returns200() throws Exception {
        given(updateScheduleUseCase.update(eq(1L), any())).willReturn(
                new ScheduleResponse(1L, null, 5L, 9L, TimeOfDay.MORNING,
                        java.time.LocalTime.of(7, 30), LocalDate.of(2026, 1, 1), null, true));

        mockMvc.perform(patch("/schedules/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customTime\":\"07:30\"}")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customTime").value("07:30:00"));
    }

    @Test
    @DisplayName("PATCH /schedules/prescriptions/{id}/period → 200")
    void updatePrescriptionPeriod_validRequest_returns200() throws Exception {
        willDoNothing().given(updatePrescriptionPeriodUseCase).update(eq(10L), any());

        mockMvc.perform(patch("/schedules/prescriptions/10/period")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDate\":\"2026-08-01\"}")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PATCH /schedules/prescriptions/{id}/period 타인 처방전 → 403")
    void updatePrescriptionPeriod_otherPatient_returns403() throws Exception {
        willThrow(new PillmateException(ErrorCode.PATIENT_ACCESS_DENIED))
                .given(updatePrescriptionPeriodUseCase).update(eq(10L), any());

        mockMvc.perform(patch("/schedules/prescriptions/10/period")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDate\":\"2026-08-01\"}")
                        .header("X-User-Id", "99"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_016"));
    }

    @Test
    @DisplayName("GET /schedules/day patientId 미지정 → 200, execute(date, null, null) 호출")
    void getDay_withoutPatientId_returns200() throws Exception {
        given(getDayScheduleUseCase.execute(eq(LocalDate.of(2026, 6, 21)), isNull(), isNull()))
                .willReturn(new DayScheduleResponse(LocalDate.of(2026, 6, 21), 0, 0, List.of()));

        mockMvc.perform(get("/schedules/day")
                        .param("date", "2026-06-21")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0));
    }

    @Test
    @DisplayName("GET /schedules/day patientId 지정(같은 그룹) → 200, execute(date, patientId, null) 호출")
    void getDay_withPatientId_sameGroup_returns200() throws Exception {
        given(getDayScheduleUseCase.execute(eq(LocalDate.of(2026, 6, 21)), eq(5L), isNull()))
                .willReturn(new DayScheduleResponse(LocalDate.of(2026, 6, 21), 1, 0, List.of()));

        mockMvc.perform(get("/schedules/day")
                        .param("date", "2026-06-21")
                        .param("patientId", "5")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1));
    }

    @Test
    @DisplayName("GET /schedules/day patientId+groupId 지정 → 200, execute(date, patientId, groupId) 호출 (L2 판정 배선)")
    void getDay_withPatientIdAndGroupId_returns200() throws Exception {
        given(getDayScheduleUseCase.execute(eq(LocalDate.of(2026, 6, 21)), eq(5L), eq(7L)))
                .willReturn(new DayScheduleResponse(LocalDate.of(2026, 6, 21), 1, 0, List.of()));

        mockMvc.perform(get("/schedules/day")
                        .param("date", "2026-06-21")
                        .param("patientId", "5")
                        .param("groupId", "7")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1));
    }

    @Test
    @DisplayName("GET /schedules/day patientId 지정(비그룹원) → 403 GROUP_ACCESS_DENIED")
    void getDay_withPatientId_notSharedGroup_returns403() throws Exception {
        given(getDayScheduleUseCase.execute(eq(LocalDate.of(2026, 6, 21)), eq(999L), isNull()))
                .willThrow(new PillmateException(ErrorCode.GROUP_ACCESS_DENIED));

        mockMvc.perform(get("/schedules/day")
                        .param("date", "2026-06-21")
                        .param("patientId", "999")
                        .header("X-User-Id", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_011"));
    }

    @Test
    @DisplayName("GET /schedules/month patientId 미지정 → 200, execute(month, null) 호출")
    void getMonth_withoutPatientId_returns200() throws Exception {
        given(getMonthScheduleUseCase.execute(eq(YearMonth.of(2026, 6)), isNull()))
                .willReturn(new MonthScheduleResponse("2026-06", List.of()));

        mockMvc.perform(get("/schedules/month")
                        .param("month", "2026-06")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-06"));
    }

    @Test
    @DisplayName("GET /schedules/month patientId 지정(같은 그룹) → 200, execute(month, patientId) 호출")
    void getMonth_withPatientId_sameGroup_returns200() throws Exception {
        given(getMonthScheduleUseCase.execute(eq(YearMonth.of(2026, 6)), eq(5L)))
                .willReturn(new MonthScheduleResponse("2026-06", List.of()));

        mockMvc.perform(get("/schedules/month")
                        .param("month", "2026-06")
                        .param("patientId", "5")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-06"));
    }

    @Test
    @DisplayName("GET /schedules/month patientId 지정(비그룹원) → 403 GROUP_ACCESS_DENIED")
    void getMonth_withPatientId_notSharedGroup_returns403() throws Exception {
        given(getMonthScheduleUseCase.execute(eq(YearMonth.of(2026, 6)), eq(999L)))
                .willThrow(new PillmateException(ErrorCode.GROUP_ACCESS_DENIED));

        mockMvc.perform(get("/schedules/month")
                        .param("month", "2026-06")
                        .param("patientId", "999")
                        .header("X-User-Id", "1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PILL_011"));
    }

    @Test
    @DisplayName("GET /schedules/month groupId 동봉해도 200 (month 는 L1만이라 판정에 미사용, 계약 일관성용 파라미터)")
    void getMonth_withGroupId_isAcceptedButUnused() throws Exception {
        given(getMonthScheduleUseCase.execute(eq(YearMonth.of(2026, 6)), eq(5L)))
                .willReturn(new MonthScheduleResponse("2026-06", List.of()));

        mockMvc.perform(get("/schedules/month")
                        .param("month", "2026-06")
                        .param("patientId", "5")
                        .param("groupId", "7")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.month").value("2026-06"));
    }
}
