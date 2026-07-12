package com.pillmate.activity.application;

import com.pillmate.activity.domain.model.ActivityFeed;
import com.pillmate.activity.domain.model.ActivitySeverity;
import com.pillmate.activity.domain.model.ActivityType;
import com.pillmate.activity.application.port.ActivityFeedCachePort;
import com.pillmate.activity.domain.repository.ActivityFeedRepository;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.schedule.domain.model.TimeOfDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@DisplayName("ActivityFeedAppender — 활동 피드 등록")
@ExtendWith(MockitoExtension.class)
class ActivityFeedAppenderTest {

    @Mock ActivityFeedRepository activityFeedRepository;
    @Mock MembershipRepository membershipRepository;
    @Mock ActivityFeedCachePort activityFeedCachePort;
    @InjectMocks ActivityFeedAppender sut;

    @Test
    @DisplayName("DOSE_TAKEN — summary에 HH:mm 시각 포함, PII(약 이름)+시간대라벨 미포함, INFO 심각도")
    void appendTaken_savesFeedWithHhMmAndNoMedicalPII() {
        // when
        sut.appendTaken(1L, TimeOfDay.MORNING, "08:00", "할머니");

        // then
        ArgumentCaptor<ActivityFeed> captor = ArgumentCaptor.forClass(ActivityFeed.class);
        then(activityFeedRepository).should(times(1)).save(captor.capture());
        ActivityFeed saved = captor.getValue();
        assertThat(saved.getActorUserId()).isEqualTo(1L);
        assertThat(saved.getActivityType()).isEqualTo(ActivityType.DOSE_TAKEN);
        assertThat(saved.getTimeSlot()).isEqualTo(TimeOfDay.MORNING);
        assertThat(saved.getSummary()).isEqualTo("할머니이(가) 08:00 약을 복용했어요");
        assertThat(saved.getSummary()).doesNotContain("아침").doesNotContain("mg").doesNotContain("처방");
        assertThat(saved.getSeverity()).isEqualTo(ActivitySeverity.INFO);
    }

    @Test
    @DisplayName("DOSE_MISSED — summary에 미복용 문구 포함, WARN 심각도")
    void appendMissed_savesFeedWithWarnSeverity() {
        // when
        sut.appendMissed(2L, TimeOfDay.NOON, "12:30", "어머니");

        // then
        ArgumentCaptor<ActivityFeed> captor = ArgumentCaptor.forClass(ActivityFeed.class);
        then(activityFeedRepository).should(times(1)).save(captor.capture());
        ActivityFeed saved = captor.getValue();
        assertThat(saved.getActivityType()).isEqualTo(ActivityType.DOSE_MISSED);
        assertThat(saved.getSeverity()).isEqualTo(ActivitySeverity.WARN);
        assertThat(saved.getSummary()).isEqualTo("어머니이(가) 12:30 약을 복용하지 않았어요");
        assertThat(saved.getSummary()).doesNotContain("점심");
    }

    @Test
    @DisplayName("DOSE_CANCELED — summary에 취소 문구+시간대+이름 포함, PII 미포함, WARN 심각도")
    void appendCanceled_savesFeedWithCancelSummaryAndWarnSeverity() {
        // when
        sut.appendCanceled(3L, TimeOfDay.EVENING, "19:00", "아버지");

        // then
        ArgumentCaptor<ActivityFeed> captor = ArgumentCaptor.forClass(ActivityFeed.class);
        then(activityFeedRepository).should(times(1)).save(captor.capture());
        ActivityFeed saved = captor.getValue();
        assertThat(saved.getActorUserId()).isEqualTo(3L);
        assertThat(saved.getActivityType()).isEqualTo(ActivityType.DOSE_CANCELED);
        assertThat(saved.getTimeSlot()).isEqualTo(TimeOfDay.EVENING);
        assertThat(saved.getSeverity()).isEqualTo(ActivitySeverity.WARN);
        assertThat(saved.getSummary()).isEqualTo("아버지이(가) 19:00 약 복용을 취소했어요");
        assertThat(saved.getSummary()).contains("취소").doesNotContain("저녁").doesNotContain("mg");
    }

