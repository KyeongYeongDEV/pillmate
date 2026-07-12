package com.pillmate.notification.application.port;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface NotificationSenderPort {

    void send(NotificationCommand command);

    // 반환: 발송 성공한 notificationId 목록 — 호출측이 배치 결과 기반으로 markSent 처리
    default List<Long> sendAll(List<NotificationCommand> commands) {
        List<Long> sent = new ArrayList<>();
        for (NotificationCommand command : commands) {
            if (trySend(command)) {
                sent.add(command.notificationId());
            }
        }
        return sent;
    }

    private boolean trySend(NotificationCommand command) {
        try {
            send(command);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    record NotificationCommand(
            Long notificationId,
            Long recipientUserId,
            String recipientPushToken,
            String title,
            String body,
            Map<String, String> data
    ) {}
}
