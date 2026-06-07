package com.pillmate.notification.application;

import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.model.NotificationType;
import com.pillmate.prescription.domain.event.DdiCriticalDetected;
import com.pillmate.prescription.domain.event.PrescriptionRegistered;
import com.pillmate.report.domain.event.WeeklyReportGenerated;
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
        given(membershipRepository.findByUserId(ACTOR_ID)).willReturn(List.of(membership));

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
        verify(notificationSenderPort).send(any());
    }

    @Test
    @DisplayName("PrescriptionRegistered — 그룹 멤버(본인 제외) notification 저장 + send 호출")
    void on_prescriptionRegistered_savesAndSendsToGroupMembers() {
        // given
        PrescriptionRegistered event = new PrescriptionRegistered(ACTOR_ID, PRESCRIPTION_ID);
        given(membershipRepository.findGroupMemberUserIds(ACTOR_ID))
                .willReturn(List.of(ACTOR_ID, MEMBER_ID));
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
        verify(notificationSenderPort).send(any());
    }

    @Test
    @DisplayName("PrescriptionRegistered — 본인만 그룹에 있으면 notification 저장 X")
    void on_prescriptionRegistered_whenOnlySelfInGroup_skips() {
        // given
        PrescriptionRegistered event = new PrescriptionRegistered(ACTOR_ID, PRESCRIPTION_ID);
        given(membershipRepository.findGroupMemberUserIds(ACTOR_ID)).willReturn(List.of(ACTOR_ID));

        // when
        sut.on(event);

        // then
        verify(notificationPersistenceService, org.mockito.Mockito.never()).saveAll(anyList());
    }

    @Test
    @DisplayName("WeeklyReportGenerated — 그룹 멤버(본인 제외) notification 저장 + send 호출")
    void on_weeklyReportGenerated_savesAndSendsToGroupMembers() {
        // given
        WeeklyReportGenerated event = new WeeklyReportGenerated(ACTOR_ID, REPORT_ID, LocalDate.of(2026, 6, 1));
        given(membershipRepository.findGroupMemberUserIds(ACTOR_ID))
                .willReturn(List.of(ACTOR_ID, MEMBER_ID));
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
        verify(notificationSenderPort).send(any());
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
