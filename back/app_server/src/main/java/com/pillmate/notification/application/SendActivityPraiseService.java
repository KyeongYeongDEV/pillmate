package com.pillmate.notification.application;

import com.pillmate.activity.application.port.ActivityFeedCachePort;
import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivityPraise;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import com.pillmate.activity.domain.repository.ActivityPraiseRepository;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.notification.application.dto.PraiseResponse;
import com.pillmate.notification.application.port.CareGroupLookupPort;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

// 그룹원 → "복용 완료" 활동에 칭찬. (activityFeed, praiser) 유니크로 중복 알림 차단(멱등, like-버튼 시맨틱).
@Service
@RequiredArgsConstructor
public class SendActivityPraiseService {

    private static final String ROUTE_GROUP_PREFIX = "/group/";

    private final ActivityFeedRepository activityFeedRepository;
    private final ActivityPraiseRepository activityPraiseRepository;
    private final MembershipRepository membershipRepository;
    private final CareGroupGuard careGroupGuard;
    private final CareGroupLookupPort careGroupLookupPort;
    private final UserRepository userRepository;
    private final NotificationPersistenceService notificationPersistenceService;
    private final NotificationSenderPort notificationSenderPort;
    private final ActivityFeedCachePort activityFeedCachePort;
    private final Clock clock;

    @Transactional
    public PraiseResponse praise(Long groupId, Long activityFeedId, Long praiserUserId) {
        careGroupGuard.requireAccessible(groupId);
        ActivityFeed feed = findActivityFeed(activityFeedId);
        requireDoseTaken(feed);
        Long recipientUserId = feed.getActorUserId();
        requireValidTarget(groupId, recipientUserId, praiserUserId);

        if (activityPraiseRepository.existsByActivityFeedIdAndPraiserUserId(activityFeedId, praiserUserId)) {
            return new PraiseResponse(true);
        }
        activityPraiseRepository.save(ActivityPraise.create(activityFeedId, praiserUserId, clock));
        activityFeedCachePort.evictGroup(groupId);

        Notification notification = Notification.dosePraise(
                recipientUserId, praiserUserId, groupId, activityFeedId,
                resolveName(praiserUserId), resolveGroupName(groupId));
        Notification saved = notificationPersistenceService.saveAll(List.of(notification)).get(0);
        List<Long> sentIds = notificationSenderPort.sendAll(List.of(toCommand(saved, groupId)));
        markSentAll(sentIds);
        return new PraiseResponse(false);
    }

    private void requireDoseTaken(ActivityFeed feed) {
        if (feed.getActivityType() != ActivityType.DOSE_TAKEN) {
            throw new PillmateException(ErrorCode.PRAISE_TARGET_INVALID);
        }
    }

    // 자기 자신은 칭찬 불가 + 활동의 주인공이 이 그룹 멤버가 아니면 차단(크로스그룹 알림 위조 방지)
    private void requireValidTarget(Long groupId, Long recipientUserId, Long praiserUserId) {
        if (recipientUserId.equals(praiserUserId)
                || !membershipRepository.existsByCareGroupIdAndUserId(groupId, recipientUserId)) {
            throw new PillmateException(ErrorCode.PRAISE_TARGET_INVALID);
        }
    }

    private ActivityFeed findActivityFeed(Long activityFeedId) {
        return activityFeedRepository.findById(activityFeedId)
                .orElseThrow(() -> new PillmateException(ErrorCode.ACTIVITY_FEED_NOT_FOUND));
    }

    private String resolveName(Long userId) {
        return userRepository.findById(userId).map(User::getName).orElse(null);
    }

    private String resolveGroupName(Long groupId) {
        return careGroupLookupPort.findNameById(groupId).orElse(null);
    }

    private NotificationCommand toCommand(Notification n, Long groupId) {
        String token = userRepository.findById(n.getRecipientUserId())
                .map(User::getExpoPushToken)
                .orElse(null);
        Map<String, String> data = Map.of("route", ROUTE_GROUP_PREFIX + groupId, "type", n.getType().name());
        return new NotificationCommand(n.getId(), n.getRecipientUserId(), token, n.getTitle(), n.getBody(), data);
    }

    private void markSentAll(List<Long> sentNotificationIds) {
        Instant now = Instant.now(clock);
        sentNotificationIds.forEach(id -> notificationPersistenceService.markSent(id, now));
    }
}
