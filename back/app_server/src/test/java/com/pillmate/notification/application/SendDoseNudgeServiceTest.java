package com.pillmate.notification.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.notification.application.dto.NudgeResponse;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import com.pillmate.notification.application.port.NudgeCooldownPort;
import com.pillmate.notification.domain.model.Notification;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("SendDoseNudgeService — 그룹원 → 당사자 수동 넛지")
@ExtendWith(MockitoExtension.class)
class SendDoseNudgeServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-12T10:00:00Z");
    private static final Long PATIENT_ID  = 1L;
    private static final Long FROM_USER_ID = 2L;
    private static final Long DOSE_LOG_ID = 5L;
    private static final Long SCHEDULE_ID = 10L;
    private static final Long GROUP_ID    = 20L;

    @Mock DoseLogRepository doseLogRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock CareGroupGuard careGroupGuard;
    @Mock NudgeCooldownPort nudgeCooldownPort;
    @Mock UserRepository userRepository;
    @Mock NotificationPersistenceService notificationPersistenceService;
    @Mock NotificationSenderPort notificationSenderPort;
    @Spy  Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    @InjectMocks SendDoseNudgeService sut;

    @Test
    @DisplayName("비 ACTIVE 그룹원 — GROUP_ACCESS_DENIED (403)")
    void nudge_whenNotGroupMember_throwsAccessDenied() {
        DoseLog doseLog = pendingDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        org.mockito.BDDMockito.willThrow(new PillmateException(ErrorCode.GROUP_ACCESS_DENIED))
                .given(careGroupGuard).requireAccessible(GROUP_ID);

        assertThatThrownBy(() -> sut.nudge(DOSE_LOG_ID, FROM_USER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);

        verify(notificationPersistenceService, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("이미 TAKEN 인 dose — DOSE_LOG_ALREADY_CHECKED (409)")
    void nudge_whenAlreadyTaken_throwsAlreadyChecked() {
        DoseLog doseLog = pendingDoseLog();
        doseLog.take(PATIENT_ID, clock);
        Schedule schedule = scheduleOf(GROUP_ID);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));

        assertThatThrownBy(() -> sut.nudge(DOSE_LOG_ID, FROM_USER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DOSE_LOG_ALREADY_CHECKED);

        verify(notificationPersistenceService, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("쿨다운 중(10분 이내 재요청) — NUDGE_COOLDOWN_ACTIVE (429)")
    void nudge_whenCooldownActive_throwsCooldownActive() {
        DoseLog doseLog = pendingDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(nudgeCooldownPort.tryAcquire(DOSE_LOG_ID, FROM_USER_ID, Duration.ofMinutes(10)))
                .willReturn(false);

        assertThatThrownBy(() -> sut.nudge(DOSE_LOG_ID, FROM_USER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NUDGE_COOLDOWN_ACTIVE);

        verify(notificationPersistenceService, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("정상 — ACTIVE 멤버 + PENDING + 쿨다운 통과 → 당사자에게 발송, dose-reminder 채널")
    void nudge_whenValid_sendsToPatient() {
        DoseLog doseLog = pendingDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User fromUser = userOf(FROM_USER_ID, "김철수");
        User patient = patientWithToken("ExponentPushToken[patient]");

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(nudgeCooldownPort.tryAcquire(DOSE_LOG_ID, FROM_USER_ID, Duration.ofMinutes(10)))
                .willReturn(true);
        given(nudgeCooldownPort.acquireRecipientCap(PATIENT_ID, Duration.ofMinutes(10)))
                .willReturn(true);
        given(userRepository.findById(FROM_USER_ID)).willReturn(Optional.of(fromUser));
        given(userRepository.findById(PATIENT_ID)).willReturn(Optional.of(patient));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(notificationSenderPort.sendAll(anyList())).willReturn(List.of(1L));

        NudgeResponse response = sut.nudge(DOSE_LOG_ID, FROM_USER_ID);

        assertThat(response.alreadyNotified()).isFalse();
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        Notification saved = captor.getValue().get(0);
        assertThat(saved.getRecipientUserId()).isEqualTo(PATIENT_ID);
        assertThat(saved.getActorUserId()).isEqualTo(FROM_USER_ID);
        assertThat(saved.getBody()).isEqualTo("김철수님이 약 챙기라고 알려드려요");

        ArgumentCaptor<List<NotificationCommand>> cmdCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationSenderPort).sendAll(cmdCaptor.capture());
        NotificationCommand cmd = cmdCaptor.getValue().get(0);
        assertThat(cmd.recipientPushToken()).isEqualTo("ExponentPushToken[patient]");
        assertThat(cmd.data()).containsEntry("channel", "dose-reminder");
        verify(notificationPersistenceService).markSent(1L, FIXED_NOW);
    }

    @Test
    @DisplayName("당사자 총량 캡 소진(다른 발신자·다른 dose 로 방금 알림받음) — FCM 미발송 + 200 alreadyNotified=true")
    void nudge_whenRecipientCapExhausted_skipsFcmAndReturnsAlreadyNotified() {
        DoseLog doseLog = pendingDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(nudgeCooldownPort.tryAcquire(DOSE_LOG_ID, FROM_USER_ID, Duration.ofMinutes(10)))
                .willReturn(true);
        given(nudgeCooldownPort.acquireRecipientCap(PATIENT_ID, Duration.ofMinutes(10)))
                .willReturn(false);

        NudgeResponse response = sut.nudge(DOSE_LOG_ID, FROM_USER_ID);

        assertThat(response.alreadyNotified()).isTrue();
        verify(notificationPersistenceService, never()).saveAll(anyList());
        verify(notificationSenderPort, never()).sendAll(anyList());
    }

    private User userOf(Long id, String name) {
        User user = User.dummy(name);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private User patientWithToken(String token) {
        User user = User.dummy("환자");
        ReflectionTestUtils.setField(user, "id", PATIENT_ID);
        user.registerPushToken(token, PushProvider.EXPO);
        return user;
    }

    private DoseLog pendingDoseLog() {
        DoseLog log = DoseLog.of(SCHEDULE_ID, PATIENT_ID, FIXED_NOW.minusSeconds(600));
        ReflectionTestUtils.setField(log, "id", DOSE_LOG_ID);
        return log;
    }

    private Schedule scheduleOf(Long careGroupId) {
        return Schedule.of(careGroupId, PATIENT_ID, 1L, TimeOfDay.MORNING,
                LocalDate.now(), LocalDate.now().plusDays(30), PATIENT_ID);
    }
}
