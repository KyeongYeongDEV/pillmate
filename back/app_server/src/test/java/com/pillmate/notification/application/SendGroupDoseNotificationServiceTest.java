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
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
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

    @Mock DoseLogRepository doseLogRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock NotificationPersistenceService notificationPersistenceService;
    @Mock UserRepository userRepository;
    @Mock NotificationSenderPort notificationSenderPort;
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
}
