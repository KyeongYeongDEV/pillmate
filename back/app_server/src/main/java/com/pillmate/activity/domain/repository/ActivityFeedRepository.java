package com.pillmate.activity.domain.repository;

import com.pillmate.activity.domain.model.ActivityFeed;

import java.util.List;

public interface ActivityFeedRepository {
    ActivityFeed save(ActivityFeed feed);
    List<ActivityFeed> findByActorUserIdIn(List<Long> actorUserIds, int limit);
}
