package com.pillmate.notification.application;

import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import com.pillmate.notification.application.port.RecipientCachePort;
import com.pillmate.notification.application.port.RecipientCachePort.CachedRecipient;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import com.pillmate.user.domain.model.PushProvider;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("SendOverdueDoseNotificationService — 단위 테스트")
@ExtendWith(MockitoExtension.class)
class SendOverdueDoseNotificationServiceTest {

    // KST 08:00 == UTC 전일 23:00
    private static final Instant SCHEDULED_AT = Instant.parse("2026-06-11T23:00:00Z");
    private static final Instant FIXED_NOW = SCHEDULED_AT.plusSeconds(1800);

    @Mock DoseLogRepository doseLogRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock NotificationPersistenceService notificationPersistenceService;
    @Mock UserRepository userRepository;
    @Mock NotificationSenderPort notificationSenderPort;
    @Mock RecipientCachePort recipientCachePort;
    @Spy  Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    @InjectMocks SendOverdueDoseNotificationService sut;

    private static final Long PATIENT_ID  = 1L;
    private static final Long MEMBER_ID   = 2L;
    private static final Long DOSE_LOG_ID = 5L;
    private static final Long SCHEDULE_ID = 10L;
    private static final Long GROUP_ID    = 20L;

    @BeforeEach
    void setUp() {
        lenient().when(userRepository.findById(PATIENT_ID)).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("DoseLog 없으면 INVALID_NOTIFICATION_DOSE_LOG")
    void send_whenDoseLogNotFound_throws() {
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.send(DOSE_LOG_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_NOTIFICATION_DOSE_LOG);
    }

    @Test
    @DisplayName("비활성 스케줄이면 발송 없이 종료")
    void send_whenScheduleInactive_skips() {
        DoseLog doseLog = pendingDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        schedule.deactivate();
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));

        sut.send(DOSE_LOG_ID);

        verify(notificationPersistenceService, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("솔로(careGroupId null) — 당사자 본인 알림만 저장·발송, dose-reminder 채널")
    void send_soloPatient_onlySelfNotification() {
        DoseLog doseLog = pendingDoseLog();
        Schedule schedule = scheduleOf(null);
        User patient = patientWithToken("ExponentPushToken[patient]");
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(userRepository.findById(PATIENT_ID)).willReturn(Optional.of(patient));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        sut.send(DOSE_LOG_ID);

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        Notification self = captor.getValue().get(0);
        assertThat(self.getRecipientUserId()).isEqualTo(PATIENT_ID);
        assertThat(self.getBody()).isEqualTo("오전 8시 약을 아직 안 드셨어요");

        ArgumentCaptor<List<NotificationCommand>> cmdCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationSenderPort).sendAll(cmdCaptor.capture());
        assertThat(cmdCaptor.getValue()).hasSize(1);
        assertThat(cmdCaptor.getValue().get(0).data()).containsEntry("channel", "dose-reminder");
        assertThat(cmdCaptor.getValue().get(0).recipientPushToken()).isEqualTo("ExponentPushToken[patient]");
        verify(membershipRepository, never()).findByCareGroupId(any());
    }

