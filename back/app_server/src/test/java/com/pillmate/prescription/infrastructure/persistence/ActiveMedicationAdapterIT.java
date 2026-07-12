package com.pillmate.prescription.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T-BE-INSIGHT-ACTIVE-LIST — 오늘 복약중 처방전 판정 native SQL 검증.
 * 만료 제외 + 무기한(end_date IS NULL) 포함 + 환자 격리를 query-level 로 확인.
 * schedules 는 native INSERT 로 적재 (schedule 컨텍스트 클래스 미의존 — DDD 격리).
 */
@Tag("integration")
@DataJpaTest(properties = "spring.flyway.locations=classpath:db/migration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(ActiveMedicationAdapter.class)
@DisplayName("ActiveMedicationAdapter.findActivePrescriptionIds — 오늘 복약중 판정")
class ActiveMedicationAdapterIT {

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
    private static final Long OTHER_PATIENT_ID = 777_666L;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 3);

    @Autowired EntityManager entityManager;
    @Autowired ActiveMedicationAdapter adapter;

    @Test
    @DisplayName("활성 2건 + 만료 1건 → 활성 2건만 반환")
    void findActive_excludesExpired() {
        Long active1 = insertPrescription();
        Long active2 = insertPrescription();
        Long expired = insertPrescription();
        insertSlot(PATIENT_ID, active1, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 31), true);
        insertSlot(PATIENT_ID, active2, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 10), true);
        insertSlot(PATIENT_ID, expired, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 30), true);

        Set<Long> result = adapter.findActivePrescriptionIds(PATIENT_ID, TODAY);

        assertThat(result).containsExactlyInAnyOrder(active1, active2);
    }

    @Test
    @DisplayName("무기한(end_date IS NULL) 스케줄 포함")
    void findActive_includesUnlimited() {
        Long unlimited = insertPrescription();
        insertSlot(PATIENT_ID, unlimited, LocalDate.of(2026, 6, 1), null, true);

        Set<Long> result = adapter.findActivePrescriptionIds(PATIENT_ID, TODAY);

        assertThat(result).containsExactly(unlimited);
    }

    @Test
    @DisplayName("아직 시작 안 한(start_date > today) 스케줄 제외")
    void findActive_excludesFuture() {
        Long future = insertPrescription();
        insertSlot(PATIENT_ID, future, LocalDate.of(2026, 8, 1), null, true);

        Set<Long> result = adapter.findActivePrescriptionIds(PATIENT_ID, TODAY);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("비활성(active=false) 스케줄 제외")
    void findActive_excludesInactive() {
        Long inactive = insertPrescription();
        insertSlot(PATIENT_ID, inactive, LocalDate.of(2026, 6, 1), null, false);

        Set<Long> result = adapter.findActivePrescriptionIds(PATIENT_ID, TODAY);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("다른 환자 스케줄 제외 (환자 격리)")
    void findActive_excludesOtherPatient() {
        Long otherPrescription = insertPrescription();
        insertSlot(OTHER_PATIENT_ID, otherPrescription, LocalDate.of(2026, 6, 1), null, true);

        Set<Long> result = adapter.findActivePrescriptionIds(PATIENT_ID, TODAY);

        assertThat(result).isEmpty();
    }

    private Long insertPrescription() {
        Object id = entityManager.createNativeQuery(
                "INSERT INTO prescriptions (patient_id, prescribed_at) VALUES (999888, DATE '2026-06-01') RETURNING id")
                .getSingleResult();
        return ((Number) id).longValue();
    }

    private void insertSlot(Long patientId, Long prescriptionId, LocalDate startDate, LocalDate endDate, boolean active) {
        entityManager.createNativeQuery(
                "INSERT INTO schedules " +
                "(care_group_id, patient_id, prescription_id, time_of_day, custom_time, start_date, end_date, active, created_by) " +
                "VALUES (1, :patientId, :prescriptionId, 'MORNING', TIME '08:00', :startDate, :endDate, :active, 1)")
                .setParameter("patientId", patientId)
                .setParameter("prescriptionId", prescriptionId)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .setParameter("active", active)
                .executeUpdate();
    }
}
