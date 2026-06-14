package com.pillmate.prescription.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.PatientAccessGuard;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.dto.PrescriptionDetailResponse;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.application.port.DrugLookupPort.DrugSummary;
import com.pillmate.prescription.application.port.FileStoragePort;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetPrescriptionDetailUseCase — 처방전 상세 (본인 격리)")
class GetPrescriptionDetailUseCaseTest {

    private static final Long OWNER_ID = 7L;
    private static final Long OTHER_ID = 99L;
    private static final Long PRESCRIPTION_ID = 1L;

    @Mock PrescriptionRepository prescriptionRepository;
    @Mock DrugLookupPort drugLookupPort;
    @Mock FileStoragePort fileStoragePort;

    private GetPrescriptionDetailUseCase sut;

    @BeforeEach
    void setUp() {
        sut = new GetPrescriptionDetailUseCase(
                prescriptionRepository, drugLookupPort, fileStoragePort, new PatientAccessGuard());
    }

    @AfterEach void tearDown() { UserContext.clear(); }

    @Test
    @DisplayName("본인 처방전 상세 — drugs 매핑 + presigned imageUrl")
    void detail_ownPrescription_returnsMappedDetail() {
        UserContext.set(OWNER_ID);
        Prescription p = prescription(OWNER_ID, "prescriptions/uuid.jpg");
        p.addDrug(matchedDrug(101L, "타이레놀정"));
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));
        given(drugLookupPort.findById(101L))
                .willReturn(Optional.of(new DrugSummary(101L, "KD-001", "타이레놀정500밀리그램", "img")));
        given(fileStoragePort.generateGetUrl("prescriptions/uuid.jpg"))
                .willReturn("https://s3.test/presigned?sig=x");

        PrescriptionDetailResponse detail = sut.detail(PRESCRIPTION_ID);

        assertThat(detail.imageUrl()).isEqualTo("https://s3.test/presigned?sig=x");
        assertThat(detail.drugs()).hasSize(1);
        assertThat(detail.drugs().get(0).nameRaw()).isEqualTo("타이레놀정");
        assertThat(detail.drugs().get(0).matchedDrugName()).isEqualTo("타이레놀정500밀리그램");
    }

    @Test
    @DisplayName("타인 처방전 조회 — PATIENT_ACCESS_DENIED (403)")
    void detail_otherPatient_throwsAccessDenied() {
        UserContext.set(OTHER_ID);
        Prescription p = prescription(OWNER_ID, "prescriptions/uuid.jpg");
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> sut.detail(PRESCRIPTION_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);

        verify(fileStoragePort, never()).generateGetUrl(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("미매칭 약품(drugId null) — matchedDrugName null")
    void detail_unmatchedDrug_matchedNameNull() {
        UserContext.set(OWNER_ID);
        Prescription p = prescription(OWNER_ID, "prescriptions/uuid.jpg");
        p.addDrug(unmatchedDrug("동광나자티딘캡슐"));
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));
        lenient().when(fileStoragePort.generateGetUrl("prescriptions/uuid.jpg"))
                .thenReturn("https://s3.test/x");

        PrescriptionDetailResponse detail = sut.detail(PRESCRIPTION_ID);

        assertThat(detail.drugs().get(0).matchedDrugName()).isNull();
        verify(drugLookupPort, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("imageKey 없으면 imageUrl null — presigned 발급 안 함")
    void detail_noImageKey_imageUrlNull() {
        UserContext.set(OWNER_ID);
        Prescription p = prescription(OWNER_ID, null);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));

        PrescriptionDetailResponse detail = sut.detail(PRESCRIPTION_ID);

        assertThat(detail.imageUrl()).isNull();
        verify(fileStoragePort, never()).generateGetUrl(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("존재하지 않는 처방전 — PRESCRIPTION_NOT_FOUND")
    void detail_notFound_throws() {
        UserContext.set(OWNER_ID);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.detail(PRESCRIPTION_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRESCRIPTION_NOT_FOUND);
    }

    private Prescription prescription(Long patientId, String imageKey) {
        Prescription p = Prescription.create(patientId, imageKey, LocalDate.of(2026, 6, 1));
        ReflectionTestUtils.setField(p, "id", PRESCRIPTION_ID);
        return p;
    }

    private PrescribedDrug matchedDrug(Long drugId, String nameRaw) {
        return PrescribedDrug.builder()
                .drugId(drugId).nameRaw(nameRaw)
                .doseAmount(new BigDecimal("1.00")).doseUnit("정")
                .frequency(3).durationDays(7).confidence(new BigDecimal("0.95"))
                .build();
    }

    private PrescribedDrug unmatchedDrug(String nameRaw) {
        return PrescribedDrug.builder()
                .nameRaw(nameRaw)
                .doseAmount(new BigDecimal("1.00")).doseUnit("정")
                .frequency(3).durationDays(7).confidence(new BigDecimal("0.95"))
                .build();
    }
}
