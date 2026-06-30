package com.pillmate.activity.application;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivitySeverity;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
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

    public void appendTaken(Long actorUserId, TimeOfDay timeSlot, String actorName) {
        if (isDuplicate(actorUserId, ActivityType.DOSE_TAKEN, timeSlot)) return;
        String summary = actorName + "이(가) " + toKorean(timeSlot) + "약을 복용했어요";
        ActivityFeed feed = ActivityFeed.create(actorUserId, ActivityType.DOSE_TAKEN, timeSlot, summary, ActivitySeverity.INFO);
        activityFeedRepository.save(feed);
    }

    public void appendMissed(Long actorUserId, TimeOfDay timeSlot, String actorName) {
        if (isDuplicate(actorUserId, ActivityType.DOSE_MISSED, timeSlot)) return;
        String summary = actorName + "이(가) " + toKorean(timeSlot) + "약을 복용하지 않았어요";
        ActivityFeed feed = ActivityFeed.create(actorUserId, ActivityType.DOSE_MISSED, timeSlot, summary, ActivitySeverity.WARN);
        activityFeedRepository.save(feed);
    }

    public void appendCanceled(Long actorUserId, TimeOfDay timeSlot, String actorName) {
        if (isDuplicate(actorUserId, ActivityType.DOSE_CANCELED, timeSlot)) return;
        String summary = actorName + "이(가) " + toKorean(timeSlot) + "약 복용을 취소했어요";
        ActivityFeed feed = ActivityFeed.create(actorUserId, ActivityType.DOSE_CANCELED, timeSlot, summary, ActivitySeverity.WARN);
        activityFeedRepository.save(feed);
    }

    private boolean isDuplicate(Long actorUserId, ActivityType type, TimeOfDay timeSlot) {
        Instant since = Instant.now().minus(Duration.ofSeconds(DEDUPE_WINDOW_SEC));
        return activityFeedRepository.existsRecent(actorUserId, type, timeSlot, since);
    }

    private String toKorean(TimeOfDay timeSlot) {
        if (timeSlot == null) return "";
        return switch (timeSlot) {
            case MORNING -> "아침 ";
            case NOON -> "점심 ";
            case EVENING -> "저녁 ";
            case BEDTIME -> "취침 전 ";
        };
    }
}
