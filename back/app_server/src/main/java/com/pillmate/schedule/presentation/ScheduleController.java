package com.pillmate.schedule.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.port.PrescriptionSchedulePort.CreatedSchedule;
import com.pillmate.schedule.application.AddPrescriptionSlotUseCase;
import com.pillmate.schedule.application.CreateScheduleUseCase;
import com.pillmate.schedule.application.DeactivateScheduleUseCase;
import com.pillmate.schedule.application.GetDayScheduleUseCase;
import com.pillmate.schedule.application.GetMonthScheduleUseCase;
import com.pillmate.schedule.application.ListSchedulesUseCase;
import com.pillmate.schedule.application.RemovePrescriptionSlotUseCase;
import com.pillmate.schedule.application.UpdateScheduleUseCase;
import com.pillmate.schedule.application.dto.AddPrescriptionSlotRequest;
import com.pillmate.schedule.application.dto.CreateScheduleRequest;
import com.pillmate.schedule.application.dto.CreateScheduleResponse;
import com.pillmate.schedule.application.dto.DayScheduleResponse;
import com.pillmate.schedule.application.dto.MonthScheduleResponse;
import com.pillmate.schedule.application.dto.ScheduleResponse;
import com.pillmate.schedule.application.dto.UpdateScheduleRequest;
import com.pillmate.schedule.domain.model.TimeOfDay;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final CreateScheduleUseCase createScheduleUseCase;
    private final UpdateScheduleUseCase updateScheduleUseCase;
    private final DeactivateScheduleUseCase deactivateScheduleUseCase;
    private final ListSchedulesUseCase listSchedulesUseCase;
    private final GetDayScheduleUseCase getDayScheduleUseCase;
    private final GetMonthScheduleUseCase getMonthScheduleUseCase;
    private final AddPrescriptionSlotUseCase addPrescriptionSlotUseCase;
    private final RemovePrescriptionSlotUseCase removePrescriptionSlotUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateScheduleResponse>> create(
            @RequestBody @Valid CreateScheduleRequest request) {
        Long userId = UserContext.get();
        return ResponseEntity.ok(ApiResponse.success(createScheduleUseCase.create(request, userId)));
    }

    @PatchMapping("/{scheduleId}")
    public ResponseEntity<ApiResponse<ScheduleResponse>> update(
            @PathVariable Long scheduleId,
            @RequestBody UpdateScheduleRequest request) {
        return ResponseEntity.ok(ApiResponse.success(updateScheduleUseCase.update(scheduleId, request)));
    }

    @PatchMapping("/{scheduleId}/deactivate")
    public ResponseEntity<ApiResponse<ScheduleResponse>> deactivate(@PathVariable Long scheduleId) {
        return ResponseEntity.ok(ApiResponse.success(deactivateScheduleUseCase.deactivate(scheduleId)));
    }

    @PostMapping("/prescriptions/{prescriptionId}/slots")
    public ResponseEntity<ApiResponse<List<CreatedSchedule>>> addPrescriptionSlot(
            @PathVariable Long prescriptionId,
            @RequestBody @Valid AddPrescriptionSlotRequest request) {
        List<CreatedSchedule> created = addPrescriptionSlotUseCase.addSlot(
                prescriptionId, request.timeOfDay(), request.customTime());
        return ResponseEntity.ok(ApiResponse.success(created));
    }

    @PatchMapping("/prescriptions/{prescriptionId}/slots/{timeOfDay}/deactivate")
    public ResponseEntity<ApiResponse<Integer>> removePrescriptionSlot(
            @PathVariable Long prescriptionId,
            @PathVariable TimeOfDay timeOfDay) {
        int removed = removePrescriptionSlotUseCase.removeSlot(prescriptionId, timeOfDay);
        return ResponseEntity.ok(ApiResponse.success(removed));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> list(
            @RequestParam Long patientId,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ApiResponse.success(listSchedulesUseCase.list(patientId, active)));
    }

    @GetMapping("/day")
    public ResponseEntity<ApiResponse<DayScheduleResponse>> getDay(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(ApiResponse.success(getDayScheduleUseCase.execute(date)));
    }

    @GetMapping("/month")
    public ResponseEntity<ApiResponse<MonthScheduleResponse>> getMonth(
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ResponseEntity.ok(ApiResponse.success(getMonthScheduleUseCase.execute(month)));
    }
}
