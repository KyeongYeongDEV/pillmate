package com.pillmate.activity.domain;

import com.pillmate.activity.domain.model.ActivityPraise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ActivityPraise 도메인")
class ActivityPraiseTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-15T09:00:00Z"), ZoneOffset.UTC);

    @Test
    @DisplayName("create — activityFeedId/praiserUserId/createdAt(clock 기준) 설정")
    void create_setsFields() {
        ActivityPraise praise = ActivityPraise.create(7L, 2L, FIXED_CLOCK);

        assertThat(praise.getActivityFeedId()).isEqualTo(7L);
        assertThat(praise.getPraiserUserId()).isEqualTo(2L);
        assertThat(praise.getCreatedAt()).isEqualTo(Instant.parse("2026-09-15T09:00:00Z"));
    }
}
