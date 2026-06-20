package com.pillmate.schedule.integration;

import com.pillmate.schedule.application.port.ScheduleDayQueryPort;
import com.pillmate.schedule.application.port.ScheduleDayQueryPort.DayScheduleProjection;
import jakarta.persistence.EntityManager;
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

import java.time.LocalDate;
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
@DisplayName("ScheduleDayQueryAdapter — timezone 경계 통합 테스트")
class ScheduleDayQueryAdapterIntegrationTest {

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
        // 프로덕션 동일하게 PostgreSQL 세션 timezone=UTC 강제 (JVM TZ 무관)
        registry.add("spring.datasource.url",
                () -> postgres.getJdbcUrl() + "?options=-c%20timezone%3DUTC");
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired ScheduleDayQueryPort scheduleDayQueryPort;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("UTC 23:00 (= KST 익일 08:00) MORNING dose_log 은 KST 익일 조회 시 매칭")
    void findByPatientAndDate_kstBoundary_matchesDoseLog() {
        // 프로덕션 동일하게 PostgreSQL 세션 timezone=UTC 강제 (JDBC 드라이버가 JVM TZ 무시)
        entityManager.createNativeQuery("SET TIME ZONE 'UTC'").executeUpdate();

        // given
        Long patientId = insertUser("tz-patient");
        Long careGroupId = insertCareGroup();
        Long drugId = insertDrug("타이레놀500mg");
        Long scheduleId = insertSchedule(careGroupId, patientId, drugId, "MORNING");
        // UTC 2026-05-30 23:00 = KST 2026-05-31 08:00
        Long doseLogId = insertDoseLog(scheduleId, patientId,
                "2026-05-30 23:00:00+00", "PENDING");

        // when — KST 기준 익일(2026-05-31)로 조회
        List<DayScheduleProjection> result =
                scheduleDayQueryPort.findByPatientAndDate(patientId, LocalDate.of(2026, 5, 31));

        // then — doseLogId 매칭되어야 함 (현재 SQL 은 UTC date 비교라 null 반환 = RED)
        assertThat(result).hasSize(1);
        assertThat(result.get(0).doseLogId()).isEqualTo(doseLogId);
        assertThat(result.get(0).doseStatus()).isEqualTo("PENDING");
    }

    private Long insertDrug(String name) {
        return ((Number) entityManager.createNativeQuery(
                "INSERT INTO drugs (name, kd_code, status, synced_at) " +
                "VALUES (:n, :kd, 'ACTIVE', NOW()) RETURNING id")
                .setParameter("n", name)
                .setParameter("kd", "TZ-" + System.nanoTime())
                .getSingleResult()).longValue();
    }

    private Long insertUser(String name) {
        return ((Number) entityManager.createNativeQuery(
                "INSERT INTO users (name) VALUES (:n) RETURNING id")
                .setParameter("n", name)
                .getSingleResult()).longValue();
    }

    private Long insertCareGroup() {
        return ((Number) entityManager.createNativeQuery(
                "INSERT INTO care_groups (name, created_by, created_at, updated_at) " +
                "VALUES ('tz-test', :cb, NOW(), NOW()) RETURNING id")
                .setParameter("cb", insertUser("tz-creator"))
                .getSingleResult()).longValue();
    }

    private Long insertSchedule(Long careGroupId, Long patientId, Long drugId, String timeOfDay) {
        return ((Number) entityManager.createNativeQuery(
                "INSERT INTO schedules (care_group_id, patient_id, drug_id, time_of_day, custom_time, " +
                "start_date, end_date, active, created_by, created_at) " +
                "VALUES (:g, :p, :d, :t, '08:00', '2026-01-01', '2026-12-31', true, :p, NOW()) RETURNING id")
                .setParameter("g", careGroupId)
                .setParameter("p", patientId)
                .setParameter("d", drugId)
                .setParameter("t", timeOfDay)
                .getSingleResult()).longValue();
    }

    private Long insertDoseLog(Long scheduleId, Long patientId, String scheduledAt, String status) {
        return ((Number) entityManager.createNativeQuery(
                "INSERT INTO dose_logs (schedule_id, patient_id, scheduled_at, status) " +
                "VALUES (:s, :p, CAST(:sa AS timestamptz), :st) RETURNING id")
                .setParameter("s", scheduleId)
                .setParameter("p", patientId)
                .setParameter("sa", scheduledAt)
                .setParameter("st", status)
                .getSingleResult()).longValue();
    }
}
