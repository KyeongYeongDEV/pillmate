package com.pillmate.notification.infrastructure.fcm;

import com.pillmate.notification.application.port.NotificationSenderPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@ConditionalOnProperty(name = "pillmate.notification.provider", havingValue = "log", matchIfMissing = true)
public class LogNotificationSenderAdapter implements NotificationSenderPort {

    @Override
    public void send(NotificationCommand command) {
        log.info("[PUSH-LOG] notificationId={} recipient={} token={} title='{}' body='{}' data={}",
                command.notificationId(),
                command.recipientUserId(),
                maskToken(command.recipientPushToken()),
                command.title(),
                command.body(),
                command.data());
    }

    private String maskToken(String token) {
        if (token == null || token.length() < 8) return "(none)";
        return token.substring(0, 4) + "***" + token.substring(token.length() - 4);
    }
}
