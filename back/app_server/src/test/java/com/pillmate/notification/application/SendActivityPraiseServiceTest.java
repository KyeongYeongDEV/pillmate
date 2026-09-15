package com.pillmate.notification.application;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivityPraise;
import com.pillmate.activity.domain.model.ActivitySeverity;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import com.pillmate.activity.domain.repository.ActivityPraiseRepository;
import com.pillmate.activity.application.port.ActivityFeedCachePort;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.notification.application.dto.PraiseResponse;
import com.pillmate.notification.application.port.CareGroupLookupPort;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.schedule.domain.model.TimeOfDay;
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
import java.time.Instant;
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

@DisplayName("SendActivityPraiseService — 그룹원 → 복약 완료 활동 칭찬")
@ExtendWith(MockitoExtension.class)
class SendActivityPraiseServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-09-15T10:00:00Z");
    private static final Long GROUP_ID = 20L;
    private static final Long ACTIVITY_FEED_ID = 7L;
    private static final Long RECIPIENT_ID = 1L;
    private static final Long PRAISER_ID = 2L;

    @Mock ActivityFeedRepository activityFeedRepository;
    @Mock ActivityPraiseRepository activityPraiseRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock CareGroupGuard careGroupGuard;
    @Mock CareGroupLookupPort careGroupLookupPort;
    @Mock UserRepository userRepository;
    @Mock NotificationPersistenceService notificationPersistenceService;
    @Mock NotificationSenderPort notificationSenderPort;
    @Mock ActivityFeedCachePort activityFeedCachePort;
    @Spy  Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    @InjectMocks SendActivityPraiseService sut;

    @Test
    @DisplayName("정상 — DOSE_TAKEN 활동에 칭찬 저장 + 알림 발송")
    void praise_doseTakenActivity_savesAndSends() {
        ActivityFeed feed = doseTakenFeedBy(RECIPIENT_ID);
        User praiser = userOf(PRAISER_ID, "김철수");
        User recipient = patientWithToken(RECIPIENT_ID, "ExponentPushToken[patient]");

        given(activityFeedRepository.findById(ACTIVITY_FEED_ID)).willReturn(Optional.of(feed));
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, RECIPIENT_ID)).willReturn(true);
        given(activityPraiseRepository.existsByActivityFeedIdAndPraiserUserId(ACTIVITY_FEED_ID, PRAISER_ID))
                .willReturn(false);
        given(userRepository.findById(PRAISER_ID)).willReturn(Optional.of(praiser));
        given(userRepository.findById(RECIPIENT_ID)).willReturn(Optional.of(recipient));
        given(careGroupLookupPort.findNameById(GROUP_ID)).willReturn(Optional.of("우리가족"));
        given(notificationPersistenceService.saveAll(anyList())).willAnswer(inv -> inv.getArgument(0));
        given(notificationSenderPort.sendAll(anyList())).willReturn(List.of(1L));

        PraiseResponse response = sut.praise(GROUP_ID, ACTIVITY_FEED_ID, PRAISER_ID);

        assertThat(response.alreadyPraised()).isFalse();
        verify(careGroupGuard).requireAccessible(GROUP_ID);
        ArgumentCaptor<ActivityPraise> praiseCaptor = ArgumentCaptor.forClass(ActivityPraise.class);
        verify(activityPraiseRepository).save(praiseCaptor.capture());
        assertThat(praiseCaptor.getValue().getActivityFeedId()).isEqualTo(ACTIVITY_FEED_ID);
        assertThat(praiseCaptor.getValue().getPraiserUserId()).isEqualTo(PRAISER_ID);

        ArgumentCaptor<List<Notification>> notifCaptor = ArgumentCaptor.forClass(List.class);
        verify(notificationPersistenceService).saveAll(notifCaptor.capture());
        Notification saved = notifCaptor.getValue().get(0);
        assertThat(saved.getRecipientUserId()).isEqualTo(RECIPIENT_ID);
        assertThat(saved.getActorUserId()).isEqualTo(PRAISER_ID);
        assertThat(saved.getBody()).isEqualTo("우리가족에서 김철수님이 복약을 칭찬해줬어요!");
        verify(notificationPersistenceService).markSent(1L, FIXED_NOW);
        verify(activityFeedCachePort).evictGroup(GROUP_ID);
    }

    @Test
    @DisplayName("이미 칭찬한 활동 — 재요청 시 저장/발송 없이 alreadyPraised=true")
    void praise_alreadyPraised_skipsSaveAndSend() {
        ActivityFeed feed = doseTakenFeedBy(RECIPIENT_ID);
        given(activityFeedRepository.findById(ACTIVITY_FEED_ID)).willReturn(Optional.of(feed));
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, RECIPIENT_ID)).willReturn(true);
        given(activityPraiseRepository.existsByActivityFeedIdAndPraiserUserId(ACTIVITY_FEED_ID, PRAISER_ID))
                .willReturn(true);

        PraiseResponse response = sut.praise(GROUP_ID, ACTIVITY_FEED_ID, PRAISER_ID);

        assertThat(response.alreadyPraised()).isTrue();
        verify(activityPraiseRepository, never()).save(any());
        verify(notificationPersistenceService, never()).saveAll(anyList());
        verify(notificationSenderPort, never()).sendAll(anyList());
        verify(activityFeedCachePort, never()).evictGroup(any());
    }

    @Test
    @DisplayName("활동이 DOSE_TAKEN 이 아니면 PRAISE_TARGET_INVALID, 저장/발송 없음")
    void praise_notDoseTaken_throwsAndSkips() {
        ActivityFeed feed = ActivityFeed.create(RECIPIENT_ID, ActivityType.DOSE_MISSED,
                TimeOfDay.MORNING, "08:00 약을 놓치셨어요", ActivitySeverity.WARN);
        given(activityFeedRepository.findById(ACTIVITY_FEED_ID)).willReturn(Optional.of(feed));

        assertThatThrownBy(() -> sut.praise(GROUP_ID, ACTIVITY_FEED_ID, PRAISER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRAISE_TARGET_INVALID);

        verify(activityPraiseRepository, never()).save(any());
        verify(notificationSenderPort, never()).sendAll(anyList());
    }

    @Test
    @DisplayName("자기 자신의 활동을 칭찬하려 하면 PRAISE_TARGET_INVALID")
    void praise_selfPraise_throws() {
        ActivityFeed feed = doseTakenFeedBy(PRAISER_ID);
        given(activityFeedRepository.findById(ACTIVITY_FEED_ID)).willReturn(Optional.of(feed));

        assertThatThrownBy(() -> sut.praise(GROUP_ID, ACTIVITY_FEED_ID, PRAISER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRAISE_TARGET_INVALID);

        verify(activityPraiseRepository, never()).save(any());
    }

    @Test
    @DisplayName("활동의 주인공이 이 그룹 멤버가 아니면 PRAISE_TARGET_INVALID (크로스그룹 차단)")
    void praise_recipientNotInGroup_throws() {
        ActivityFeed feed = doseTakenFeedBy(RECIPIENT_ID);
        given(activityFeedRepository.findById(ACTIVITY_FEED_ID)).willReturn(Optional.of(feed));
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, RECIPIENT_ID)).willReturn(false);

        assertThatThrownBy(() -> sut.praise(GROUP_ID, ACTIVITY_FEED_ID, PRAISER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PRAISE_TARGET_INVALID);

        verify(activityPraiseRepository, never()).save(any());
    }

    @Test
    @DisplayName("존재하지 않는 활동이면 ACTIVITY_FEED_NOT_FOUND")
    void praise_activityNotFound_throws() {
        given(activityFeedRepository.findById(ACTIVITY_FEED_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> sut.praise(GROUP_ID, ACTIVITY_FEED_ID, PRAISER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ACTIVITY_FEED_NOT_FOUND);
    }

    @Test
    @DisplayName("호출자가 그 그룹 ACTIVE 멤버 아니면 403, 활동 조회도 안 함")
    void praise_callerNotGroupMember_throwsAndSkipsLookup() {
        org.mockito.BDDMockito.willThrow(new PillmateException(ErrorCode.GROUP_ACCESS_DENIED))
                .given(careGroupGuard).requireAccessible(GROUP_ID);

        assertThatThrownBy(() -> sut.praise(GROUP_ID, ACTIVITY_FEED_ID, PRAISER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);

        verify(activityFeedRepository, never()).findById(any());
    }

    private ActivityFeed doseTakenFeedBy(Long actorUserId) {
        ActivityFeed feed = ActivityFeed.create(actorUserId, ActivityType.DOSE_TAKEN,
                TimeOfDay.MORNING, "08:00 약을 복용했어요", ActivitySeverity.INFO);
        ReflectionTestUtils.setField(feed, "id", ACTIVITY_FEED_ID);
        return feed;
    }

    private User userOf(Long id, String name) {
        User user = User.dummy(name);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private User patientWithToken(Long id, String token) {
        User user = User.dummy("환자");
        ReflectionTestUtils.setField(user, "id", id);
        user.registerPushToken(token, PushProvider.EXPO);
        return user;
    }
}
