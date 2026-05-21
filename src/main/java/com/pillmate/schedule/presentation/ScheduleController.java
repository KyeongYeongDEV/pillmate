package com.pillmate.schedule.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.CreateScheduleUseCase;
import com.pillmate.schedule.application.dto.CreateScheduleRequest;
import com.pillmate.schedule.application.dto.CreateScheduleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final CreateScheduleUseCase createScheduleUseCase;

    @PostMapping
    public ResponseEntity<ApiResponse<CreateScheduleResponse>> create(
            @RequestBody @Valid CreateScheduleRequest request) {
        Long userId = UserContext.get();
        return ResponseEntity.ok(ApiResponse.success(createScheduleUseCase.create(request, userId)));
    }
}
