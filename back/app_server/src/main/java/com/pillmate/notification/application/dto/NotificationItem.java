package com.pillmate.notification.application.dto;

import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.model.NotificationStatus;
import com.pillmate.notification.domain.model.NotificationType;

import java.time.Instant;

public record NotificationItem(
        Long id,
        NotificationType type,
        String title,
        String body,
        NotificationStatus status,
        Long doseLogId,
        Instant createdAt
) {
    public static NotificationItem from(Notification notification) {
        return new NotificationItem(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getStatus(),
                notification.getDoseLogId(),
                notification.getCreatedAt()
        );
    }
}