    @Test
    @DisplayName("그룹 있음 — 당사자(자가, dose-reminder) + 다른 ACTIVE 그룹원(3인칭, group-activity) 모두 발송")
    void send_withGroup_sendsSelfAndGroupNotifications() {
        DoseLog doseLog = pendingDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User patient = patientWithToken("ExponentPushToken[patient]");
        User member = memberWithToken(MEMBER_ID, "ExponentPushToken[member]");

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(userRepository.findById(PATIENT_ID)).willReturn(Optional.of(patient));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, PATIENT_ID), membershipOf(GROUP_ID, MEMBER_ID)));
        given(userRepository.findAllByIdIn(List.of(PATIENT_ID, MEMBER_ID)))
                .willReturn(List.of(patient, member));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        sut.send(DOSE_LOG_ID);

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);

        Notification self = captor.getValue().stream()
                .filter(n -> n.getRecipientUserId().equals(PATIENT_ID)).findFirst().orElseThrow();
        assertThat(self.getBody()).isEqualTo("오전 8시 약을 아직 안 드셨어요");

        Notification group = captor.getValue().stream()
                .filter(n -> n.getRecipientUserId().equals(MEMBER_ID)).findFirst().orElseThrow();
        assertThat(group.getBody()).contains("님이").contains("오전 8시");
        assertThat(group.getActorUserId()).isEqualTo(PATIENT_ID);

        ArgumentCaptor<List<NotificationCommand>> cmdCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationSenderPort).sendAll(cmdCaptor.capture());
        assertThat(cmdCaptor.getValue()).hasSize(2);
        NotificationCommand selfCmd = cmdCaptor.getValue().stream()
                .filter(c -> c.recipientUserId().equals(PATIENT_ID)).findFirst().orElseThrow();
        NotificationCommand groupCmd = cmdCaptor.getValue().stream()
                .filter(c -> c.recipientUserId().equals(MEMBER_ID)).findFirst().orElseThrow();
        assertThat(selfCmd.data()).containsEntry("channel", "dose-reminder");
        assertThat(groupCmd.data()).doesNotContainKey("channel");
    }

    @Test
    @DisplayName("그룹원 목록에 환자 본인 포함되어도 group 알림에서는 제외 (자가 알림과 중복 방지)")
    void send_excludesPatientFromGroupNotifications() {
        DoseLog doseLog = pendingDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User patient = patientWithToken("ExponentPushToken[patient]");

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(userRepository.findById(PATIENT_ID)).willReturn(Optional.of(patient));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, PATIENT_ID)));
        given(userRepository.findAllByIdIn(List.of(PATIENT_ID))).willReturn(List.of(patient));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        sut.send(DOSE_LOG_ID);

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getRecipientUserId()).isEqualTo(PATIENT_ID);
    }

    @Test
    @DisplayName("캐시 hit — membership/유저 DB 미조회, 캐시 수신자로 그룹 알림 발송")
    void send_recipientCacheHit_skipsDb() {
        DoseLog doseLog = pendingDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User patient = patientWithToken("ExponentPushToken[patient]");
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(userRepository.findById(PATIENT_ID)).willReturn(Optional.of(patient));
        given(recipientCachePort.get(GROUP_ID)).willReturn(Optional.of(List.of(
                new CachedRecipient(PATIENT_ID, "ExponentPushToken[patient]"),
                new CachedRecipient(MEMBER_ID, "ExponentPushToken[cached]"))));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        sut.send(DOSE_LOG_ID);

        verify(membershipRepository, never()).findByCareGroupId(any());
        verify(userRepository, never()).findAllByIdIn(anyList());
        ArgumentCaptor<List<NotificationCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationSenderPort).sendAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
    }

    private User patientWithToken(String token) {
        User user = User.dummy("환자");
        ReflectionTestUtils.setField(user, "id", PATIENT_ID);
        user.registerPushToken(token, PushProvider.EXPO);
        return user;
    }

    private User memberWithToken(Long id, String token) {
        User user = User.dummy("member-" + id);
        ReflectionTestUtils.setField(user, "id", id);
        if (token != null) {
            user.registerPushToken(token, PushProvider.EXPO);
        }
        return user;
    }

    private Membership membershipOf(Long careGroupId, Long userId) {
        return Membership.of(careGroupId, userId, MemberRole.PATIENT, null);
    }

    private DoseLog pendingDoseLog() {
        DoseLog log = DoseLog.of(SCHEDULE_ID, PATIENT_ID, SCHEDULED_AT);
        ReflectionTestUtils.setField(log, "id", DOSE_LOG_ID);
        return log;
    }

    private Schedule scheduleOf(Long careGroupId) {
        return Schedule.of(careGroupId, PATIENT_ID, 1L, TimeOfDay.MORNING,
                LocalDate.now(), LocalDate.now().plusDays(30), PATIENT_ID);
    }
}
