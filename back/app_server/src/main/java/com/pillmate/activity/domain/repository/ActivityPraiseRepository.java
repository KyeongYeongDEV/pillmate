package com.pillmate.activity.domain.repository;

import com.pillmate.activity.domain.model.ActivityPraise;

import java.util.List;
import java.util.Set;

public interface ActivityPraiseRepository {
    ActivityPraise save(ActivityPraise praise);
    boolean existsByActivityFeedIdAndPraiserUserId(Long activityFeedId, Long praiserUserId);
    // 화면(홈/그룹상세/전체보기) 어디서든 "칭찬함" 상태가 서버 진실源으로 일관되게 보이도록 —
    // 목록 조회 시 이 viewer 가 이미 칭찬한 activityFeedId 들을 배치로 조회.
    Set<Long> findPraisedActivityFeedIds(Long praiserUserId, List<Long> activityFeedIds);
}
