package com.pillmate.notification.application.port;

import java.util.Map;

public interface NotificationSenderPort {

    void send(NotificationCommand command);

    record NotificationCommand(
            Long notificationId,
            Long recipientUserId,
            String recipientPushToken,
            String title,
            String body,
            Map<String, String> data
    ) {}
}
