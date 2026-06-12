package com.pillmate.schedule.integration;

import com.pillmate.schedule.application.port.ScheduleMonthQueryPort;
import com.pillmate.schedule.application.port.ScheduleMonthQueryPort.DayDoseCount;
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

import java.time.Instant;
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
@DisplayName("ScheduleMonthQueryAdapter — KST 월 집계 통합 테스트")
class ScheduleMonthQueryAdapterIntegrationTest {

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

    @Autowired ScheduleMonthQueryPort scheduleMonthQueryPort;
    @Autowired EntityManager entityManager;

    // KST 2026-06-01 00:00 ~ 2026-07-01 00:00
    private static final Instant JUNE_FROM = Instant.parse("2026-05-31T15:00:00Z");
    private static final Instant JUNE_TO   = Instant.parse("2026-06-30T15:00:00Z");

    @Test
    @DisplayName("날짜별 total/taken 집계 — TAKEN 2 + PENDING 1 인 날은 (3, 2)")
    void findDailyDoseCounts_aggregatesPerKstDate() {
        // given
        entityManager.createNativeQuery("SET TIME ZONE 'UTC'").executeUpdate();
        Long patientId = insertUser("month-patient");
        Long scheduleId = insertSchedule(patientId);
        // KST 2026-06-05: TAKEN 2건 + PENDING 1건
        insertDoseLog(scheduleId, patientId, "2026-06-04 23:00:00+00", "TAKEN");   // KST 6/5 08:00
        insertDoseLog(scheduleId, patientId, "2026-06-05 03:30:00+00", "TAKEN");   // KST 6/5 12:30
        insertDoseLog(scheduleId, patientId, "2026-06-05 10:00:00+00", "PENDING"); // KST 6/5 19:00

        // when
        List<DayDoseCount> result =
                scheduleMonthQueryPort.findDailyDoseCounts(patientId, JUNE_FROM, JUNE_TO);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 6, 5));
        assertThat(result.get(0).totalCount()).isEqualTo(3);
        assertThat(result.get(0).takenCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("KST 월 경계 — UTC 5/31 15:00(=KST 6/1 00:00) 은 6월 포함, UTC 5/31 14:59 는 제외")
    void findDailyDoseCounts_kstMonthBoundary() {
        // given
        entityManager.createNativeQuery("SET TIME ZONE 'UTC'").executeUpdate();
        Long patientId = insertUser("boundary-patient");
        Long scheduleId = insertSchedule(patientId);
        insertDoseLog(scheduleId, patientId, "2026-05-31 15:00:00+00", "TAKEN"); // KST 6/1 00:00 → 포함
        insertDoseLog(scheduleId, patientId, "2026-05-31 14:59:00+00", "TAKEN"); // KST 5/31 23:59 → 제외

        // when
        List<DayDoseCount> result =
                scheduleMonthQueryPort.findDailyDoseCounts(patientId, JUNE_FROM, JUNE_TO);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(result.get(0).totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("다른 환자 dose_logs 는 집계 제외 (그룹 격리)")
    void findDailyDoseCounts_isolatesByPatient() {
        // given
        entityManager.createNativeQuery("SET TIME ZONE 'UTC'").executeUpdate();
        Long patientId = insertUser("me");
        Long otherId = insertUser("other");
        Long mySchedule = insertSchedule(patientId);
        Long otherSchedule = insertSchedule(otherId);
        insertDoseLog(mySchedule, patientId, "2026-06-05 03:30:00+00", "TAKEN");
        insertDoseLog(otherSchedule, otherId, "2026-06-05 03:30:00+00", "TAKEN");

        // when
        List<DayDoseCount> result =
                scheduleMonthQueryPort.findDailyDoseCounts(patientId, JUNE_FROM, JUNE_TO);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalCount()).isEqualTo(1);
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
                "VALUES ('month-test', :cb, NOW(), NOW()) RETURNING id")
                .setParameter("cb", insertUser("month-creator"))
                .getSingleResult()).longValue();
    }

    private Long insertDrug() {
        return ((Number) entityManager.createNativeQuery(
                "INSERT INTO drugs (name, kd_code, status, synced_at) " +
                "VALUES ('타이레놀500mg', :kd, 'ACTIVE', NOW()) RETURNING id")
                .setParameter("kd", "M-" + System.nanoTime())
                .getSingleResult()).longValue();
    }

    private Long insertSchedule(Long patientId) {
        return ((Number) entityManager.createNativeQuery(
                "INSERT INTO schedules (care_group_id, patient_id, drug_id, time_of_day, " +
                "start_date, end_date, active, created_by, created_at) " +
                "VALUES (:g, :p, :d, 'MORNING', '2026-01-01', '2026-12-31', true, :p, NOW()) RETURNING id")
                .setParameter("g", insertCareGroup())
                .setParameter("p", patientId)
                .setParameter("d", insertDrug())
                .getSingleResult()).longValue();
    }

    private void insertDoseLog(Long scheduleId, Long patientId, String scheduledAt, String status) {
        entityManager.createNativeQuery(
                "INSERT INTO dose_logs (schedule_id, patient_id, scheduled_at, status) " +
                "VALUES (:s, :p, CAST(:sa AS timestamptz), :st)")
                .setParameter("s", scheduleId)
                .setParameter("p", patientId)
                .setParameter("sa", scheduledAt)
                .setParameter("st", status)
                .executeUpdate();
    }
}
