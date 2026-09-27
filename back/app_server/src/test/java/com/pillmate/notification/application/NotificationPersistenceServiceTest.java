package com.pillmate.notification.application;

import com.pillmate.notification.domain.model.NotificationStatus;
import com.pillmate.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

// 2026-09-27 FCM 성능개선 — markSent 를 건당 별도 트랜잭션(REQUIRES_NEW) N번 대신
// 배치 UPDATE 1번으로 (기존 saveAll 관례와 동일 패턴).
@DisplayName("NotificationPersistenceService — 단위 테스트")
@ExtendWith(MockitoExtension.class)
class NotificationPersistenceServiceTest {

    @Mock NotificationRepository notificationRepository;
    @InjectMocks NotificationPersistenceService sut;

    @Test
    @DisplayName("markSentAll — id 목록을 배치 UPDATE 1회로 위임")
    void markSentAll_delegatesToBatchUpdate() {
        given(notificationRepository.markSentByIdIn(any(), any(), any())).willReturn(3);

        sut.markSentAll(List.of(1L, 2L, 3L), Instant.parse("2026-09-27T00:00:00Z"));

        verify(notificationRepository).markSentByIdIn(
                eq(List.of(1L, 2L, 3L)),
                eq(Instant.parse("2026-09-27T00:00:00Z")),
                eq(NotificationStatus.SENT));
    }

    @Test
    @DisplayName("markSentAll — 빈 목록이면 쿼리 호출 자체를 안 함 (빈 IN 절 방지)")
    void markSentAll_emptyList_skipsQuery() {
        sut.markSentAll(List.of(), Instant.now());

        verify(notificationRepository, never()).markSentByIdIn(any(), any(), any());
    }
}
