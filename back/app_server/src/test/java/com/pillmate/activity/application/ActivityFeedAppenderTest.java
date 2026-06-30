package com.pillmate.activity.application;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivitySeverity;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import com.pillmate.schedule.domain.model.TimeOfDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@DisplayName("ActivityFeedAppender — 활동 피드 등록")
@ExtendWith(MockitoExtension.class)
class ActivityFeedAppenderTest {

    @Mock ActivityFeedRepository activityFeedRepository;
    @InjectMocks ActivityFeedAppender sut;

    @Test
    @DisplayName("DOSE_TAKEN — summary에 시간대 한국어 포함, PII(약 이름) 미포함, INFO 심각도")
    void appendTaken_savesFeedWithKoreanSlotAndNoMedicalPII() {
        // when
        sut.appendTaken(1L, TimeOfDay.MORNING, "할머니");

        // then
        ArgumentCaptor<ActivityFeed> captor = ArgumentCaptor.forClass(ActivityFeed.class);
        then(activityFeedRepository).should(times(1)).save(captor.capture());
        ActivityFeed saved = captor.getValue();
        assertThat(saved.getActorUserId()).isEqualTo(1L);
        assertThat(saved.getActivityType()).isEqualTo(ActivityType.DOSE_TAKEN);
        assertThat(saved.getTimeSlot()).isEqualTo(TimeOfDay.MORNING);
        assertThat(saved.getSummary()).contains("아침").contains("할머니").doesNotContain("mg").doesNotContain("처방");
        assertThat(saved.getSeverity()).isEqualTo(ActivitySeverity.INFO);
    }

    @Test
    @DisplayName("DOSE_MISSED — summary에 미복용 문구 포함, WARN 심각도")
    void appendMissed_savesFeedWithWarnSeverity() {
        // when
        sut.appendMissed(2L, TimeOfDay.NOON, "어머니");

        // then
        ArgumentCaptor<ActivityFeed> captor = ArgumentCaptor.forClass(ActivityFeed.class);
        then(activityFeedRepository).should(times(1)).save(captor.capture());
        ActivityFeed saved = captor.getValue();
        assertThat(saved.getActivityType()).isEqualTo(ActivityType.DOSE_MISSED);
        assertThat(saved.getSeverity()).isEqualTo(ActivitySeverity.WARN);
        assertThat(saved.getSummary()).contains("점심").contains("어머니");
    }

    @Test
    @DisplayName("DOSE_CANCELED — summary에 취소 문구+시간대+이름 포함, PII 미포함, WARN 심각도")
    void appendCanceled_savesFeedWithCancelSummaryAndWarnSeverity() {
        // when
        sut.appendCanceled(3L, TimeOfDay.EVENING, "아버지");

        // then
        ArgumentCaptor<ActivityFeed> captor = ArgumentCaptor.forClass(ActivityFeed.class);
        then(activityFeedRepository).should(times(1)).save(captor.capture());
        ActivityFeed saved = captor.getValue();
        assertThat(saved.getActorUserId()).isEqualTo(3L);
        assertThat(saved.getActivityType()).isEqualTo(ActivityType.DOSE_CANCELED);
        assertThat(saved.getTimeSlot()).isEqualTo(TimeOfDay.EVENING);
        assertThat(saved.getSeverity()).isEqualTo(ActivitySeverity.WARN);
        assertThat(saved.getSummary()).contains("저녁").contains("아버지").contains("취소")
                .doesNotContain("mg").doesNotContain("처방");
    }

    @Test
    @DisplayName("DOSE_CANCELED — timeSlot null 이어도 안전 저장")
    void appendCanceled_nullTimeSlot_savesSafely() {
        sut.appendCanceled(4L, null, "멤버");

        ArgumentCaptor<ActivityFeed> captor = ArgumentCaptor.forClass(ActivityFeed.class);
        then(activityFeedRepository).should(times(1)).save(captor.capture());
        assertThat(captor.getValue().getActivityType()).isEqualTo(ActivityType.DOSE_CANCELED);
    }
}
