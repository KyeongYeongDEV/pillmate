package com.pillmate.notification.domain;

import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.model.NotificationStatus;
import com.pillmate.notification.domain.model.NotificationType;
import com.pillmate.common.exception.PillmateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Notification Aggregate — 팩토리/상태전이 단위")
class NotificationTest {

    private static final Long RECIPIENT = 2L;
    private static final Long ACTOR     = 1L;
    private static final Long GROUP_ID  = 10L;
    private static final Long DOSE_LOG  = 5L;

    @Test
    @DisplayName("DOSE_TAKEN 팩토리 — 기본 상태 PENDING")
    void create_doseTaken_isPending() {
        Notification n = Notification.doseTaken(RECIPIENT, ACTOR, GROUP_ID, DOSE_LOG);

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(n.getType()).isEqualTo(NotificationType.DOSE_TAKEN);
        assertThat(n.getRecipientUserId()).isEqualTo(RECIPIENT);
        assertThat(n.getActorUserId()).isEqualTo(ACTOR);
        assertThat(n.getCareGroupId()).isEqualTo(GROUP_ID);
        assertThat(n.getDoseLogId()).isEqualTo(DOSE_LOG);
    }

    @Test
    @DisplayName("DOSE_MISSED 팩토리 — type DOSE_MISSED")
    void create_doseMissed_typeIsMissed() {
        Notification n = Notification.doseMissed(RECIPIENT, ACTOR, GROUP_ID, DOSE_LOG);

        assertThat(n.getType()).isEqualTo(NotificationType.DOSE_MISSED);
    }

    @Test
    @DisplayName("markSent() — PENDING → SENT, sentAt 기록")
    void markSent_transitionsToSent() {
        Notification n = Notification.doseTaken(RECIPIENT, ACTOR, GROUP_ID, DOSE_LOG);
        n.markSent(Instant.now());

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(n.getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("markRead() — SENT → READ, readAt 기록")
    void markRead_sentToRead() {
        Notification n = Notification.doseTaken(RECIPIENT, ACTOR, GROUP_ID, DOSE_LOG);
        n.markSent(Instant.now());
        n.markRead(Instant.now());

        assertThat(n.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(n.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("PENDING 상태에서 markRead() 시 예외")
    void markRead_whenPending_throws() {
        Notification n = Notification.doseTaken(RECIPIENT, ACTOR, GROUP_ID, DOSE_LOG);

        assertThatThrownBy(() -> n.markRead(Instant.now()))
                .isInstanceOf(PillmateException.class);
    }

    @Test
    @DisplayName("title/body — 약품명 미포함 (의료 안전)")
    void title_body_noMedicalDetail() {
        Notification n = Notification.doseTaken(RECIPIENT, ACTOR, GROUP_ID, DOSE_LOG);

        assertThat(n.getTitle()).isNotEmpty();
        assertThat(n.getBody()).isNotEmpty();
    }
}
