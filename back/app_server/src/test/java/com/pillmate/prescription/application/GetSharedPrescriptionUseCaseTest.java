package com.pillmate.prescription.application;

import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.prescription.application.dto.NutrientNote;
import com.pillmate.prescription.application.dto.SharedPrescriptionResponse;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.application.port.DrugLookupPort.DrugSummary;
import com.pillmate.prescription.application.port.NutrientDepletionPort;
import com.pillmate.prescription.application.port.PrescriptionPeriodPort;
import com.pillmate.prescription.domain.model.PrescribedDrug;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("GetSharedPrescriptionUseCase — 케어그룹 알약 정보 공유 상세")
class GetSharedPrescriptionUseCaseTest {

    private static final Long GROUP_ID = 42L;
    private static final Long OWNER_ID = 7L;
    private static final Long VIEWER_ID = 8L;
    private static final Long PRESCRIPTION_ID = 1L;

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-06-15T00:00:00Z"), ZoneId.of("UTC"));

    @Mock PrescriptionRepository prescriptionRepository;
    @Mock DrugLookupPort drugLookupPort;
    @Mock NutrientDepletionPort nutrientDepletionPort;
    @Mock PrescriptionPeriodPort prescriptionPeriodPort;
    @Mock MembershipRepository membershipRepository;

    private GetSharedPrescriptionUseCase sut;

    @BeforeEach
    void setUp() {
        lenient().when(prescriptionPeriodPort.fetchStatsByPrescriptionIds(List.of(PRESCRIPTION_ID)))
                .thenReturn(Map.of());
        lenient().when(drugLookupPort.findByIds(anyCollection())).thenReturn(Map.of());
        lenient().when(nutrientDepletionPort.findByDrugIds(anyCollection())).thenReturn(Map.of());
        PrescriptionViewAssembler assembler = new PrescriptionViewAssembler(drugLookupPort, nutrientDepletionPort);
        sut = new GetSharedPrescriptionUseCase(
                prescriptionRepository, assembler, prescriptionPeriodPort, membershipRepository, FIXED_CLOCK);
    }

    @Test
    @DisplayName("공유 꺼짐 — MEDICATION_SHARE_NOT_GRANTED, 약 정보 조회 미실행")
    void execute_notShared_throws() {
        Prescription p = prescription(OWNER_ID, GROUP_ID, false);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> sut.execute(GROUP_ID, PRESCRIPTION_ID, VIEWER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDICATION_SHARE_NOT_GRANTED);

        then(drugLookupPort).should(never()).findByIds(anyCollection());
    }

