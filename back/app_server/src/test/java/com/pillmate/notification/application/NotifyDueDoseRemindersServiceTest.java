package com.pillmate.notification.application;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.notification.application.port.DrugNameLookupPort;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import com.pillmate.notification.application.port.PrescriptionSummaryPort;
import com.pillmate.notification.application.port.PrescriptionSummaryPort.PrescriptionSummary;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.model.NotificationType;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import com.pillmate.user.domain.model.PushProvider;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("NotifyDueDoseRemindersService — 복약 시간 도래 시 환자 본인 리마인더")
@ExtendWith(MockitoExtension.class)
class NotifyDueDoseRemindersServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-13T10:00:00Z");
    private static final Duration RECENCY_WINDOW = Duration.ofMinutes(10);

    @Mock DoseLogRepository doseLogRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock NotificationPersistenceService notificationPersistenceService;
    @Mock UserRepository userRepository;
    @Mock NotificationSenderPort notificationSenderPort;
    @Mock PrescriptionSummaryPort prescriptionSummaryPort;
    @Mock DrugNameLookupPort drugNameLookupPort;
    @Spy  Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    @InjectMocks NotifyDueDoseRemindersService sut;

    private static final Long PATIENT_ID = 5L;
    private static final Long DOSE_LOG_ID = 100L;
    private static final Long SCHEDULE_ID = 44L;
    private static final Long PRESCRIPTION_ID = 49L;

    @Test
    @DisplayName("윈도우 [now-10분, now] 로만 조회 — 과거 행 폭주 방지 (그룹 폴러 선례)")
    void notifyDue_queriesRecencyWindow() {
        given(doseLogRepository.findPendingNotRemindedBetween(any(), any())).willReturn(List.of());

        int sent = sut.notifyDue();

        verify(doseLogRepository).findPendingNotRemindedBetween(
                FIXED_NOW.minus(RECENCY_WINDOW), FIXED_NOW);
        assertThat(sent).isZero();
    }

    @Test
    @DisplayName("솔로(careGroupId null) 스케줄 — NPE 없이 careGroupId null 알림 저장 + 본인 토큰으로 발송")
    void notifyDue_soloSchedule_sendsToPatientSelf() {
        DoseLog doseLog = pendingDoseLog();
        Schedule schedule = prescriptionSchedule(null, TimeOfDay.MORNING, null);
        stubHappyPath(doseLog, schedule, "솔로 테스트 약봉투");

        sut.notifyDue();

        ArgumentCaptor<List<Notification>> notifCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(notifCaptor.capture());
        Notification n = notifCaptor.getValue().get(0);
        assertThat(n.getRecipientUserId()).isEqualTo(PATIENT_ID);
        assertThat(n.getCareGroupId()).isNull();
        assertThat(n.getType()).isEqualTo(NotificationType.DOSE_REMINDER);
        assertThat(n.getDoseLogId()).isEqualTo(DOSE_LOG_ID);

        ArgumentCaptor<List<NotificationCommand>> cmdCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationSenderPort).sendAll(cmdCaptor.capture());
        NotificationCommand cmd = cmdCaptor.getValue().get(0);
        assertThat(cmd.recipientUserId()).isEqualTo(PATIENT_ID);
        assertThat(cmd.recipientPushToken()).isEqualTo("ExponentPushToken[me]");
        assertThat(cmd.data()).containsEntry("route", "/home");
    }

    @Test
    @DisplayName("본문 — [그룹명]·약이름 나열 금지, 약봉투 label + 시간대(아침) 사용")
    void notifyDue_bodyUsesLabelAndTimeOfDay() {
        DoseLog doseLog = pendingDoseLog();
        Schedule schedule = prescriptionSchedule(20L, TimeOfDay.MORNING, null);
        stubHappyPath(doseLog, schedule, "저녁약");

        sut.notifyDue();

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        Notification n = captor.getValue().get(0);
        assertThat(n.getTitle()).contains("복약 시간");
        assertThat(n.getBody()).contains("저녁약");
        assertThat(n.getBody()).contains("아침");
        assertThat(n.getBody()).doesNotContain("[");
    }

    @Test
    @DisplayName("label blank → 'M월 D일 약봉투' fallback (기존 표기 규칙 동일)")
    void notifyDue_whenLabelBlank_usesDateFallback() {
        DoseLog doseLog = pendingDoseLog();
        Schedule schedule = prescriptionSchedule(null, TimeOfDay.EVENING, null);
        stubHappyPath(doseLog, schedule, "   ");

        sut.notifyDue();

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getBody()).contains("7월 13일 약봉투");
        assertThat(captor.getValue().get(0).getBody()).contains("저녁");
    }

    @Test
    @DisplayName("기본 시각이 아닌 custom 시각(09:30) → 시간대 대신 HH:mm 표기")
    void notifyDue_customTime_usesHhMmLabel() {
        DoseLog doseLog = pendingDoseLog();
        Schedule schedule = prescriptionSchedule(null, TimeOfDay.MORNING, LocalTime.of(9, 30));
        stubHappyPath(doseLog, schedule, "혈압약");

        sut.notifyDue();

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getBody()).contains("09:30");
    }

    @Test
    @DisplayName("처방전 없는 레거시 약 스케줄 — 약 이름으로 fallback")
    void notifyDue_drugSchedule_usesDrugName() {
        DoseLog doseLog = pendingDoseLog();
        Schedule schedule = Schedule.of(null, PATIENT_ID, 7L, TimeOfDay.NOON,
                LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 20), PATIENT_ID);
        given(doseLogRepository.findPendingNotRemindedBetween(any(), any())).willReturn(List.of(doseLog));
        given(doseLogRepository.markRemindedIfPending(DOSE_LOG_ID, FIXED_NOW)).willReturn(1);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(drugNameLookupPort.findNameById(7L)).willReturn(Optional.of("타이레놀정500밀리그램"));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(PATIENT_ID)).willReturn(Optional.of(patientWithToken()));

        sut.notifyDue();

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getBody()).contains("타이레놀정500밀리그램");
        verify(prescriptionSummaryPort, never()).findById(any());
    }

    // ─── T-BE-REMINDER-FIX-ATOMIC-MARK — P0/P1-1 조건부 원자 클레임 ───

    @Test
    @DisplayName("P0: 마킹은 조건부 원자 UPDATE 만 사용 — detached 엔티티 save 절대 없음 (TAKEN 되돌림 원천 차단)")
    void notifyDue_neverSavesDetachedEntity() {
        DoseLog doseLog = pendingDoseLog();
        Schedule schedule = prescriptionSchedule(null, TimeOfDay.MORNING, null);
        stubHappyPath(doseLog, schedule, "약봉투A");

        sut.notifyDue();

        verify(doseLogRepository).markRemindedIfPending(DOSE_LOG_ID, FIXED_NOW);
        verify(doseLogRepository, never()).save(any(DoseLog.class));
        verify(doseLogRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("P0: 조회 후 발송 전 TAKEN 전환(영향행수 0) → dispatch 없음 + 엔티티 save 없음")
    void notifyDue_whenClaimLostToConcurrentTake_skipsDispatch() {
        DoseLog doseLog = pendingDoseLog();
        given(doseLogRepository.findPendingNotRemindedBetween(any(), any())).willReturn(List.of(doseLog));
        given(doseLogRepository.markRemindedIfPending(DOSE_LOG_ID, FIXED_NOW)).willReturn(0);

        sut.notifyDue();

        verify(scheduleRepository, never()).findById(any());
        verify(notificationPersistenceService, never()).saveAll(anyList());
        verify(notificationSenderPort, never()).sendAll(anyList());
        verify(doseLogRepository, never()).save(any(DoseLog.class));
    }

    @Test
    @DisplayName("P1-1: 다른 인스턴스 선점(이미 reminded, 영향행수 0) → 중복 발송 0")
    void notifyDue_whenClaimedByOtherInstance_noDuplicateSend() {
        DoseLog doseLog = pendingDoseLog();
        given(doseLogRepository.findPendingNotRemindedBetween(any(), any())).willReturn(List.of(doseLog));
        given(doseLogRepository.markRemindedIfPending(DOSE_LOG_ID, FIXED_NOW)).willReturn(0);

        sut.notifyDue();

        verify(notificationSenderPort, never()).sendAll(anyList());
    }

    @Test
    @DisplayName("푸시 토큰 없는 환자 — 발송 command 는 token null(포트가 skip), 클레임은 수행")
    void notifyDue_whenNoToken_stillClaims() {
        DoseLog doseLog = pendingDoseLog();
        Schedule schedule = prescriptionSchedule(null, TimeOfDay.MORNING, null);
        given(doseLogRepository.findPendingNotRemindedBetween(any(), any())).willReturn(List.of(doseLog));
        given(doseLogRepository.markRemindedIfPending(DOSE_LOG_ID, FIXED_NOW)).willReturn(1);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(prescriptionSummaryPort.findById(PRESCRIPTION_ID))
                .willReturn(Optional.of(new PrescriptionSummary(LocalDate.of(2026, 7, 13), "약봉투A")));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(PATIENT_ID)).willReturn(Optional.of(patientWithoutToken()));

        sut.notifyDue();

        verify(doseLogRepository).markRemindedIfPending(DOSE_LOG_ID, FIXED_NOW);
        ArgumentCaptor<List<NotificationCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationSenderPort).sendAll(captor.capture());
        assertThat(captor.getValue().get(0).recipientPushToken()).isNull();
    }

    @Test
    @DisplayName("T-BE-REMINDER-FIX-2: 비활성(soft-delete 등) 스케줄 — 클레임만 남기고 발송 없음 (defense in depth)")
    void notifyDue_whenScheduleInactive_claimsWithoutSending() {
        DoseLog doseLog = pendingDoseLog();
        Schedule inactive = prescriptionSchedule(null, TimeOfDay.MORNING, null);
        inactive.deactivate();
        given(doseLogRepository.findPendingNotRemindedBetween(any(), any())).willReturn(List.of(doseLog));
        given(doseLogRepository.markRemindedIfPending(DOSE_LOG_ID, FIXED_NOW)).willReturn(1);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(inactive));

        sut.notifyDue();

        verify(notificationPersistenceService, never()).saveAll(anyList());
        verify(notificationSenderPort, never()).sendAll(anyList());
    }

    @Test
    @DisplayName("스케줄 미조회(비활성 등) — 클레임만 남기고 발송 없음 (폴러 무한 재조회 방지)")
    void notifyDue_whenScheduleMissing_claimsWithoutSending() {
        DoseLog doseLog = pendingDoseLog();
        given(doseLogRepository.findPendingNotRemindedBetween(any(), any())).willReturn(List.of(doseLog));
        given(doseLogRepository.markRemindedIfPending(DOSE_LOG_ID, FIXED_NOW)).willReturn(1);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.empty());

        sut.notifyDue();

        verify(doseLogRepository).markRemindedIfPending(DOSE_LOG_ID, FIXED_NOW);
        verify(notificationSenderPort, never()).sendAll(anyList());
    }

    @Test
    @DisplayName("한 행 처리 실패해도 나머지 행 계속 처리")
    void notifyDue_whenOneFails_continuesOthers() {
        DoseLog failing = pendingDoseLog();
        DoseLog ok = DoseLog.of(SCHEDULE_ID, PATIENT_ID, FIXED_NOW.minusSeconds(60));
        org.springframework.test.util.ReflectionTestUtils.setField(ok, "id", 101L);
        Schedule schedule = prescriptionSchedule(null, TimeOfDay.MORNING, null);

        given(doseLogRepository.findPendingNotRemindedBetween(any(), any()))
                .willReturn(List.of(failing, ok));
        given(doseLogRepository.markRemindedIfPending(eq(DOSE_LOG_ID), any()))
                .willThrow(new RuntimeException("DB 순간 장애"));
        given(doseLogRepository.markRemindedIfPending(eq(101L), any())).willReturn(1);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(prescriptionSummaryPort.findById(PRESCRIPTION_ID))
                .willReturn(Optional.of(new PrescriptionSummary(LocalDate.of(2026, 7, 13), "약봉투A")));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(PATIENT_ID)).willReturn(Optional.of(patientWithToken()));

        int processed = sut.notifyDue();

        assertThat(processed).isEqualTo(2);
        verify(notificationSenderPort).sendAll(anyList());
    }

    private void stubHappyPath(DoseLog doseLog, Schedule schedule, String label) {
        given(doseLogRepository.findPendingNotRemindedBetween(any(), any())).willReturn(List.of(doseLog));
        given(doseLogRepository.markRemindedIfPending(DOSE_LOG_ID, FIXED_NOW)).willReturn(1);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(prescriptionSummaryPort.findById(PRESCRIPTION_ID))
                .willReturn(Optional.of(new PrescriptionSummary(LocalDate.of(2026, 7, 13), label)));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(PATIENT_ID)).willReturn(Optional.of(patientWithToken()));
    }

    private DoseLog pendingDoseLog() {
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, FIXED_NOW.minusSeconds(120));
        org.springframework.test.util.ReflectionTestUtils.setField(doseLog, "id", DOSE_LOG_ID);
        return doseLog;
    }

    private Schedule prescriptionSchedule(Long careGroupId, TimeOfDay timeOfDay, LocalTime customTime) {
        return Schedule.forPrescription(careGroupId, PATIENT_ID, PRESCRIPTION_ID,
                timeOfDay, customTime, LocalDate.of(2026, 7, 13), LocalDate.of(2026, 7, 20), PATIENT_ID);
    }

    private User patientWithToken() {
        User user = User.dummy("환자");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", PATIENT_ID);
        user.registerPushToken("ExponentPushToken[me]", PushProvider.EXPO);
        return user;
    }

    private User patientWithoutToken() {
        User user = User.dummy("환자");
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", PATIENT_ID);
        return user;
    }
}
