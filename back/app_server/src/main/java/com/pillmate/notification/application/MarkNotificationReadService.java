package com.pillmate.notification.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MarkNotificationReadService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public void markRead(Long notificationId, Long requestUserId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new PillmateException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getRecipientUserId().equals(requestUserId)) {
            throw new PillmateException(ErrorCode.NOT_NOTIFICATION_OWNER);
        }

        notification.markRead(Instant.now());
    }
}
