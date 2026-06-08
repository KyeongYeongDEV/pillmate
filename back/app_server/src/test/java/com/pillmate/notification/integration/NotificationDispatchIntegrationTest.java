package com.pillmate.notification.integration;

import com.pillmate.caregroup.domain.model.CareGroup;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.CareGroupRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.notification.application.NotificationDispatcher;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.domain.model.NotificationReferenceType;
import com.pillmate.notification.domain.model.NotificationType;
import com.pillmate.notification.domain.repository.NotificationRepository;
import com.pillmate.prescription.domain.event.DdiCriticalDetected;
import com.pillmate.prescription.domain.event.PrescriptionRegistered;
import com.pillmate.report.domain.event.WeeklyReportGenerated;
import com.pillmate.user.domain.model.PushProvider;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.pillmate.notification.application.port.NotificationSenderPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration",
        "cloud.aws.credentials.access-key=test",
        "cloud.aws.credentials.secret-key=test"
})
@Testcontainers
@Transactional
@DisplayName("NotificationDispatcher 통합 — DDI/처방전/리포트 발송 시나리오")
class NotificationDispatchIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired NotificationDispatcher notificationDispatcher;
    @Autowired NotificationRepository notificationRepository;
    @Autowired UserRepository userRepository;
    @Autowired CareGroupRepository careGroupRepository;
    @Autowired MembershipRepository membershipRepository;
    @MockBean NotificationSenderPort notificationSenderPort;

    private Long actorUserId;
    private Long memberUserId;
    private Long careGroupId;

    @BeforeEach
    void setUp() {
        User actor = userRepository.save(User.dummy("actor"));
        actor.registerPushToken("ExponentPushToken[actor]", PushProvider.EXPO);
        actorUserId = actor.getId();

        User member = userRepository.save(User.dummy("member"));
        member.registerPushToken("ExponentPushToken[member]", PushProvider.EXPO);
        memberUserId = member.getId();

        CareGroup group = careGroupRepository.save(CareGroup.create("테스트 그룹", actorUserId));
        careGroupId = group.getId();

        membershipRepository.save(Membership.of(careGroupId, actorUserId, MemberRole.PATIENT, null));
        membershipRepository.save(Membership.of(careGroupId, memberUserId, MemberRole.GUARDIAN, actorUserId));
    }

    @Test
    @DisplayName("DdiCriticalDetected — 본인에게 DDI_CRITICAL 알림 저장")
    void on_ddiCriticalDetected_savesNotificationForSelf() {
        // given
        Long prescriptionId = 500L;
        DdiCriticalDetected event = new DdiCriticalDetected(actorUserId, prescriptionId, List.of("횡문근융해증 위험"));

        // when
        notificationDispatcher.on(event);

        // then
        List<Notification> saved = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.DDI_CRITICAL)
                .toList();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getRecipientUserId()).isEqualTo(actorUserId);
        assertThat(saved.get(0).getBody()).contains("약사 또는 의사와 상담");
        assertThat(saved.get(0).getReferenceId()).isEqualTo(prescriptionId);
        assertThat(saved.get(0).getReferenceType()).isEqualTo(NotificationReferenceType.PRESCRIPTION);
    }

    @Test
    @DisplayName("PrescriptionRegistered — 그룹 멤버(본인 제외)에게 PRESCRIPTION_NEW 알림 저장")
    void on_prescriptionRegistered_savesNotificationForGroupMembers() {
        // given
        Long prescriptionId = 600L;
        PrescriptionRegistered event = new PrescriptionRegistered(actorUserId, prescriptionId);

        // when
        notificationDispatcher.on(event);

        // then
        List<Notification> saved = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.PRESCRIPTION_NEW)
                .toList();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getRecipientUserId()).isEqualTo(memberUserId);
        assertThat(saved.get(0).getActorUserId()).isEqualTo(actorUserId);
        assertThat(saved.get(0).getReferenceId()).isEqualTo(prescriptionId);
        assertThat(saved.get(0).getReferenceType()).isEqualTo(NotificationReferenceType.PRESCRIPTION);
    }

    @Test
    @DisplayName("WeeklyReportGenerated — 그룹 멤버(본인 제외)에게 WEEKLY_REPORT 알림 저장")
    void on_weeklyReportGenerated_savesNotificationForGroupMembers() {
        // given
        Long reportId = 700L;
        WeeklyReportGenerated event = new WeeklyReportGenerated(actorUserId, reportId, LocalDate.of(2026, 6, 1));

        // when
        notificationDispatcher.on(event);

        // then
        List<Notification> saved = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.WEEKLY_REPORT)
                .toList();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getRecipientUserId()).isEqualTo(memberUserId);
        assertThat(saved.get(0).getReferenceId()).isEqualTo(reportId);
        assertThat(saved.get(0).getReferenceType()).isEqualTo(NotificationReferenceType.REPORT);
    }

    @Test
    @DisplayName("PrescriptionRegistered — actor가 2그룹 소속: 각 그룹 멤버에게 별도 알림 (groupId 정확)")
    void on_prescriptionRegistered_multiGroup_separatesNotificationPerGroup() {
        // given — actor가 속한 2번째 그룹과 다른 멤버 추가
        User memberB = userRepository.save(User.dummy("memberB"));
        memberB.registerPushToken("ExponentPushToken[memberB]", PushProvider.EXPO);
        Long memberBId = memberB.getId();

        CareGroup groupB = careGroupRepository.save(CareGroup.create("그룹B", actorUserId));
        Long groupBId = groupB.getId();
        membershipRepository.save(Membership.of(groupBId, actorUserId, MemberRole.PATIENT, null));
        membershipRepository.save(Membership.of(groupBId, memberBId, MemberRole.GUARDIAN, actorUserId));

        Long prescriptionId = 800L;
        PrescriptionRegistered event = new PrescriptionRegistered(actorUserId, prescriptionId);

        // when
        notificationDispatcher.on(event);

        // then — 그룹A 멤버 1건 + 그룹B 멤버 1건 = 2건
        List<Notification> saved = notificationRepository.findAll().stream()
                .filter(n -> n.getType() == NotificationType.PRESCRIPTION_NEW)
                .toList();
        assertThat(saved).hasSize(2);
        assertThat(saved.stream().map(Notification::getCareGroupId).toList())
                .containsExactlyInAnyOrder(careGroupId, groupBId);
        assertThat(saved.stream().map(Notification::getRecipientUserId).toList())
                .containsExactlyInAnyOrder(memberUserId, memberBId);
        assertThat(saved).allMatch(n -> n.getReferenceId().equals(prescriptionId));
    }
}
