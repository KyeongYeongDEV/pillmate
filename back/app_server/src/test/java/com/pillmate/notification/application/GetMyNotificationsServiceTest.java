package com.pillmate.notification.application;

import com.pillmate.notification.application.dto.NotificationItem;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.model.NotificationStatus;
import com.pillmate.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@DisplayName("GetMyNotificationsService — 단위 테스트")
@ExtendWith(MockitoExtension.class)
class GetMyNotificationsServiceTest {

    @Mock NotificationRepository notificationRepository;
    @InjectMocks GetMyNotificationsService sut;

    @Test
    @DisplayName("수신자 ID로 알림 목록 반환 — 최신순")
    void query_returnsNotificationsForRecipient() {
        Notification n1 = Notification.doseTaken(1L, 2L, 10L, 5L);
        Notification n2 = Notification.doseMissed(1L, 3L, 10L, 6L);
        given(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(1L))
                .willReturn(List.of(n1, n2));

        List<NotificationItem> result = sut.query(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).status()).isEqualTo(NotificationStatus.PENDING);
    }
}
