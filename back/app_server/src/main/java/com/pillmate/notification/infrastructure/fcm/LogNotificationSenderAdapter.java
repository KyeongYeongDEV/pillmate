package com.pillmate.notification.infrastructure.fcm;

import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.domain.model.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class LogNotificationSenderAdapter implements NotificationSenderPort {

    @Override
    public void send(Notification notification) {
        log.info("[FCM-STUB] type={} recipient={} title='{}' body='{}'",
                notification.getType(),
                notification.getRecipientUserId(),
                notification.getTitle(),
                notification.getBody());
        notification.markSent(Instant.now());
    }
}
