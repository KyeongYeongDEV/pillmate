package com.pillmate.activity.infrastructure.persistence;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.schedule.domain.model.TimeOfDay;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

interface ActivityFeedJpaRepository extends JpaRepository<ActivityFeed, Long> {
    List<ActivityFeed> findByActorUserIdInOrderByOccurredAtDesc(List<Long> actorUserIds, Pageable pageable);

    List<ActivityFeed> findByActorUserIdAndOccurredAtGreaterThanEqualOrderByOccurredAtDesc(
            Long actorUserId, Instant occurredAtFrom, Pageable pageable);

    boolean existsByActorUserIdAndActivityTypeAndTimeSlotAndOccurredAtGreaterThanEqual(
            Long actorUserId, ActivityType activityType, TimeOfDay timeSlot, Instant occurredAtFrom);
}
