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

    // 회귀 스위트의 "오늘"을 6월 마지막 날로 고정 — 과거/오늘 구간(dose_logs 기반)만 검증하는
    // 기존 테스트가 미래 합성 로직의 영향을 받지 않도록 한다.
    private static final LocalDate JUNE_LAST_DAY = LocalDate.of(2026, 6, 30);

    @Test
    @DisplayName("날짜별 total/taken 집계 — TAKEN 2 + PENDING 1 인 날은 (3, 2)")
    void findDailyDoseCounts_aggregatesPerKstDate() {
        // given
        entityManager.createNativeQuery("SET TIME ZONE 'UTC'").executeUpdate();
        Long patientId = insertUser("month-patient");
        Long scheduleId = insertSchedule(patientId, "2026-01-01", "2026-12-31");
        // KST 2026-06-05: TAKEN 2건 + PENDING 1건
        insertDoseLog(scheduleId, patientId, "2026-06-04 23:00:00+00", "TAKEN");   // KST 6/5 08:00
        insertDoseLog(scheduleId, patientId, "2026-06-05 03:30:00+00", "TAKEN");   // KST 6/5 12:30
        insertDoseLog(scheduleId, patientId, "2026-06-05 10:00:00+00", "PENDING"); // KST 6/5 19:00

        // when
        List<DayDoseCount> result =
                scheduleMonthQueryPort.findDailyDoseCounts(patientId, JUNE_FROM, JUNE_TO, JUNE_LAST_DAY);

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
        Long scheduleId = insertSchedule(patientId, "2026-01-01", "2026-12-31");
        insertDoseLog(scheduleId, patientId, "2026-05-31 15:00:00+00", "TAKEN"); // KST 6/1 00:00 → 포함
        insertDoseLog(scheduleId, patientId, "2026-05-31 14:59:00+00", "TAKEN"); // KST 5/31 23:59 → 제외

        // when
        List<DayDoseCount> result =
                scheduleMonthQueryPort.findDailyDoseCounts(patientId, JUNE_FROM, JUNE_TO, JUNE_LAST_DAY);

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
        Long mySchedule = insertSchedule(patientId, "2026-01-01", "2026-12-31");
        Long otherSchedule = insertSchedule(otherId, "2026-01-01", "2026-12-31");
        insertDoseLog(mySchedule, patientId, "2026-06-05 03:30:00+00", "TAKEN");
        insertDoseLog(otherSchedule, otherId, "2026-06-05 03:30:00+00", "TAKEN");

        // when
        List<DayDoseCount> result =
                scheduleMonthQueryPort.findDailyDoseCounts(patientId, JUNE_FROM, JUNE_TO, JUNE_LAST_DAY);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("#134 — 오늘 등록한 미래 스케줄은 dose_log 없이도 totalCount 에 즉시 반영")
    void findDailyDoseCounts_futureScheduleWithoutDoseLog_isSynthesizedFromSchedules() {
        // given — 오늘(KST 6/10) 3일짜리 새 약봉투 스케줄 등록, 자정 배치는 아직 오늘치만 생성
        entityManager.createNativeQuery("SET TIME ZONE 'UTC'").executeUpdate();
        Long patientId = insertUser("future-patient");
        LocalDate today = LocalDate.of(2026, 6, 10);
        insertSchedule(patientId, "2026-06-10", "2026-06-12");

        // when
        List<DayDoseCount> result =
                scheduleMonthQueryPort.findDailyDoseCounts(patientId, JUNE_FROM, JUNE_TO, today);

        // then — 6/11, 6/12 는 dose_log 가 전혀 없지만 schedules 기반으로 합성되어야 함
        assertThat(result).extracting(DayDoseCount::date)
                .containsExactly(LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 12));
        assertThat(result).allSatisfy(count -> {
            assertThat(count.totalCount()).isEqualTo(1);
            assertThat(count.takenCount()).isEqualTo(0);
        });
    }

    @Test
    @DisplayName("#134 — 그 날짜에 기존 스케줄이 전혀 없었다가 방금 하나 생기면 결과에 새로 나타남")
    void findDailyDoseCounts_newFutureDateWithNoPriorSchedule_appearsInResult() {
        // given — 다른 활성 스케줄이 전혀 없는 미래 날짜에 오늘 막 등록
        entityManager.createNativeQuery("SET TIME ZONE 'UTC'").executeUpdate();
        Long patientId = insertUser("new-future-patient");
        LocalDate today = LocalDate.of(2026, 6, 10);
        insertSchedule(patientId, "2026-06-20", "2026-06-20");

        // when
        List<DayDoseCount> result =
                scheduleMonthQueryPort.findDailyDoseCounts(patientId, JUNE_FROM, JUNE_TO, today);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(result.get(0).totalCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("#134 — end_date 지난 스케줄은 그 날짜 이후 미래 구간에 카운트되지 않음")
    void findDailyDoseCounts_pastEndDate_excludedFromFutureSynthesis() {
        // given — 스케줄이 6/12 에 종료, 오늘은 6/10
        entityManager.createNativeQuery("SET TIME ZONE 'UTC'").executeUpdate();
        Long patientId = insertUser("ended-patient");
        LocalDate today = LocalDate.of(2026, 6, 10);
        insertSchedule(patientId, "2026-06-01", "2026-06-12");

        // when
        List<DayDoseCount> result =
                scheduleMonthQueryPort.findDailyDoseCounts(patientId, JUNE_FROM, JUNE_TO, today);

        // then — 6/11, 6/12 는 포함, 6/13 이후(end_date 지남)는 미포함
        assertThat(result).extracting(DayDoseCount::date)
                .containsExactly(LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 12));
    }

    @Test
    @DisplayName("#134 — 오늘 날짜는 dose_log 유무와 무관하게 항상 dose_logs 소스(합성 대상 아님)")
    void findDailyDoseCounts_todayUsesDoseLogSourceEvenWithoutDoseLog() {
        // given — 오늘(6/10) 활성 스케줄은 있지만 아직 dose_log 미생성(배치 전 타이밍 가정)
        entityManager.createNativeQuery("SET TIME ZONE 'UTC'").executeUpdate();
        Long patientId = insertUser("today-no-log-patient");
        LocalDate today = LocalDate.of(2026, 6, 10);
        insertSchedule(patientId, "2026-06-10", "2026-06-12");

        // when
        List<DayDoseCount> result =
                scheduleMonthQueryPort.findDailyDoseCounts(patientId, JUNE_FROM, JUNE_TO, today);

        // then — 오늘(6/10)은 dose_log 가 없으므로 결과에 없어야 함(합성 대상 아님), 미래만 합성됨
        assertThat(result).extracting(DayDoseCount::date)
                .containsExactly(LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 12));
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

    private Long insertSchedule(Long patientId, String startDate, String endDate) {
        return ((Number) entityManager.createNativeQuery(
                "INSERT INTO schedules (care_group_id, patient_id, drug_id, time_of_day, custom_time, " +
                "start_date, end_date, active, created_by, created_at) " +
                "VALUES (:g, :p, :d, 'MORNING', '08:00', CAST(:sd AS date), CAST(:ed AS date), true, :p, NOW()) RETURNING id")
                .setParameter("g", insertCareGroup())
                .setParameter("p", patientId)
                .setParameter("d", insertDrug())
                .setParameter("sd", startDate)
                .setParameter("ed", endDate)
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
