package com.pillmate.notification.application.port;

import com.pillmate.notification.domain.model.Notification;

public interface NotificationSenderPort {

    void send(Notification notification);
}
