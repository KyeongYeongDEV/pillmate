package com.pillmate.activity.domain.model;

import com.pillmate.schedule.domain.model.TimeOfDay;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "activity_feeds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActivityFeed {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ActivityType activityType;

    @Enumerated(EnumType.STRING)
    @Column(name = "time_slot", length = 20)
    private TimeOfDay timeSlot;

    @Column(nullable = false, length = 120)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ActivitySeverity severity;

    @Column(nullable = false)
    private Instant occurredAt;

    public static ActivityFeed create(Long actorUserId, ActivityType activityType,
                                      TimeOfDay timeSlot, String summary, ActivitySeverity severity) {
        ActivityFeed feed = new ActivityFeed();
        feed.actorUserId = actorUserId;
        feed.activityType = activityType;
        feed.timeSlot = timeSlot;
        feed.summary = summary;
        feed.severity = severity != null ? severity : ActivitySeverity.INFO;
        feed.occurredAt = Instant.now();
        return feed;
    }
}