    @Test
    @DisplayName("본인 조회 — 공유 꺼짐이어도 정상 반환")
    void execute_ownerIsViewer_returnsDetail() {
        Prescription p = prescription(OWNER_ID, GROUP_ID, false);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));

        SharedPrescriptionResponse response = sut.execute(GROUP_ID, PRESCRIPTION_ID, OWNER_ID);

        assertThat(response.id()).isEqualTo(PRESCRIPTION_ID);
        assertThat(response.ownerUserId()).isEqualTo(OWNER_ID);
        then(membershipRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("공유 켜짐 + 둘 다 ACTIVE 멤버인 뷰어 — 약 이름/용량/알약 이미지/kdCode/영양소 노트 매핑")
    void execute_sharedViewer_mapsDrugDetail() {
        Prescription p = prescription(OWNER_ID, GROUP_ID, true);
        p.addDrug(matchedDrug(101L, "메트포르민정"));
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, VIEWER_ID)).willReturn(true);
        given(drugLookupPort.findByIds(List.of(101L)))
                .willReturn(Map.of(101L, new DrugSummary(101L, "KD-999", "메트포르민정500밀리그램", "https://img.test/m.png")));
        given(nutrientDepletionPort.findByDrugIds(List.of(101L)))
                .willReturn(Map.of(101L, List.of(
                        new NutrientNote("비타민 B12", "장기 복용 시 흡수에 영향을 줄 수 있어요.", "식품의약품안전처 의약품정보"))));

        SharedPrescriptionResponse response = sut.execute(GROUP_ID, PRESCRIPTION_ID, VIEWER_ID);

        SharedPrescriptionResponse.SharedDrugDetail drug = response.drugs().get(0);
        assertThat(drug.nameRaw()).isEqualTo("메트포르민정");
        assertThat(drug.matchedDrugName()).isEqualTo("메트포르민정500밀리그램");
        assertThat(drug.matchedKdCode()).isEqualTo("KD-999");
        assertThat(drug.imageUrl()).isEqualTo("https://img.test/m.png");
        assertThat(drug.nutrientNotes()).hasSize(1);
        assertThat(drug.nutrientNotes().get(0).nutrient()).isEqualTo("비타민 B12");
    }

    @Test
    @DisplayName("크로스그룹 차단 — 약봉투 소속 그룹과 요청 groupId 다르면 공유 켬이어도 거부")
    void execute_crossGroup_throws() {
        Long otherGroupId = 99L;
        Prescription p = prescription(OWNER_ID, otherGroupId, true);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));

        assertThatThrownBy(() -> sut.execute(GROUP_ID, PRESCRIPTION_ID, VIEWER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDICATION_SHARE_NOT_GRANTED);
        then(drugLookupPort).should(never()).findByIds(anyCollection());
    }

    @Test
    @DisplayName("공유 켜짐이지만 viewer 가 그룹 비멤버(탈퇴 포함) — 거부")
    void execute_viewerNotActiveMember_throws() {
        Prescription p = prescription(OWNER_ID, GROUP_ID, true);
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.of(p));
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, OWNER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, VIEWER_ID)).willReturn(false);

        assertThatThrownBy(() -> sut.execute(GROUP_ID, PRESCRIPTION_ID, VIEWER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.MEDICATION_SHARE_NOT_GRANTED);
    }

    @Test
    @DisplayName("존재하지 않는 처방전 — PRESCRIPTION_NOT_FOUND, 권한 판정 미실행")
    void execute_notFound_throws() {
        given(prescriptionRepository.findById(PRESCRIPTION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.execute(GROUP_ID, PRESCRIPTION_ID, VIEWER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRESCRIPTION_NOT_FOUND);

        then(membershipRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("민감정보 미포함 — 처방전 원본 사진·메모·증상·AI인사이트·OCR신뢰도는 응답 record 에 없음")
    void response_doesNotDeclareSensitiveFields() {
        Set<String> topLevelFields = fieldNamesOf(SharedPrescriptionResponse.class);
        assertThat(topLevelFields).doesNotContain("imageUrl", "memo", "symptom", "insights", "confidence");

        Set<String> drugFields = fieldNamesOf(SharedPrescriptionResponse.SharedDrugDetail.class);
        assertThat(drugFields).doesNotContain("confidence");
        // 알약(drug master) 사진은 허용 대상 — 처방전 원본 사진과는 별개
        assertThat(drugFields).contains("imageUrl");
    }

    private Set<String> fieldNamesOf(Class<?> recordType) {
        return Arrays.stream(recordType.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(java.util.stream.Collectors.toSet());
    }

    private Prescription prescription(Long patientId, Long careGroupId, boolean sharedWithGroup) {
        Prescription p = Prescription.create(patientId, "prescriptions/uuid.jpg", LocalDate.of(2026, 6, 1));
        ReflectionTestUtils.setField(p, "id", PRESCRIPTION_ID);
        ReflectionTestUtils.setField(p, "careGroupId", careGroupId);
        if (sharedWithGroup) {
            p.shareWithGroup();
        }
        return p;
    }

    private PrescribedDrug matchedDrug(Long drugId, String nameRaw) {
        return PrescribedDrug.builder()
                .drugId(drugId).nameRaw(nameRaw)
                .doseAmount(new BigDecimal("1.00")).doseUnit("정")
                .frequency(3).durationDays(7).confidence(new BigDecimal("0.95"))
                .build();
    }
}
