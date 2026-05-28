package com.pillmate.notification.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.common.security.UserContext;
import com.pillmate.notification.application.SendGroupDoseNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dose-logs")
@RequiredArgsConstructor
public class NotificationController {

    private final SendGroupDoseNotificationService sendGroupDoseNotificationService;

    @PostMapping("/{doseLogId}/notify-group")
    public ResponseEntity<ApiResponse<Void>> notifyGroup(@PathVariable Long doseLogId) {
        Long actorUserId = UserContext.get();
        sendGroupDoseNotificationService.send(doseLogId, actorUserId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
