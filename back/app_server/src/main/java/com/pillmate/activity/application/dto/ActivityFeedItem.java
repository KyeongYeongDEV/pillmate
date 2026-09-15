package com.pillmate.activity.application.dto;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivitySeverity;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.schedule.domain.model.TimeOfDay;

import java.time.Instant;

public record ActivityFeedItem(
        Long id,
        String actorNickname,
        ActivityType activityType,
        TimeOfDay timeSlot,
        String summary,
        ActivitySeverity severity,
        Instant occurredAt,
        boolean praisedByMe
) {
    public static ActivityFeedItem from(ActivityFeed feed, String actorNickname, boolean praisedByMe) {
        return new ActivityFeedItem(
                feed.getId(),
                actorNickname,
                feed.getActivityType(),
                feed.getTimeSlot(),
                feed.getSummary(),
                feed.getSeverity(),
                feed.getOccurredAt(),
                praisedByMe
        );
    }
}
