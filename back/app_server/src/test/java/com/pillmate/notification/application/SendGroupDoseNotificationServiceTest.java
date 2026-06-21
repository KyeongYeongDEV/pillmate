package com.pillmate.notification.application;

import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.schedule.domain.model.Schedule;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("SendGroupDoseNotificationService — 단위 테스트")
@ExtendWith(MockitoExtension.class)
class SendGroupDoseNotificationServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-12T10:00:00Z");

    @Mock DoseLogRepository doseLogRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock NotificationPersistenceService notificationPersistenceService;
    @Mock UserRepository userRepository;
    @Mock NotificationSenderPort notificationSenderPort;
    @Mock com.pillmate.notification.application.port.PrescriptionSummaryPort prescriptionSummaryPort;
    @Spy  Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    @InjectMocks SendGroupDoseNotificationService sut;

    private static final Long ACTOR_ID    = 1L;
    private static final Long MEMBER_ID   = 2L;
    private static final Long DOSE_LOG_ID = 5L;
    private static final Long SCHEDULE_ID = 10L;
    private static final Long GROUP_ID    = 20L;

    @Test
    @DisplayName("TAKEN DoseLog — Recipient push token 포함 NotificationCommand + deep-link route 발송")
    void notify_buildsCommandWithTokenAndRoute() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User member = User.dummy("member");
        member.registerPushToken("ExponentPushToken[abc]", PushProvider.EXPO);

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findGroupMemberUserIds(ACTOR_ID)).willReturn(List.of(MEMBER_ID));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationSenderPort).send(captor.capture());
        NotificationCommand cmd = captor.getValue();
        assertThat(cmd.recipientUserId()).isEqualTo(MEMBER_ID);
        assertThat(cmd.recipientPushToken()).isEqualTo("ExponentPushToken[abc]");
        assertThat(cmd.data()).containsEntry("route", "/group/" + GROUP_ID);
    }

    @Test
    @DisplayName("Recipient 토큰 없으면 token null 로 send (포트가 알아서 skip)")
    void notify_whenRecipientHasNoToken_sendWithNullToken() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User memberNoToken = User.dummy("no-token");

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findGroupMemberUserIds(ACTOR_ID)).willReturn(List.of(MEMBER_ID));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(MEMBER_ID)).willReturn(Optional.of(memberNoToken));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationSenderPort).send(captor.capture());
        assertThat(captor.getValue().recipientPushToken()).isNull();
    }

    @Test
    @DisplayName("DoseLog 없으면 INVALID_NOTIFICATION_DOSE_LOG")
    void notify_whenDoseLogNotFound_throws() {
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.send(DOSE_LOG_ID, ACTOR_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_NOTIFICATION_DOSE_LOG);

        verify(notificationPersistenceService, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("그룹 멤버 없으면 발송 건너뜀")
    void notify_whenNoGroupMembers_skips() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findGroupMemberUserIds(ACTOR_ID)).willReturn(List.of());

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        verify(notificationSenderPort, never()).send(any());
    }

    @Test
    @DisplayName("이미 group_notified_at 기록된 DoseLog — 중복 발송 가드 (저장/발송 모두 skip)")
    void notify_whenAlreadyGroupNotified_skips() {
        DoseLog doseLog = takenDoseLog();
        doseLog.markGroupNotified(FIXED_NOW.minusSeconds(30));
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        verify(notificationPersistenceService, never()).saveAll(anyList());
        verify(notificationSenderPort, never()).send(any());
        verify(doseLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("발송 시 group_notified_at 기록 + save — 폴러 재선택 방지 (멱등)")
    void notify_marksGroupNotifiedAndSaves() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User member = User.dummy("member");
        member.registerPushToken("ExponentPushToken[abc]", PushProvider.EXPO);

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findGroupMemberUserIds(ACTOR_ID)).willReturn(List.of(MEMBER_ID));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        ArgumentCaptor<DoseLog> captor = ArgumentCaptor.forClass(DoseLog.class);
        verify(doseLogRepository).save(captor.capture());
        assertThat(captor.getValue().isGroupNotified()).isTrue();
        assertThat(captor.getValue().getGroupNotifiedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("그룹 멤버 없어도 group_notified_at 기록 — 폴러 무한 재선택 방지")
    void notify_whenNoGroupMembers_stillMarksGroupNotified() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findGroupMemberUserIds(ACTOR_ID)).willReturn(List.of());

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        verify(doseLogRepository).save(any(DoseLog.class));
        verify(notificationSenderPort, never()).send(any());
    }

    @Test
    @DisplayName("actor 가 null (legacy checked_by 미기록) 이어도 환자 본인은 수신자에서 제외 (P1-C)")
    void notify_whenActorNull_stillExcludesPatientSelf() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User member = User.dummy("member");
        member.registerPushToken("ExponentPushToken[abc]", PushProvider.EXPO);

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findGroupMemberUserIds(null)).willReturn(List.of(ACTOR_ID, MEMBER_ID));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        sut.send(DOSE_LOG_ID, null);

        ArgumentCaptor<List<com.pillmate.notification.domain.model.Notification>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getRecipientUserId()).isEqualTo(MEMBER_ID);
    }

    @Test
    @DisplayName("처방전 단위 스케줄 — 처방전 이름 body + deep-link route /prescription/{id}")
    void notify_prescriptionSchedule_usesPrescriptionNameAndRoute() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = prescriptionScheduleOf(GROUP_ID, 77L);
        User member = User.dummy("member");
        member.registerPushToken("ExponentPushToken[abc]", PushProvider.EXPO);

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findGroupMemberUserIds(ACTOR_ID)).willReturn(List.of(MEMBER_ID));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(prescriptionSummaryPort.findById(77L)).willReturn(Optional.of(
                new com.pillmate.notification.application.port.PrescriptionSummaryPort.PrescriptionSummary(
                        LocalDate.of(2026, 6, 21), "타이레놀", 3)));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationSenderPort).send(captor.capture());
        NotificationCommand cmd = captor.getValue();
        assertThat(cmd.data()).containsEntry("route", "/prescription/77");
        assertThat(cmd.body()).contains("6월21일·타이레놀 외2종");
    }

    private DoseLog takenDoseLog() {
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, ACTOR_ID, Instant.now());
        doseLog.take(ACTOR_ID);
        return doseLog;
    }

    private Schedule scheduleOf(Long careGroupId) {
        return Schedule.of(careGroupId, ACTOR_ID, 1L,
                com.pillmate.schedule.domain.model.TimeOfDay.MORNING,
                LocalDate.now(), LocalDate.now().plusDays(30), ACTOR_ID);
    }

    private Schedule prescriptionScheduleOf(Long careGroupId, Long prescriptionId) {
        return Schedule.forPrescription(careGroupId, ACTOR_ID, prescriptionId,
                com.pillmate.schedule.domain.model.TimeOfDay.MORNING, null,
                LocalDate.now(), LocalDate.now().plusDays(30), ACTOR_ID);
    }
}
