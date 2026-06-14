package com.pillmate.prescription.integration;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.GetPrescriptionDetailUseCase;
import com.pillmate.prescription.application.GetPrescriptionsUseCase;
import com.pillmate.prescription.application.dto.PrescriptionDetailResponse;
import com.pillmate.prescription.application.dto.PrescriptionSummary;
import com.pillmate.prescription.application.port.FileStoragePort;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@Tag("integration")
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
        "cloud.aws.credentials.access-key=test",
        "cloud.aws.credentials.secret-key=test"
})
@Testcontainers
@Transactional
@DisplayName("Prescription 조회 통합 — 목록/상세 read (본인 격리)")
class PrescriptionReadIntegrationTest {

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

    private static final Long OWNER_ID = 7L;
    private static final Long OTHER_ID = 99L;

    @Autowired GetPrescriptionsUseCase getPrescriptionsUseCase;
    @Autowired GetPrescriptionDetailUseCase getPrescriptionDetailUseCase;
    @Autowired PrescriptionRepository prescriptionRepository;
    @Autowired EntityManager entityManager;

    @MockitoBean FileStoragePort fileStoragePort;

    @BeforeEach void setUp() { UserContext.clear(); }
    @AfterEach void tearDown() { UserContext.clear(); }

    @Test
    @DisplayName("목록 — 본인 처방전만 최신순(prescribedAt desc)")
    void list_ownOnly_sortedDesc() {
        save(OWNER_ID, "prescriptions/older.jpg", LocalDate.of(2026, 5, 1), "타이레놀정");
        save(OWNER_ID, "prescriptions/newer.jpg", LocalDate.of(2026, 6, 10), "아스피린정");
        save(OTHER_ID, "prescriptions/other.jpg", LocalDate.of(2026, 6, 20), "남의약");

        UserContext.set(OWNER_ID);
        List<PrescriptionSummary> result = getPrescriptionsUseCase.list();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(PrescriptionSummary::prescribedAt)
                .containsExactly(LocalDate.of(2026, 6, 10), LocalDate.of(2026, 5, 1));
    }

    @Test
    @DisplayName("상세 — 본인 처방전 + 실 drugs 매핑(findById) + presigned imageUrl")
    void detail_ownPrescription_returnsMapped() {
        Long drugId = seedDrug("KD-TEST-001", "타이레놀정500밀리그램");
        given(fileStoragePort.generateGetUrl("prescriptions/p.jpg"))
                .willReturn("https://s3.test/presigned?sig=x");
        Long id = save(OWNER_ID, "prescriptions/p.jpg", LocalDate.of(2026, 6, 1), drugId, "타이레놀정");

        UserContext.set(OWNER_ID);
        PrescriptionDetailResponse detail = getPrescriptionDetailUseCase.detail(id);

        assertThat(detail.id()).isEqualTo(id);
        assertThat(detail.imageUrl()).isEqualTo("https://s3.test/presigned?sig=x");
        assertThat(detail.drugs()).hasSize(1);
        assertThat(detail.drugs().get(0).matchedDrugName()).isEqualTo("타이레놀정500밀리그램");
        assertThat(detail.drugs().get(0).doseUnit()).isEqualTo("정");
    }

    @Test
    @DisplayName("상세 — 타인 처방전 조회 시 PATIENT_ACCESS_DENIED (presigned 미발급)")
    void detail_otherPatient_throwsAccessDenied() {
        Long id = save(OWNER_ID, "prescriptions/secret.jpg", LocalDate.of(2026, 6, 1), "비밀약");

        UserContext.set(OTHER_ID);
        assertThatThrownBy(() -> getPrescriptionDetailUseCase.detail(id))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);

        org.mockito.Mockito.verify(fileStoragePort, org.mockito.Mockito.never()).generateGetUrl(anyString());
    }

    private Long seedDrug(String kdCode, String name) {
        entityManager.createNativeQuery(
                        "INSERT INTO drugs (kd_code, name, status, source, synced_at, version) "
                        + "VALUES (:kd, :name, 'ACTIVE', '식품의약품안전처', now(), 1)")
                .setParameter("kd", kdCode)
                .setParameter("name", name)
                .executeUpdate();
        Number id = (Number) entityManager.createNativeQuery(
                        "SELECT id FROM drugs WHERE kd_code = :kd")
                .setParameter("kd", kdCode)
                .getSingleResult();
        return id.longValue();
    }

    private Long save(Long patientId, String imageKey, LocalDate prescribedAt, String drugName) {
        return save(patientId, imageKey, prescribedAt, null, drugName);
    }

    private Long save(Long patientId, String imageKey, LocalDate prescribedAt, Long drugId, String drugName) {
        Prescription p = Prescription.create(patientId, imageKey, prescribedAt);
        p.addDrug(PrescribedDrug.builder()
                .drugId(drugId).nameRaw(drugName)
                .doseAmount(new BigDecimal("1.00")).doseUnit("정")
                .frequency(3).durationDays(7).confidence(new BigDecimal("0.95"))
                .build());
        return prescriptionRepository.save(p).getId();
    }
}
