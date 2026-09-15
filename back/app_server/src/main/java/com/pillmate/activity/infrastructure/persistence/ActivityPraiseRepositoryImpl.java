package com.pillmate.activity.infrastructure.persistence;

import com.pillmate.activity.domain.model.ActivityPraise;
import com.pillmate.activity.domain.repository.ActivityPraiseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
class ActivityPraiseRepositoryImpl implements ActivityPraiseRepository {

    private final ActivityPraiseJpaRepository jpa;

    @Override
    public ActivityPraise save(ActivityPraise praise) {
        return jpa.save(praise);
    }

    @Override
    public boolean existsByActivityFeedIdAndPraiserUserId(Long activityFeedId, Long praiserUserId) {
        return jpa.existsByActivityFeedIdAndPraiserUserId(activityFeedId, praiserUserId);
    }

    @Override
    public Set<Long> findPraisedActivityFeedIds(Long praiserUserId, List<Long> activityFeedIds) {
        if (activityFeedIds.isEmpty()) {
            return Collections.emptySet();
        }
        return jpa.findByPraiserUserIdAndActivityFeedIdIn(praiserUserId, activityFeedIds).stream()
                .map(ActivityPraise::getActivityFeedId)
                .collect(Collectors.toSet());
    }
}
