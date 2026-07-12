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
import com.pillmate.notification.application.port.RecipientCachePort;
import com.pillmate.notification.application.port.RecipientCachePort.CachedRecipient;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    @Mock RecipientCachePort recipientCachePort;
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
        User member = memberWithToken(MEMBER_ID, "ExponentPushToken[abc]");

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findAllByIdIn(List.of(ACTOR_ID, MEMBER_ID))).willReturn(List.of(member));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        ArgumentCaptor<List<NotificationCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationSenderPort).sendAll(captor.capture());
        NotificationCommand cmd = captor.getValue().get(0);
        assertThat(cmd.recipientUserId()).isEqualTo(MEMBER_ID);
        assertThat(cmd.recipientPushToken()).isEqualTo("ExponentPushToken[abc]");
        assertThat(cmd.data()).containsEntry("route", "/group/" + GROUP_ID);
    }

    @Test
    @DisplayName("Recipient 토큰 없으면 token null 로 send (포트가 알아서 skip)")
    void notify_whenRecipientHasNoToken_sendWithNullToken() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User memberNoToken = memberWithToken(MEMBER_ID, null);

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findAllByIdIn(List.of(ACTOR_ID, MEMBER_ID))).willReturn(List.of(memberNoToken));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        ArgumentCaptor<List<NotificationCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationSenderPort).sendAll(captor.capture());
        assertThat(captor.getValue().get(0).recipientPushToken()).isNull();
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

        verify(notificationSenderPort, never()).sendAll(anyList());
    }

    @Test
    @DisplayName("이미 group_notified_at 기록된 DoseLog — 중복 발송 가드 (저장/발송 모두 skip)")
    void notify_whenAlreadyGroupNotified_skips() {
        DoseLog doseLog = takenDoseLog();
        doseLog.markGroupNotified(FIXED_NOW.minusSeconds(30));
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        verify(notificationPersistenceService, never()).saveAll(anyList());
        verify(notificationSenderPort, never()).sendAll(anyList());
        verify(doseLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("발송 시 group_notified_at 기록 + save — 폴러 재선택 방지 (멱등)")
    void notify_marksGroupNotifiedAndSaves() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User member = memberWithToken(MEMBER_ID, "ExponentPushToken[abc]");

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findAllByIdIn(List.of(ACTOR_ID, MEMBER_ID))).willReturn(List.of(member));

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
        verify(notificationSenderPort, never()).sendAll(anyList());
    }

    @Test
    @DisplayName("actor 가 null (legacy checked_by 미기록) 이어도 환자 본인은 수신자에서 제외 (P1-C)")
    void notify_whenActorNull_stillExcludesPatientSelf() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User member = memberWithToken(MEMBER_ID, "ExponentPushToken[abc]");

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findAllByIdIn(List.of(ACTOR_ID, MEMBER_ID))).willReturn(List.of(member));

        sut.send(DOSE_LOG_ID, null);

        ArgumentCaptor<List<com.pillmate.notification.domain.model.Notification>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).getRecipientUserId()).isEqualTo(MEMBER_ID);
    }

    @Test
    @DisplayName("처방전 단위 스케줄 — label 있으면 그대로 사용, route '/group/{id}' + 본문에 actor·label 포함, 그룹명 prefix 없음")
    void notify_prescriptionSchedule_usesGroupRouteAndLabelAsIs() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = prescriptionScheduleOf(GROUP_ID, 77L);
        User member = memberWithToken(MEMBER_ID, "ExponentPushToken[abc]");
        User actor = User.dummy("홍길동");

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findAllByIdIn(List.of(ACTOR_ID, MEMBER_ID))).willReturn(List.of(member));
        given(userRepository.findById(ACTOR_ID)).willReturn(Optional.of(actor));
        given(careGroupLookupPort.findNameById(GROUP_ID)).willReturn(Optional.of("가족그룹"));
        given(prescriptionSummaryPort.findById(77L)).willReturn(Optional.of(
                new com.pillmate.notification.application.port.PrescriptionSummaryPort.PrescriptionSummary(
                        LocalDate.of(2026, 6, 21), "저녁약")));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        ArgumentCaptor<List<NotificationCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationSenderPort).sendAll(captor.capture());
        NotificationCommand cmd = captor.getValue().get(0);
        assertThat(cmd.data()).containsEntry("route", "/group/" + GROUP_ID);
        assertThat(cmd.body()).contains("저녁약");
        assertThat(cmd.body()).contains("홍길동");
        assertThat(cmd.body()).doesNotContain("가족그룹");
        assertThat(cmd.body()).doesNotContain("[");
    }

    @Test
    @DisplayName("처방전 단위 스케줄 — label 없으면(blank) 'M월 D일 약봉투' fallback 사용")
    void notify_prescriptionSchedule_whenLabelBlank_usesDateFallback() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = prescriptionScheduleOf(GROUP_ID, 77L);
        User member = memberWithToken(MEMBER_ID, "ExponentPushToken[abc]");
        User actor = User.dummy("홍길동");

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findAllByIdIn(List.of(ACTOR_ID, MEMBER_ID))).willReturn(List.of(member));
        given(userRepository.findById(ACTOR_ID)).willReturn(Optional.of(actor));
        given(careGroupLookupPort.findNameById(GROUP_ID)).willReturn(Optional.of("가족그룹"));
        given(prescriptionSummaryPort.findById(77L)).willReturn(Optional.of(
                new com.pillmate.notification.application.port.PrescriptionSummaryPort.PrescriptionSummary(
                        LocalDate.of(2026, 6, 21), "   ")));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        ArgumentCaptor<List<NotificationCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationSenderPort).sendAll(captor.capture());
        NotificationCommand cmd = captor.getValue().get(0);
        assertThat(cmd.body()).contains("6월 21일 약봉투");
        assertThat(cmd.body()).doesNotContain("가족그룹");
    }

    @Test
    @DisplayName("본문에 actor 이름 포함, 그룹명 prefix 없음 (비처방전 스케줄)")
    void notify_bodyContainsActorNameWithoutGroupNamePrefix() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User member = memberWithToken(MEMBER_ID, "ExponentPushToken[abc]");
        User actor = User.dummy("김철수");

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findAllByIdIn(List.of(ACTOR_ID, MEMBER_ID))).willReturn(List.of(member));
        given(userRepository.findById(ACTOR_ID)).willReturn(Optional.of(actor));
        given(careGroupLookupPort.findNameById(GROUP_ID)).willReturn(Optional.of("우리가족"));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        ArgumentCaptor<List<Notification>> notifCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(notifCaptor.capture());
        String body = notifCaptor.getValue().get(0).getBody();
        assertThat(body).contains("김철수");
        assertThat(body).doesNotContain("우리가족");
        assertThat(body).doesNotContain("[");
    }

    @Test
    @DisplayName("actor 이름·그룹명 조회 실패 시 '그룹 멤버가' fallback 본문")
    void notify_whenNamesNotResolved_fallbackBody() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User member = memberWithToken(MEMBER_ID, "ExponentPushToken[abc]");

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findAllByIdIn(List.of(ACTOR_ID, MEMBER_ID))).willReturn(List.of(member));
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

        User memberA = memberWithToken(MEMBER_ID, "ExponentPushToken[A]");

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        // groupA 멤버: actor + memberA
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID),
                membershipOf(GROUP_ID, MEMBER_ID)
        ));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findAllByIdIn(List.of(ACTOR_ID, MEMBER_ID))).willReturn(List.of(memberA));

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
        User member = memberWithToken(MEMBER_ID, "ExponentPushToken[abc]");

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID),   // 제외되어야 함
                membershipOf(GROUP_ID, MEMBER_ID)   // 수신
        ));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findAllByIdIn(List.of(ACTOR_ID, MEMBER_ID))).willReturn(List.of(member));

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

        verify(notificationSenderPort, never()).sendAll(anyList());
        verify(notificationPersistenceService, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("배치 — recipient 3명이어도 토큰 조회(findAllByIdIn) 1회 + sendAll 1회")
    void notify_threeRecipients_singleTokenQueryAndSingleBatchSend() {
        Long MEMBER_2 = 3L;
        Long MEMBER_3 = 4L;
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID),
                membershipOf(GROUP_ID, MEMBER_ID),
                membershipOf(GROUP_ID, MEMBER_2),
                membershipOf(GROUP_ID, MEMBER_3)));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findAllByIdIn(List.of(ACTOR_ID, MEMBER_ID, MEMBER_2, MEMBER_3))).willReturn(List.of(
                memberWithToken(MEMBER_ID, "ExponentPushToken[a]"),
                memberWithToken(MEMBER_2, "ExponentPushToken[b]"),
                memberWithToken(MEMBER_3, "ExponentPushToken[c]")));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        verify(userRepository, times(1)).findAllByIdIn(anyList());
        ArgumentCaptor<List<NotificationCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationSenderPort, times(1)).sendAll(captor.capture());
        assertThat(captor.getValue()).hasSize(3);
        assertThat(captor.getValue()).extracting(NotificationCommand::recipientPushToken)
                .containsExactly("ExponentPushToken[a]", "ExponentPushToken[b]", "ExponentPushToken[c]");
    }

    @Test
    @DisplayName("배치 — 일부 토큰 누락이어도 전체 command 는 sendAll 로 전달 (누락분 token null, 포트가 skip)")
    void notify_partialTokenMissing_stillSendsAllCommands() {
        Long MEMBER_2 = 3L;
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID),
                membershipOf(GROUP_ID, MEMBER_ID),
                membershipOf(GROUP_ID, MEMBER_2)));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findAllByIdIn(List.of(ACTOR_ID, MEMBER_ID, MEMBER_2))).willReturn(List.of(
                memberWithToken(MEMBER_ID, "ExponentPushToken[a]"),
                memberWithToken(MEMBER_2, null)));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        ArgumentCaptor<List<NotificationCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationSenderPort).sendAll(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(0).recipientPushToken()).isEqualTo("ExponentPushToken[a]");
        assertThat(captor.getValue().get(1).recipientPushToken()).isNull();
    }

    @Test
    @DisplayName("배치 — sendAll 이 반환한 성공 notificationId 만 markSent (배치 결과 기반)")
    void notify_marksSentOnlyForBatchSuccesses() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User member = memberWithToken(MEMBER_ID, "ExponentPushToken[abc]");

        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findAllByIdIn(List.of(ACTOR_ID, MEMBER_ID))).willReturn(List.of(member));
        given(notificationSenderPort.sendAll(anyList())).willReturn(List.of(42L));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        verify(notificationPersistenceService).markSent(org.mockito.ArgumentMatchers.eq(42L), any(Instant.class));
    }

    // T-BE-REDIS-RECIPIENT-CACHE — 수신자+토큰 캐시
    @Test
    @DisplayName("캐시 hit — membership/유저 토큰 DB 미조회, 캐시 수신자로 발송")
    void notify_recipientCacheHit_skipsDb() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(recipientCachePort.get(GROUP_ID)).willReturn(Optional.of(List.of(
                new CachedRecipient(ACTOR_ID, "ExponentPushToken[actor]"),
                new CachedRecipient(MEMBER_ID, "ExponentPushToken[cached]"))));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        org.mockito.Mockito.verify(membershipRepository, never()).findByCareGroupId(anyLong());
        org.mockito.Mockito.verify(userRepository, never()).findAllByIdIn(anyList());
        ArgumentCaptor<List<NotificationCommand>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationSenderPort).sendAll(captor.capture());
        assertThat(captor.getValue()).hasSize(1);
        assertThat(captor.getValue().get(0).recipientPushToken()).isEqualTo("ExponentPushToken[cached]");
    }

    @Test
    @DisplayName("캐시 miss — DB 조회 후 put(groupId, 전체 멤버+토큰) 적재")
    void notify_recipientCacheMiss_loadsAndStores() {
        DoseLog doseLog = takenDoseLog();
        Schedule schedule = scheduleOf(GROUP_ID);
        User member = memberWithToken(MEMBER_ID, "ExponentPushToken[abc]");
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.of(schedule));
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                membershipOf(GROUP_ID, ACTOR_ID), membershipOf(GROUP_ID, MEMBER_ID)));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(userRepository.findAllByIdIn(List.of(ACTOR_ID, MEMBER_ID))).willReturn(List.of(member));

        sut.send(DOSE_LOG_ID, ACTOR_ID);

        ArgumentCaptor<List<CachedRecipient>> captor = ArgumentCaptor.forClass(List.class);
        verify(recipientCachePort).put(org.mockito.ArgumentMatchers.eq(GROUP_ID), captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(1).userId()).isEqualTo(MEMBER_ID);
        assertThat(captor.getValue().get(1).token()).isEqualTo("ExponentPushToken[abc]");
    }

    private User memberWithToken(Long id, String token) {
        User user = User.dummy("member-" + id);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        if (token != null) {
            user.registerPushToken(token, PushProvider.EXPO);
        }
        return user;
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
