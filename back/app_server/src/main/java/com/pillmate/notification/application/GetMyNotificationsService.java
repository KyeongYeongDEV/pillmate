package com.pillmate.notification.application;

import com.pillmate.notification.application.dto.NotificationItem;
import com.pillmate.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetMyNotificationsService {

    private final NotificationRepository notificationRepository;

    @Transactional(readOnly = true)
    public List<NotificationItem> query(Long recipientUserId) {
        return notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(recipientUserId)
                .stream()
                .map(NotificationItem::from)
                .toList();
    }
}
