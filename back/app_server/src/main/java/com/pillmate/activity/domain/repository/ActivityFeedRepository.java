package com.pillmate.activity.domain.repository;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.schedule.domain.model.TimeOfDay;

import java.time.Instant;
import java.util.List;

public interface ActivityFeedRepository {
    ActivityFeed save(ActivityFeed feed);
    List<ActivityFeed> findByActorUserIdIn(List<Long> actorUserIds, int limit);
    // 그룹 화면: 멤버별 가입(joined_at) 시점 이후 활동만 — 새 그룹은 과거 활동 미노출
    List<ActivityFeed> findByActorSince(Long actorUserId, Instant since, int limit);
    boolean existsRecent(Long actorUserId, ActivityType type, TimeOfDay timeSlot, Instant since);
}
