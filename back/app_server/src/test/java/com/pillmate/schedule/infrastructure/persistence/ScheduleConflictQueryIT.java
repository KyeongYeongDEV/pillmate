package com.pillmate.schedule.infrastructure.persistence;

import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.service.ScheduleConflictChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-BE-AUDIT-P1-FIXES Fix3 — 충돌 후보 조회 query-level 검증 (의료 안전 TDD).
 * in-memory checker 테스트가 못 잡는 갭: bucket 필터로 후보 누락 + endDate IS NULL 우회.
 */
@Tag("integration")
@DataJpaTest(properties = "spring.flyway.locations=classpath:db/migration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DisplayName("ScheduleJpaRepository.findActiveByPatient — 충돌 후보 조회 (bucket 무관 + 무기한 포함)")
class ScheduleConflictQueryIT {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    private static final Long PATIENT_ID = 999_888L;
    private static final LocalDate QUERY_DATE = LocalDate.of(2026, 7, 1);

    @Autowired ScheduleJpaRepository jpa;
    @Autowired EntityManager entityManager;

    private final ScheduleConflictChecker checker = new ScheduleConflictChecker();

    @Test
    @DisplayName("다른 bucket(EVENING) 스케줄도 후보에 포함 → 같은 정확 시각이면 checker 가 충돌 탐지")
    void findActiveByPatient_includesOtherBucket_checkerDetectsSameExactTime() {
        Long prescriptionId = insertPrescription();
        jpa.save(prescriptionSlot(prescriptionId, TimeOfDay.EVENING, LocalTime.of(8, 0), LocalDate.of(2026, 7, 31)));

        List<Schedule> candidates = jpa.findActiveByPatient(PATIENT_ID, QUERY_DATE);

        assertThat(candidates).hasSize(1);
        boolean conflict = checker.hasPrescriptionSlotConflict(
                prescriptionId, LocalTime.of(8, 0), QUERY_DATE, LocalDate.of(2026, 7, 7), candidates);
        assertThat(conflict).isTrue();
    }

    @Test
    @DisplayName("무기한(end_date IS NULL) 스케줄도 후보에 포함 → 충돌 검사 우회 불가")
    void findActiveByPatient_includesUnlimitedEndDate() {
        Long prescriptionId = insertPrescription();
        jpa.save(prescriptionSlot(prescriptionId, TimeOfDay.MORNING, LocalTime.of(8, 0), null));

        List<Schedule> candidates = jpa.findActiveByPatient(PATIENT_ID, QUERY_DATE);

        assertThat(candidates).hasSize(1);
        boolean conflict = checker.hasPrescriptionSlotConflict(
                prescriptionId, LocalTime.of(8, 0), QUERY_DATE, LocalDate.of(2026, 7, 7), candidates);
        assertThat(conflict).isTrue();
    }

    @Test
    @DisplayName("기간 지난 스케줄(endDate < date)은 후보 제외")
    void findActiveByPatient_excludesExpired() {
        jpa.save(prescriptionSlot(insertPrescription(), TimeOfDay.MORNING, LocalTime.of(8, 0), LocalDate.of(2026, 6, 30)));

        List<Schedule> candidates = jpa.findActiveByPatient(PATIENT_ID, QUERY_DATE);

        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("비활성(active=false) 스케줄은 후보 제외")
    void findActiveByPatient_excludesInactive() {
        Schedule inactive = prescriptionSlot(insertPrescription(), TimeOfDay.MORNING, LocalTime.of(8, 0), null);
        inactive.deactivate();
        jpa.save(inactive);

        List<Schedule> candidates = jpa.findActiveByPatient(PATIENT_ID, QUERY_DATE);

        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("다른 환자 스케줄은 후보 제외")
    void findActiveByPatient_excludesOtherPatient() {
        Schedule other = Schedule.forPrescription(1L, 777_666L, insertPrescription(),
                TimeOfDay.MORNING, LocalTime.of(8, 0), LocalDate.of(2026, 6, 1), null, 1L);
        jpa.save(other);

        List<Schedule> candidates = jpa.findActiveByPatient(PATIENT_ID, QUERY_DATE);

        assertThat(candidates).isEmpty();
    }

    private Long insertPrescription() {
        Object id = entityManager.createNativeQuery(
                "INSERT INTO prescriptions (patient_id, prescribed_at) VALUES (999888, DATE '2026-06-01') RETURNING id")
                .getSingleResult();
        return ((Number) id).longValue();
    }

    private Schedule prescriptionSlot(Long prescriptionId, TimeOfDay bucket, LocalTime customTime, LocalDate endDate) {
        return Schedule.forPrescription(1L, PATIENT_ID, prescriptionId,
                bucket, customTime, LocalDate.of(2026, 6, 1), endDate, 1L);
    }
}
