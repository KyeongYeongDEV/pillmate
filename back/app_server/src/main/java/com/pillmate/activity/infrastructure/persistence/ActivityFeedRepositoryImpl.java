package com.pillmate.activity.infrastructure.persistence;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

@Repository
@RequiredArgsConstructor
class ActivityFeedRepositoryImpl implements ActivityFeedRepository {

    private final ActivityFeedJpaRepository jpa;

    @Override
    public ActivityFeed save(ActivityFeed feed) {
        return jpa.save(feed);
    }

    @Override
    public List<ActivityFeed> findByActorUserIdIn(List<Long> actorUserIds, int limit) {
        if (actorUserIds.isEmpty()) {
            return Collections.emptyList();
        }
        return jpa.findByActorUserIdInOrderByOccurredAtDesc(actorUserIds, PageRequest.of(0, limit));
    }
}
