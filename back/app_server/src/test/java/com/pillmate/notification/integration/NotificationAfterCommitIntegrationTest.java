package com.pillmate.notification.integration;

import com.pillmate.common.security.UserContext;
import com.pillmate.doselog.application.CheckDoseUseCase;
import com.pillmate.doselog.application.dto.CheckDoseRequest;
import com.pillmate.notification.application.port.NotificationSenderPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * #142 P1-B / #120 — AFTER_COMMIT 리스너에서 REQUIRES_NEW 저장 검증.
 * 테스트 클래스에 @Transactional 을 두지 않는다 — 트랜잭션이 실제 커밋되어야
 * @TransactionalEventListener(AFTER_COMMIT) 가 발화하고, P1-B (커밋 완료 트랜잭션
 * 참여로 인한 INSERT 유실) 가 재현/검증된다.
 */
@Tag("integration")
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
        "cloud.aws.credentials.access-key=test",
        "cloud.aws.credentials.secret-key=test"
})
@Testcontainers
@DisplayName("AFTER_COMMIT × REQUIRES_NEW — DOSE_CANCELED 알림 영속 통합 테스트")
class NotificationAfterCommitIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withEnv("TZ", "UTC")
                    .withCommand("postgres", "-c", "timezone=UTC");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> postgres.getJdbcUrl() + "?options=-c%20timezone%3DUTC");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired CheckDoseUseCase checkDoseUseCase;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired TransactionTemplate transactionTemplate;

    @MockBean NotificationSenderPort notificationSenderPort;

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("CANCEL 커밋 후 AFTER_COMMIT 리스너 — notifications 에 DOSE_CANCELED 행 영속 (id not null)")
    void cancel_afterCommit_persistsDoseCanceledNotification() {
        // given — 환자 + 보호자 그룹, 그룹 알림 기발송된 TAKEN dose_log
        Fixture f = transactionTemplate.execute(status -> insertFixture());
        UserContext.set(f.patientId);

        // when — check() 의 @Transactional 커밋 → AFTER_COMMIT 발화
        checkDoseUseCase.check(new CheckDoseRequest(f.doseLogId, "CANCEL", null), f.patientId);

        // then — @Async + REQUIRES_NEW 신규 트랜잭션으로 INSERT 가 비동기 영속 → Awaitility 대기
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT id, recipient_user_id, type, body FROM notifications WHERE dose_log_id = ?",
                    f.doseLogId);
            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).get("id")).isNotNull();
            assertThat(rows.get(0).get("type")).isEqualTo("DOSE_CANCELED");
            assertThat(((Number) rows.get(0).get("recipient_user_id")).longValue()).isEqualTo(f.guardianId);
            assertThat((String) rows.get(0).get("body")).contains("취소했습니다");
        });
    }

    private record Fixture(Long patientId, Long guardianId, Long doseLogId) {}

    private Fixture insertFixture() {
        Long patientId = insertUser("p1b-patient");
        Long guardianId = insertUser("p1b-guardian");
        Long groupId = insertCareGroup(patientId);
        insertMembership(groupId, patientId, "PATIENT");
        insertMembership(groupId, guardianId, "GUARDIAN");
        Long drugId = insertDrug();
        Long scheduleId = insertSchedule(groupId, patientId, drugId);
        Long doseLogId = insertTakenNotifiedDoseLog(scheduleId, patientId);
        return new Fixture(patientId, guardianId, doseLogId);
    }

    private Long insertUser(String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO users (name) VALUES (?) RETURNING id", Long.class, name);
    }

    private Long insertCareGroup(Long createdBy) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO care_groups (name, created_by, created_at, updated_at) " +
                "VALUES ('p1b-group', ?, NOW(), NOW()) RETURNING id", Long.class, createdBy);
    }

    private void insertMembership(Long groupId, Long userId, String role) {
        jdbcTemplate.update(
                "INSERT INTO memberships (care_group_id, user_id, role) VALUES (?, ?, ?)",
                groupId, userId, role);
    }

    private Long insertDrug() {
        return jdbcTemplate.queryForObject(
                "INSERT INTO drugs (name, kd_code, status, synced_at) " +
                "VALUES ('타이레놀500mg', ?, 'ACTIVE', NOW()) RETURNING id",
                Long.class, "P1B-" + System.nanoTime() % 1_000_000_000L);
    }

    private Long insertSchedule(Long groupId, Long patientId, Long drugId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO schedules (care_group_id, patient_id, drug_id, time_of_day, custom_time, " +
                "start_date, end_date, active, created_by, created_at) " +
                "VALUES (?, ?, ?, 'MORNING', '08:00', '2026-01-01', '2026-12-31', true, ?, NOW()) RETURNING id",
                Long.class, groupId, patientId, drugId, patientId);
    }

    private Long insertTakenNotifiedDoseLog(Long scheduleId, Long patientId) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO dose_logs (schedule_id, patient_id, scheduled_at, status, " +
                "checked_by, checked_at, group_notified_at) " +
                "VALUES (?, ?, NOW(), 'TAKEN', ?, NOW() - interval '2 minutes', NOW() - interval '1 minute') " +
                "RETURNING id",
                Long.class, scheduleId, patientId, patientId);
    }
}
