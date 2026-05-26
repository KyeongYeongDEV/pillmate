package com.pillmate.activity.infrastructure.persistence;

import com.pillmate.activity.domain.model.ActivityFeed;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ActivityFeedJpaRepository extends JpaRepository<ActivityFeed, Long> {
    List<ActivityFeed> findByActorUserIdInOrderByOccurredAtDesc(List<Long> actorUserIds, Pageable pageable);
}
