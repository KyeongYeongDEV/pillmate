package com.pillmate.activity.application;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivitySeverity;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import com.pillmate.schedule.domain.model.TimeOfDay;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ActivityFeedAppender {

    private final ActivityFeedRepository activityFeedRepository;

    public void appendTaken(Long actorUserId, TimeOfDay timeSlot, String actorName) {
        String summary = actorName + "이(가) " + toKorean(timeSlot) + "약을 복용했어요";
        ActivityFeed feed = ActivityFeed.create(actorUserId, ActivityType.DOSE_TAKEN, timeSlot, summary, ActivitySeverity.INFO);
        activityFeedRepository.save(feed);
    }

    public void appendMissed(Long actorUserId, TimeOfDay timeSlot, String actorName) {
        String summary = actorName + "이(가) " + toKorean(timeSlot) + "약을 복용하지 않았어요";
        ActivityFeed feed = ActivityFeed.create(actorUserId, ActivityType.DOSE_MISSED, timeSlot, summary, ActivitySeverity.WARN);
        activityFeedRepository.save(feed);
    }

    public void appendCanceled(Long actorUserId, TimeOfDay timeSlot, String actorName) {
        String summary = actorName + "이(가) " + toKorean(timeSlot) + "약 복용을 취소했어요";
        ActivityFeed feed = ActivityFeed.create(actorUserId, ActivityType.DOSE_CANCELED, timeSlot, summary, ActivitySeverity.WARN);
        activityFeedRepository.save(feed);
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
