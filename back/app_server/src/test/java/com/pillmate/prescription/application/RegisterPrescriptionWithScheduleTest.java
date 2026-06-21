package com.pillmate.prescription.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pillmate.common.security.UserContext;
import com.pillmate.prescription.application.dto.DrugItem;
import com.pillmate.prescription.application.dto.RegisterPrescriptionCommand;
import com.pillmate.prescription.application.dto.RegisterPrescriptionResponse;
import com.pillmate.prescription.application.dto.ScheduleSpec;
import com.pillmate.prescription.application.dto.ScheduleSpec.SlotInput;
import com.pillmate.prescription.application.port.DrugLookupPort;
import com.pillmate.prescription.application.port.SchedulingPort;
import com.pillmate.prescription.application.port.SchedulingPort.CreateScheduleCommand;
import com.pillmate.prescription.application.port.SchedulingPort.ScheduledSlot;
import com.pillmate.prescription.domain.model.Prescription;
import com.pillmate.prescription.domain.repository.PrescriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("RegisterPrescription 스케줄 자동 생성 — scheduleSpec 동반 시 약봉투 단위 스케줄 생성")
@ExtendWith(MockitoExtension.class)
class RegisterPrescriptionWithScheduleTest {

    private static final LocalDate PRESCRIBED_AT = LocalDate.of(2026, 6, 21);

    @Mock PrescriptionRepository prescriptionRepository;
    @Mock DrugLookupPort drugLookupPort;
    @Mock ObjectMapper objectMapper;
    @Mock CheckInteractionsUseCase checkInteractionsUseCase;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock SchedulingPort schedulingPort;
    @InjectMocks RegisterPrescriptionService sut;

    @BeforeEach
    void setUp() {
        UserContext.set(2L);
        lenient().when(checkInteractionsUseCase.check(anyList())).thenReturn(List.of());
        lenient().when(prescriptionRepository.save(any(Prescription.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("scheduleSpec 동반 → 포트 호출(그룹·환자·요청자·기간 정합) 및 createdSchedules 반영")
    void register_withScheduleSpec_invokesPortAndReturnsSchedules() {
        // given
        given(drugLookupPort.findByKdCode("KD-001"))
                .willReturn(Optional.of(new DrugLookupPort.DrugSummary(101L, "KD-001", "타이레놀", null)));
        given(schedulingPort.createForPrescription(any()))
                .willReturn(List.of(new ScheduledSlot(1L, "MORNING", LocalTime.of(8, 0), PRESCRIBED_AT, PRESCRIBED_AT)));
        ScheduleSpec spec = new ScheduleSpec(1L, List.of(new SlotInput("MORNING", LocalTime.of(8, 0))), null, null);

        // when
        RegisterPrescriptionResponse response = sut.register(command(spec, 7));

        // then
        ArgumentCaptor<CreateScheduleCommand> captor = ArgumentCaptor.forClass(CreateScheduleCommand.class);
        verify(schedulingPort).createForPrescription(captor.capture());
        CreateScheduleCommand sent = captor.getValue();
        assertThat(sent.careGroupId()).isEqualTo(1L);
        assertThat(sent.patientId()).isEqualTo(2L);
        assertThat(sent.requesterId()).isEqualTo(2L);
        assertThat(sent.startDate()).isEqualTo(PRESCRIBED_AT);
        assertThat(sent.endDate()).isEqualTo(PRESCRIBED_AT.plusDays(6));
        assertThat(sent.slots()).hasSize(1);
        assertThat(response.createdSchedules()).hasSize(1);
    }

    @Test
    @DisplayName("scheduleSpec 미동반 → 포트 미호출, createdSchedules 빈 목록 (기존 흐름 호환)")
    void register_withoutScheduleSpec_doesNotInvokePort() {
        // given
        given(drugLookupPort.findByKdCode("KD-001"))
                .willReturn(Optional.of(new DrugLookupPort.DrugSummary(101L, "KD-001", "타이레놀", null)));

        // when
        RegisterPrescriptionResponse response = sut.register(command(null, 7));

        // then
        verify(schedulingPort, never()).createForPrescription(any());
        assertThat(response.createdSchedules()).isEmpty();
    }

    @Test
    @DisplayName("기간 미지정 + durationDays 미상 → endDate = 처방일 + 29일 (30일 기본)")
    void register_whenPeriodAndDurationUnknown_defaultsTo30Days() {
        // given
        given(drugLookupPort.findByKdCode("KD-001"))
                .willReturn(Optional.of(new DrugLookupPort.DrugSummary(101L, "KD-001", "타이레놀", null)));
        given(schedulingPort.createForPrescription(any())).willReturn(List.of());
        ScheduleSpec spec = new ScheduleSpec(1L, null, null, null);

        // when
        sut.register(command(spec, null));

        // then
        ArgumentCaptor<CreateScheduleCommand> captor = ArgumentCaptor.forClass(CreateScheduleCommand.class);
        verify(schedulingPort).createForPrescription(captor.capture());
        assertThat(captor.getValue().startDate()).isEqualTo(PRESCRIBED_AT);
        assertThat(captor.getValue().endDate()).isEqualTo(PRESCRIBED_AT.plusDays(29));
        assertThat(captor.getValue().slots()).isNull();
    }

    @Test
    @DisplayName("음수 durationDays → 양수 가드로 무시, DEFAULT 30일 적용")
    void register_whenNegativeDurationDays_usesDefault30Days() {
        // given
        given(drugLookupPort.findByKdCode("KD-001"))
                .willReturn(Optional.of(new DrugLookupPort.DrugSummary(101L, "KD-001", "타이레놀", null)));
        given(schedulingPort.createForPrescription(any())).willReturn(List.of());
        ScheduleSpec spec = new ScheduleSpec(1L, null, null, null);

        // when
        sut.register(command(spec, -5));

        // then
        ArgumentCaptor<CreateScheduleCommand> captor = ArgumentCaptor.forClass(CreateScheduleCommand.class);
        verify(schedulingPort).createForPrescription(captor.capture());
        assertThat(captor.getValue().endDate()).isEqualTo(PRESCRIBED_AT.plusDays(29));
    }

    @Test
    @DisplayName("durationDays = 0 → 양수 가드로 무시, DEFAULT 30일 적용")
    void register_whenZeroDurationDays_usesDefault30Days() {
        // given
        given(drugLookupPort.findByKdCode("KD-001"))
                .willReturn(Optional.of(new DrugLookupPort.DrugSummary(101L, "KD-001", "타이레놀", null)));
        given(schedulingPort.createForPrescription(any())).willReturn(List.of());
        ScheduleSpec spec = new ScheduleSpec(1L, null, null, null);

        // when
        sut.register(command(spec, 0));

        // then
        ArgumentCaptor<CreateScheduleCommand> captor = ArgumentCaptor.forClass(CreateScheduleCommand.class);
        verify(schedulingPort).createForPrescription(captor.capture());
        assertThat(captor.getValue().endDate()).isEqualTo(PRESCRIBED_AT.plusDays(29));
    }

    private RegisterPrescriptionCommand command(ScheduleSpec spec, Integer durationDays) {
        DrugItem item = new DrugItem("KD-001", "타이레놀", new BigDecimal("1.00"), "정",
                3, durationDays, new BigDecimal("0.95"));
        return new RegisterPrescriptionCommand(2L, PRESCRIBED_AT, "prescriptions/x.jpg", List.of(item), spec);
    }
}
