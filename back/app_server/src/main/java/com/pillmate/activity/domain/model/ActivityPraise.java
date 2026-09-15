package com.pillmate.activity.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.Instant;

// 그룹원이 "복용 완료" 활동에 보내는 칭찬 1건. (activity_feed_id, praiser_user_id) DB 유니크로
// 같은 사람이 같은 활동을 두 번 눌러도 중복 적재/중복 알림이 나가지 않는다.
@Entity
@Table(name = "activity_praises")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityPraise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_feed_id", nullable = false)
    private Long activityFeedId;

    @Column(name = "praiser_user_id", nullable = false)
    private Long praiserUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static ActivityPraise create(Long activityFeedId, Long praiserUserId, Clock clock) {
        ActivityPraise praise = new ActivityPraise();
        praise.activityFeedId = activityFeedId;
        praise.praiserUserId = praiserUserId;
        praise.createdAt = Instant.now(clock);
        return praise;
    }
}
