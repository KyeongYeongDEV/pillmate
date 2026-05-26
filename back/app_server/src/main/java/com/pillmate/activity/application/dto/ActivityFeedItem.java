package com.pillmate.activity.application.dto;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivitySeverity;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.schedule.domain.model.TimeOfDay;

import java.time.Instant;

public record ActivityFeedItem(
        String actorNickname,
        ActivityType activityType,
        TimeOfDay timeSlot,
        String summary,
        ActivitySeverity severity,
        Instant occurredAt
) {
    public static ActivityFeedItem from(ActivityFeed feed, String actorNickname) {
        return new ActivityFeedItem(
                actorNickname,
                feed.getActivityType(),
                feed.getTimeSlot(),
                feed.getSummary(),
                feed.getSeverity(),
                feed.getOccurredAt()
        );
    }
}
