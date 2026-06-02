package com.pillmate.notification.application;

import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationPersistenceService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public List<Notification> saveAll(List<Notification> notifications) {
        return notificationRepository.saveAll(notifications);
    }

    @Transactional
    public void markSent(Long notificationId, Instant when) {
        notificationRepository.findById(notificationId)
                .ifPresent(n -> n.markSent(when));
    }
}
