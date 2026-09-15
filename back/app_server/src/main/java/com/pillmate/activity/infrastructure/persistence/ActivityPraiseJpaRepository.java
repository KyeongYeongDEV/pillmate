package com.pillmate.activity.infrastructure.persistence;

import com.pillmate.activity.domain.model.ActivityPraise;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface ActivityPraiseJpaRepository extends JpaRepository<ActivityPraise, Long> {
    boolean existsByActivityFeedIdAndPraiserUserId(Long activityFeedId, Long praiserUserId);
    List<ActivityPraise> findByPraiserUserIdAndActivityFeedIdIn(Long praiserUserId, List<Long> activityFeedIds);
}
