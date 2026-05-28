package com.pillmate.notification.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.model.NotificationStatus;
import com.pillmate.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@DisplayName("MarkNotificationReadService — 단위 테스트")
@ExtendWith(MockitoExtension.class)
class MarkNotificationReadServiceTest {

    @Mock NotificationRepository notificationRepository;
    @InjectMocks MarkNotificationReadService sut;

    private static final Long RECIPIENT = 1L;
    private static final Long NOTIF_ID  = 10L;

    @Test
    @DisplayName("SENT 알림을 READ 로 전환")
    void markRead_whenSent_transitionsToRead() {
        Notification n = Notification.doseTaken(RECIPIENT, 2L, 5L, 3L);
        n.markSent(Instant.now());
        given(notificationRepository.findById(NOTIF_ID)).willReturn(Optional.of(n));

        sut.markRead(NOTIF_ID, RECIPIENT);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.READ);
    }

    @Test
    @DisplayName("알림 없으면 NOTIFICATION_NOT_FOUND 예외")
    void markRead_whenNotFound_throws() {
        given(notificationRepository.findById(NOTIF_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.markRead(NOTIF_ID, RECIPIENT))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("수신자 불일치 시 NOT_NOTIFICATION_OWNER 예외")
    void markRead_whenNotOwner_throws() {
        Notification n = Notification.doseTaken(RECIPIENT, 2L, 5L, 3L);
        n.markSent(Instant.now());
        given(notificationRepository.findById(NOTIF_ID)).willReturn(Optional.of(n));

        assertThatThrownBy(() -> sut.markRead(NOTIF_ID, 99L))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NOT_NOTIFICATION_OWNER);
    }
}
