package com.pillmate.schedule.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.CreateScheduleUseCase;
import com.pillmate.schedule.application.DeactivateScheduleUseCase;
import com.pillmate.schedule.application.ListSchedulesUseCase;
import com.pillmate.schedule.application.UpdateScheduleUseCase;
import com.pillmate.schedule.application.dto.CreateScheduleRequest;
import com.pillmate.schedule.application.dto.CreateScheduleResponse;
import com.pillmate.schedule.application.dto.ScheduleResponse;
import com.pillmate.schedule.application.dto.UpdateScheduleRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final CreateScheduleUseCase createScheduleUseCase;
    private final UpdateScheduleUseCase updateScheduleUseCase;
    private final DeactivateScheduleUseCase deactivateScheduleUseCase;
    private final ListSchedulesUseCase listSchedulesUseCase;

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

    @GetMapping
    public ResponseEntity<ApiResponse<List<ScheduleResponse>>> list(
            @RequestParam Long patientId,
            @RequestParam(required = false) Boolean active) {
        return ResponseEntity.ok(ApiResponse.success(listSchedulesUseCase.list(patientId, active)));
    }
}
