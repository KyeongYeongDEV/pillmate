package com.pillmate.notification.domain.repository;

import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId);

    @Modifying
    @Query("""
            UPDATE Notification n
            SET n.status = :readStatus, n.readAt = :now
            WHERE n.recipientUserId = :userId AND n.status = :sentStatus
            """)
    int markAllReadByUser(
            @Param("userId") Long userId,
            @Param("now") Instant now,
            @Param("readStatus") NotificationStatus readStatus,
            @Param("sentStatus") NotificationStatus sentStatus
    );
}
