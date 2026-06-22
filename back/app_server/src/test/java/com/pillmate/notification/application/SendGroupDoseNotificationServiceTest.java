package com.pillmate.notification.application;

import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.notification.application.port.CareGroupLookupPort;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.schedule.domain.model.Schedule;
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
    @Mock CareGroupLookupPort careGroupLookupPort;
    @Spy  Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    @InjectMocks SendGroupDoseNotificationService sut;

    private static final Long ACTOR_ID    = 1L;
    private static final Long MEMBER_ID   = 2L;
    private static final Long DOSE_LOG_ID = 5L;
    private static final Long SCHEDULE_ID = 10L;
    private static final Long GROUP_ID    = 20L;

    @BeforeEach
    void setUp() {
        // resolveActorName/resolveGroupName 은 모든 buildNotifications 경로에서 호출된다.
        // 각 테스트가 개별 스텁으로 재정의하지 않으면 null(fallback body) 으로 동작.
        lenient().when(userRepository.findById(ACTOR_ID)).thenReturn(Optional.empty());
        lenient().when(careGroupLookupPort.findNameById(any())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("TAKEN DoseLog — Recipient push token 포함 NotificationCommand + deep-link route 발송")
    void notify_buildsCommandWithTokenAndRoute() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User member = User.dummy("member");
        member.registerPushToken("ExponentPushToken[abc]", PushProvider.EXPO);

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
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
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
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
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of());

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
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
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
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of());

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
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
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
    @DisplayName("처방전 단위 스케줄 — route '/group/{id}' + 본문에 그룹명·actor·약봉투명 포함")
    void notify_prescriptionSchedule_usesGroupRouteAndBodyWithNames() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = prescriptionScheduleOf(GROUP_ID, 77L);
        User member = User.dummy("member");
        member.registerPushToken("ExponentPushToken[abc]", PushProvider.EXPO);
        User actor = User.dummy("홍길동");

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(userRepository.findById(ACTOR_ID)).willReturn(Optional.of(actor));
        given(careGroupLookupPort.findNameById(GROUP_ID)).willReturn(Optional.of("가족그룹"));
        given(prescriptionSummaryPort.findById(77L)).willReturn(Optional.of(
                new com.pillmate.notification.application.port.PrescriptionSummaryPort.PrescriptionSummary(
                        LocalDate.of(2026, 6, 21), "타이레놀", 3)));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationSenderPort).send(captor.capture());
        NotificationCommand cmd = captor.getValue();
        assertThat(cmd.data()).containsEntry("route", "/group/" + GROUP_ID);
        assertThat(cmd.body()).contains("6월21일·타이레놀 외2종");
        assertThat(cmd.body()).contains("홍길동");
        assertThat(cmd.body()).contains("가족그룹");
    }

    @Test
    @DisplayName("본문에 그룹명·actor 이름 포함 (비처방전 스케줄)")
    void notify_bodyContainsGroupNameAndActorName() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User member = User.dummy("member");
        member.registerPushToken("ExponentPushToken[abc]", PushProvider.EXPO);
        User actor = User.dummy("김철수");

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(userRepository.findById(ACTOR_ID)).willReturn(Optional.of(actor));
        given(careGroupLookupPort.findNameById(GROUP_ID)).willReturn(Optional.of("우리가족"));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        ArgumentCaptor<List<Notification>> notifCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(notifCaptor.capture());
        String body = notifCaptor.getValue().get(0).getBody();
        assertThat(body).contains("우리가족");
        assertThat(body).contains("김철수");
    }

    @Test
    @DisplayName("actor 이름·그룹명 조회 실패 시 '그룹 멤버가' fallback 본문")
    void notify_whenNamesNotResolved_fallbackBody() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User member = User.dummy("member");
        member.registerPushToken("ExponentPushToken[abc]", PushProvider.EXPO);

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));
        given(userRepository.findById(ACTOR_ID)).willReturn(Optional.empty());
        given(careGroupLookupPort.findNameById(GROUP_ID)).willReturn(Optional.empty());

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        ArgumentCaptor<List<Notification>> notifCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(notifCaptor.capture());
        String body = notifCaptor.getValue().get(0).getBody();
        assertThat(body).contains("그룹 멤버가");
    }

    // ─── P1: cross-group 누출 방지 ─────────────────────────────────────────────

    @Test
    @DisplayName("P1 — actor 가 두 그룹 소속이어도 dose 의 careGroupId 그룹 멤버에게만 발송")
    void notify_onlySendsToScheduleGroup_notAllActorGroups() {
        Long GROUP_B = 30L;
        Long MEMBER_B_ONLY = 3L;  // groupB 전용 멤버 — 누출 대상

        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);  // groupA(20) 스케줄

        User memberA = User.dummy("memberA");
        memberA.registerPushToken("ExponentPushToken[A]", PushProvider.EXPO);

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        // groupA 멤버: actor + memberA
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID),
                membershipOf(GROUP_ID, MEMBER_ID)
        ));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(MEMBER_ID)).willReturn(Optional.of(memberA));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        List<Long> recipients = captor.getValue().stream()
                .map(Notification::getRecipientUserId).toList();
        assertThat(recipients).containsExactly(MEMBER_ID);
        assertThat(recipients).doesNotContain(MEMBER_B_ONLY);  // groupB 전용 멤버 누출 X
    }

    @Test
    @DisplayName("P1 — actor·patient 제외 필터 유지 (schedule.getCareGroupId() 기준)")
    void notify_excludesActorAndPatientFromScheduleGroup() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User member = User.dummy("member");
        member.registerPushToken("ExponentPushToken[abc]", PushProvider.EXPO);

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID),   // 제외되어야 함
                membershipOf(GROUP_ID, MEMBER_ID)   // 수신
        ));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findById(MEMBER_ID)).willReturn(Optional.of(member));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getRecipientUserId()).isEqualTo(MEMBER_ID);
    }

    // ─── P2: careGroupId null route 가드 ───────────────────────────────────────

    @Test
    @DisplayName("P2 — careGroupId null 스케줄이면 발송 없이 안전 종료 (route '/group/null' 발생 불가)")
    void notify_whenCareGroupIdNull_noNotificationSent() {
        // careGroupId=null → findGroupMembersByGroup(null) → empty → 발송 경로 진입 불가
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(null);

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        verify(notificationSenderPort, never()).send(any());
        verify(notificationPersistenceService, never()).saveAll(anyList());
    }

    private Membership membershipOf(Long careGroupId, Long userId) {
        return Membership.of(careGroupId, userId, MemberRole.PATIENT, null);
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
