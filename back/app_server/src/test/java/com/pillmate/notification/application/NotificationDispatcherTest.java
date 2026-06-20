package com.pillmate.notification.application;

import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.MembershipPair;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.doselog.domain.event.DoseCheckCanceled;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.model.NotificationReferenceType;
import com.pillmate.notification.domain.model.NotificationType;
import com.pillmate.prescription.domain.event.DdiCriticalDetected;
import com.pillmate.prescription.domain.event.PrescriptionRegistered;
import com.pillmate.report.domain.event.WeeklyReportGenerated;
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
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;

@DisplayName("NotificationDispatcher — @TransactionalEventListener(AFTER_COMMIT) 3 listener")
@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock MembershipRepository membershipRepository;
    @Mock UserRepository userRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock NotificationPersistenceService notificationPersistenceService;
    @Mock NotificationSenderPort notificationSenderPort;
    @InjectMocks NotificationDispatcher sut;

    private static final Long ACTOR_ID = 10L;
    private static final Long MEMBER_ID = 20L;
    private static final Long GROUP_ID = 5L;
    private static final Long PRESCRIPTION_ID = 100L;
    private static final Long REPORT_ID = 200L;

    @BeforeEach
    void setUp() {
        Membership membership = Membership.of(GROUP_ID, ACTOR_ID, MemberRole.PATIENT, null);
        lenient().when(membershipRepository.findByUserId(ACTOR_ID)).thenReturn(List.of(membership));

        User actor = User.dummy("actor");
        actor.registerPushToken("ExponentPushToken[actor]", PushProvider.EXPO);
        lenient().when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.of(actor));
    }

    @Test
    @DisplayName("DdiCriticalDetected — 본인 notification 저장 + send 호출")
    void on_ddiCriticalDetected_savesAndSendsToSelf() {
        // given
        DdiCriticalDetected event = new DdiCriticalDetected(ACTOR_ID, PRESCRIPTION_ID, List.of("횡문근융해증 위험"));
        Notification saved = buildNotification(ACTOR_ID, NotificationType.DDI_CRITICAL);
        given(notificationPersistenceService.saveAll(anyList())).willReturn(List.of(saved));

        // when
        sut.on(event);

        // then
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        List<Notification> notifications = captor.getValue();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.DDI_CRITICAL);
        assertThat(notifications.get(0).getRecipientUserId()).isEqualTo(ACTOR_ID);
        assertThat(notifications.get(0).getReferenceId()).isEqualTo(PRESCRIPTION_ID);
        assertThat(notifications.get(0).getReferenceType()).isEqualTo(NotificationReferenceType.PRESCRIPTION);
        verify(notificationSenderPort).send(any());
    }

    @Test
    @DisplayName("PrescriptionRegistered — 그룹 멤버(본인 제외) notification 저장 + send 호출")
    void on_prescriptionRegistered_savesAndSendsToGroupMembers() {
        // given
        PrescriptionRegistered event = new PrescriptionRegistered(ACTOR_ID, PRESCRIPTION_ID);
        given(membershipRepository.findGroupMemberPairs(ACTOR_ID))
                .willReturn(List.of(new MembershipPair(GROUP_ID, MEMBER_ID)));
        Notification saved = buildNotification(MEMBER_ID, NotificationType.PRESCRIPTION_NEW);
        given(notificationPersistenceService.saveAll(anyList())).willReturn(List.of(saved));
        User member = User.dummy("member");
        member.registerPushToken("ExponentPushToken[member]", PushProvider.EXPO);
        given(userRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        // when
        sut.on(event);

        // then
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        List<Notification> filtered = captor.getValue();
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getRecipientUserId()).isEqualTo(MEMBER_ID);
        assertThat(filtered.get(0).getType()).isEqualTo(NotificationType.PRESCRIPTION_NEW);
        assertThat(filtered.get(0).getCareGroupId()).isEqualTo(GROUP_ID);
        assertThat(filtered.get(0).getReferenceId()).isEqualTo(PRESCRIPTION_ID);
        assertThat(filtered.get(0).getReferenceType()).isEqualTo(NotificationReferenceType.PRESCRIPTION);
        verify(notificationSenderPort).send(any());
    }

    @Test
    @DisplayName("PrescriptionRegistered — 그룹 멤버 없으면 notification 저장 X")
    void on_prescriptionRegistered_whenNoPairs_skips() {
        // given
        PrescriptionRegistered event = new PrescriptionRegistered(ACTOR_ID, PRESCRIPTION_ID);
        given(membershipRepository.findGroupMemberPairs(ACTOR_ID)).willReturn(List.of());

        // when
        sut.on(event);

        // then
        verify(notificationPersistenceService, org.mockito.Mockito.never()).saveAll(anyList());
    }

    @Test
    @DisplayName("PrescriptionRegistered — 다중 그룹: 그룹별 분리 발행 (2 그룹 → 2 notification)")
    void on_prescriptionRegistered_multiGroup_createsPerGroupNotifications() {
        // given
        Long GROUP_B = 6L;
        Long MEMBER_B = 30L;
        PrescriptionRegistered event = new PrescriptionRegistered(ACTOR_ID, PRESCRIPTION_ID);
        given(membershipRepository.findGroupMemberPairs(ACTOR_ID)).willReturn(List.of(
                new MembershipPair(GROUP_ID, MEMBER_ID),
                new MembershipPair(GROUP_B, MEMBER_B)
        ));
        Notification savedA = buildNotification(MEMBER_ID, NotificationType.PRESCRIPTION_NEW);
        Notification savedB = Notification.prescriptionNew(MEMBER_B, ACTOR_ID, GROUP_B, PRESCRIPTION_ID);
        given(notificationPersistenceService.saveAll(anyList())).willReturn(List.of(savedA, savedB));
        User memberA = User.dummy("memberA");
        memberA.registerPushToken("ExponentPushToken[memberA]", PushProvider.EXPO);
        given(userRepository.findById(MEMBER_ID)).willReturn(Optional.of(memberA));
        User memberB = User.dummy("memberB");
        memberB.registerPushToken("ExponentPushToken[memberB]", PushProvider.EXPO);
        given(userRepository.findById(MEMBER_B)).willReturn(Optional.of(memberB));

        // when
        sut.on(event);

        // then
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        List<Notification> created = captor.getValue();
        assertThat(created).hasSize(2);
        assertThat(created.stream().map(Notification::getCareGroupId).toList())
                .containsExactlyInAnyOrder(GROUP_ID, GROUP_B);
        assertThat(created.stream().map(Notification::getRecipientUserId).toList())
                .containsExactlyInAnyOrder(MEMBER_ID, MEMBER_B);
        verify(notificationSenderPort, org.mockito.Mockito.times(2)).send(any());
    }

    @Test
    @DisplayName("WeeklyReportGenerated — 그룹 멤버(본인 제외) notification 저장 + send 호출")
    void on_weeklyReportGenerated_savesAndSendsToGroupMembers() {
        // given
        WeeklyReportGenerated event = new WeeklyReportGenerated(ACTOR_ID, REPORT_ID, LocalDate.of(2026, 6, 1));
        given(membershipRepository.findGroupMemberPairs(ACTOR_ID))
                .willReturn(List.of(new MembershipPair(GROUP_ID, MEMBER_ID)));
        Notification saved = buildNotification(MEMBER_ID, NotificationType.WEEKLY_REPORT);
        given(notificationPersistenceService.saveAll(anyList())).willReturn(List.of(saved));
        User member = User.dummy("member");
        member.registerPushToken("ExponentPushToken[member]", PushProvider.EXPO);
        given(userRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        // when
        sut.on(event);

        // then
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        List<Notification> filtered = captor.getValue();
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getRecipientUserId()).isEqualTo(MEMBER_ID);
        assertThat(filtered.get(0).getType()).isEqualTo(NotificationType.WEEKLY_REPORT);
        assertThat(filtered.get(0).getCareGroupId()).isEqualTo(GROUP_ID);
        assertThat(filtered.get(0).getReferenceId()).isEqualTo(REPORT_ID);
        assertThat(filtered.get(0).getReferenceType()).isEqualTo(NotificationReferenceType.REPORT);
        verify(notificationSenderPort).send(any());
    }

    @Test
    @DisplayName("DoseCheckCanceled — 'OO님이 08:00 약 복용을 취소했습니다' DOSE_CANCELED 그룹 발송 (시각 라벨)")
    void on_doseCheckCanceled_sendsCanceledNotificationToGroup() {
        // given
        Long DOSE_LOG_ID = 7L;
        Long SCHEDULE_ID = 11L;
        DoseCheckCanceled event = new DoseCheckCanceled(DOSE_LOG_ID, ACTOR_ID, SCHEDULE_ID);
        Schedule schedule = Schedule.of(GROUP_ID, ACTOR_ID, 1L, TimeOfDay.MORNING,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31), ACTOR_ID);
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findGroupMemberUserIds(ACTOR_ID)).willReturn(List.of(ACTOR_ID, MEMBER_ID));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        User member = User.dummy("member");
        member.registerPushToken("ExponentPushToken[member]", PushProvider.EXPO);
        given(userRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        // when
        sut.on(event);

        // then
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        List<Notification> created = captor.getValue();
        assertThat(created).hasSize(1);
        Notification n = created.get(0);
        assertThat(n.getType()).isEqualTo(NotificationType.DOSE_CANCELED);
        assertThat(n.getRecipientUserId()).isEqualTo(MEMBER_ID);
        assertThat(n.getCareGroupId()).isEqualTo(GROUP_ID);
        assertThat(n.getDoseLogId()).isEqualTo(DOSE_LOG_ID);
        assertThat(n.getBody()).contains("08:00").contains("취소했습니다");
        verify(notificationSenderPort).send(any());
    }

    @Test
    @DisplayName("DoseCheckCanceled — 그룹 멤버 본인뿐이면 발송 X")
    void on_doseCheckCanceled_whenNoOtherMembers_skips() {
        // given
        DoseCheckCanceled event = new DoseCheckCanceled(7L, ACTOR_ID, 11L);
        Schedule schedule = Schedule.of(GROUP_ID, ACTOR_ID, 1L, TimeOfDay.MORNING,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 12, 31), ACTOR_ID);
        given(scheduleRepository.findById(11L)).willReturn(Optional.of(schedule));
        given(membershipRepository.findGroupMemberUserIds(ACTOR_ID)).willReturn(List.of(ACTOR_ID));

        // when
        sut.on(event);

        // then
        verify(notificationPersistenceService, org.mockito.Mockito.never()).saveAll(anyList());
    }

    private Notification buildNotification(Long recipientId, NotificationType type) {
        if (type == NotificationType.DDI_CRITICAL) {
            return Notification.ddiCritical(recipientId, ACTOR_ID, GROUP_ID, PRESCRIPTION_ID, "위험");
        } else if (type == NotificationType.PRESCRIPTION_NEW) {
            return Notification.prescriptionNew(recipientId, ACTOR_ID, GROUP_ID, PRESCRIPTION_ID);
        } else {
            return Notification.weeklyReport(recipientId, ACTOR_ID, GROUP_ID, REPORT_ID);
        }
    }
}
