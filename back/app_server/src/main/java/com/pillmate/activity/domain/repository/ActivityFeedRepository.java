package com.pillmate.activity.domain.repository;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.schedule.domain.model.TimeOfDay;

import java.time.Instant;
import java.util.List;

public interface ActivityFeedRepository {
    ActivityFeed save(ActivityFeed feed);
    List<ActivityFeed> findByActorUserIdIn(List<Long> actorUserIds, int limit);
    boolean existsRecent(Long actorUserId, ActivityType type, TimeOfDay timeSlot, Instant since);
}