    @Test
    @DisplayName("DOSE_CANCELED — timeSlot null 이어도 안전 저장")
    void appendCanceled_nullTimeSlot_savesSafely() {
        sut.appendCanceled(4L, null, "22:00", "멤버");

        ArgumentCaptor<ActivityFeed> captor = ArgumentCaptor.forClass(ActivityFeed.class);
        then(activityFeedRepository).should(times(1)).save(captor.capture());
        assertThat(captor.getValue().getActivityType()).isEqualTo(ActivityType.DOSE_CANCELED);
    }

    // T-DOSE-ACTIVITY-DEDUPE: 같은 슬롯 약 N개 일괄 체크 시 10초 내 동일 (actor,type,slot) 중복 skip
    @Test
    @DisplayName("DOSE_TAKEN — 10초 내 동일 슬롯 이미 적재됨(existsRecent true) → save skip")
    void appendTaken_whenRecentExists_skipsSave() {
        given(activityFeedRepository.existsRecent(
                eq(1L), eq(ActivityType.DOSE_TAKEN), eq(TimeOfDay.MORNING), any(Instant.class)))
                .willReturn(true);

        sut.appendTaken(1L, TimeOfDay.MORNING, "08:00", "할머니");

        then(activityFeedRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("DOSE_CANCELED — 10초 내 동일 슬롯 중복 → save skip")
    void appendCanceled_whenRecentExists_skipsSave() {
        given(activityFeedRepository.existsRecent(
                eq(2L), eq(ActivityType.DOSE_CANCELED), eq(TimeOfDay.EVENING), any(Instant.class)))
                .willReturn(true);

        sut.appendCanceled(2L, TimeOfDay.EVENING, "19:00", "아버지");

        then(activityFeedRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("DOSE_MISSED — 10초 내 동일 슬롯 중복 → save skip")
    void appendMissed_whenRecentExists_skipsSave() {
        given(activityFeedRepository.existsRecent(
                eq(3L), eq(ActivityType.DOSE_MISSED), eq(TimeOfDay.NOON), any(Instant.class)))
                .willReturn(true);

        sut.appendMissed(3L, TimeOfDay.NOON, "12:30", "어머니");

        then(activityFeedRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("DOSE_TAKEN — existsRecent false(윈도우 밖/첫 적재) → 정상 save")
    void appendTaken_whenNoRecent_saves() {
        given(activityFeedRepository.existsRecent(
                eq(1L), eq(ActivityType.DOSE_TAKEN), eq(TimeOfDay.MORNING), any(Instant.class)))
                .willReturn(false);

        sut.appendTaken(1L, TimeOfDay.MORNING, "08:00", "할머니");

        then(activityFeedRepository).should(times(1)).save(any());
    }

    // T-BE-REDIS-ACTIVITY-FEED-CACHE — append 시 actor 소속 그룹 피드 캐시 무효화
    @Test
    @DisplayName("DOSE_TAKEN 적재 시 actor 소속 모든 그룹 evictGroup 호출 (즉시성 보존)")
    void appendTaken_evictsActorGroupFeeds() {
        given(membershipRepository.findByUserId(1L)).willReturn(java.util.List.of(
                Membership.of(10L, 1L, MemberRole.PATIENT, null),
                Membership.of(20L, 1L, MemberRole.PATIENT, null)));

        sut.appendTaken(1L, TimeOfDay.MORNING, "08:00", "할머니");

        then(activityFeedCachePort).should().evictGroup(10L);
        then(activityFeedCachePort).should().evictGroup(20L);
    }

    @Test
    @DisplayName("dedupe skip 시 (existsRecent true) — 새 활동 없으므로 evict 미호출")
    void appendTaken_whenDuplicate_skipsEvict() {
        given(activityFeedRepository.existsRecent(
                eq(1L), eq(ActivityType.DOSE_TAKEN), eq(TimeOfDay.MORNING), any(Instant.class)))
                .willReturn(true);

        sut.appendTaken(1L, TimeOfDay.MORNING, "08:00", "할머니");

        then(activityFeedCachePort).should(never()).evictGroup(any());
    }

    @Test
    @DisplayName("DOSE_CANCELED 적재 시에도 evictGroup 호출")
    void appendCanceled_evictsActorGroupFeeds() {
        given(membershipRepository.findByUserId(3L)).willReturn(java.util.List.of(
                Membership.of(10L, 3L, MemberRole.PATIENT, null)));

        sut.appendCanceled(3L, TimeOfDay.EVENING, "19:00", "아버지");

        then(activityFeedCachePort).should().evictGroup(10L);
    }
}
