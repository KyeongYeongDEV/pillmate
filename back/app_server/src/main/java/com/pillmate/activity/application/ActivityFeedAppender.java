package com.pillmate.activity.application;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivitySeverity;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.activity.application.port.ActivityFeedCachePort;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.schedule.domain.model.TimeOfDay;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class ActivityFeedAppender {

    // 같은 슬롯 약 N개 일괄 체크 시 doseLog 단위 N회 호출 → 슬롯 단위 1행으로 디바운스 (사용자 중복 표시 해소)
    private static final long DEDUPE_WINDOW_SEC = 10;

    private final ActivityFeedRepository activityFeedRepository;
    private final MembershipRepository membershipRepository;
    private final ActivityFeedCachePort activityFeedCachePort;

    // timeLabel: 'HH:mm' 정확 시각 (caller 가 schedule.getCustomTime() 포맷 전달). timeSlot 은 도메인 컬럼 보존.
    public void appendTaken(Long actorUserId, TimeOfDay timeSlot, String timeLabel, String actorName) {
        if (isDuplicate(actorUserId, ActivityType.DOSE_TAKEN, timeSlot)) return;
        String summary = actorName + "이(가) " + timeLabel + " 약을 복용했어요";
        ActivityFeed feed = ActivityFeed.create(actorUserId, ActivityType.DOSE_TAKEN, timeSlot, summary, ActivitySeverity.INFO);
        activityFeedRepository.save(feed);
        evictActorGroupFeeds(actorUserId);
    }

    public void appendMissed(Long actorUserId, TimeOfDay timeSlot, String timeLabel, String actorName) {
        if (isDuplicate(actorUserId, ActivityType.DOSE_MISSED, timeSlot)) return;
        String summary = actorName + "이(가) " + timeLabel + " 약을 복용하지 않았어요";
        ActivityFeed feed = ActivityFeed.create(actorUserId, ActivityType.DOSE_MISSED, timeSlot, summary, ActivitySeverity.WARN);
        activityFeedRepository.save(feed);
        evictActorGroupFeeds(actorUserId);
    }

    public void appendCanceled(Long actorUserId, TimeOfDay timeSlot, String timeLabel, String actorName) {
        if (isDuplicate(actorUserId, ActivityType.DOSE_CANCELED, timeSlot)) return;
        String summary = actorName + "이(가) " + timeLabel + " 약 복용을 취소했어요";
        ActivityFeed feed = ActivityFeed.create(actorUserId, ActivityType.DOSE_CANCELED, timeSlot, summary, ActivitySeverity.WARN);
        activityFeedRepository.save(feed);
        evictActorGroupFeeds(actorUserId);
    }

    // 새 활동이 생기는 정확한 순간 — actor 소속 그룹 피드 캐시 무효화 (FCM 즉시 갱신 UX 보존)
    private void evictActorGroupFeeds(Long actorUserId) {
        membershipRepository.findByUserId(actorUserId).stream()
                .map(Membership::getCareGroupId)
                .distinct()
                .forEach(activityFeedCachePort::evictGroup);
    }

    private boolean isDuplicate(Long actorUserId, ActivityType type, TimeOfDay timeSlot) {
        Instant since = Instant.now().minus(Duration.ofSeconds(DEDUPE_WINDOW_SEC));
        return activityFeedRepository.existsRecent(actorUserId, type, timeSlot, since);
    }
}
