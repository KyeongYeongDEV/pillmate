package com.pillmate.notification.presentation;

import com.pillmate.common.response.ApiResponse;
import com.pillmate.common.security.UserContext;
import com.pillmate.notification.application.GetMyNotificationsService;
import com.pillmate.notification.application.MarkNotificationReadService;
import com.pillmate.notification.application.dto.NotificationItem;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class MyNotificationsController {

    private final GetMyNotificationsService getMyNotificationsService;
    private final MarkNotificationReadService markNotificationReadService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationItem>>> getMyNotifications() {
        Long userId = UserContext.get();
        List<NotificationItem> items = getMyNotificationsService.query(userId);
        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Long notificationId) {
        Long userId = UserContext.get();
        markNotificationReadService.markRead(notificationId, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
