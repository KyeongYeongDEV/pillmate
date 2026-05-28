package com.pillmate.notification.integration;

import com.pillmate.notification.application.GetMyNotificationsService;
import com.pillmate.notification.application.MarkNotificationReadService;
import com.pillmate.notification.application.dto.NotificationItem;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.model.NotificationStatus;
import com.pillmate.notification.domain.model.NotificationType;
import com.pillmate.notification.domain.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
        "cloud.aws.credentials.access-key=test",
        "cloud.aws.credentials.secret-key=test"
})
@Testcontainers
@Transactional
@DisplayName("Notification 통합 테스트 — E2E (Testcontainers)")
class NotificationIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired NotificationRepository notificationRepository;
    @Autowired GetMyNotificationsService getMyNotificationsService;
    @Autowired MarkNotificationReadService markNotificationReadService;

    @Test
    @DisplayName("알림 저장 후 조회 — 수신자별 반환")
    void saveAndQuery_returnsForRecipient() {
        Notification n = Notification.doseTaken(10L, 1L, 5L, 3L);
        notificationRepository.save(n);

        List<NotificationItem> items = getMyNotificationsService.query(10L);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).type()).isEqualTo(NotificationType.DOSE_TAKEN);
        assertThat(items.get(0).status()).isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    @DisplayName("markRead — SENT → READ 전환 후 DB 반영")
    void markRead_persisted() {
        Notification n = Notification.doseTaken(10L, 1L, 5L, 3L);
        n.markSent(Instant.now());
        Notification saved = notificationRepository.save(n);

        markNotificationReadService.markRead(saved.getId(), 10L);

        Notification reloaded = notificationRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(NotificationStatus.READ);
        assertThat(reloaded.getReadAt()).isNotNull();
    }
}
