package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.notification.application.SendDoseNudgeService;
import com.pillmate.notification.application.dto.NudgeResponse;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@DisplayName("SendMemberNudgeService — 그룹 멤버 카드에서 특정 멤버 바로 재촉")
@ExtendWith(MockitoExtension.class)
class SendMemberNudgeServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-12T10:00:00Z");
    private static final Long GROUP_ID = 20L;
    private static final Long OTHER_GROUP_ID = 99L;
    private static final Long CALLER_ID = 2L;
    private static final Long TARGET_ID = 1L;
    private static final Long SCHEDULE_ID_IN_GROUP = 10L;
    private static final Long SCHEDULE_ID_IN_OTHER_GROUP = 11L;
    private static final Long DOSE_LOG_ID = 5L;

    @Mock MembershipRepository membershipRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock DoseLogRepository doseLogRepository;
    @Mock SendDoseNudgeService sendDoseNudgeService;
    private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private SendMemberNudgeService sut;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        sut = new SendMemberNudgeService(
                membershipRepository, scheduleRepository, doseLogRepository, sendDoseNudgeService, clock);
    }

    @Test
    @DisplayName("호출자가 비 ACTIVE 그룹원 — GROUP_ACCESS_DENIED (403)")
    void nudge_whenCallerNotMember_throwsAccessDenied() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, CALLER_ID)).willReturn(false);

        assertThatThrownBy(() -> sut.nudge(GROUP_ID, TARGET_ID, CALLER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);

        then(sendDoseNudgeService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("대상이 그룹 비멤버 — NUDGE_TARGET_INVALID (400)")
    void nudge_whenTargetNotMember_throwsInvalidTarget() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, CALLER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, TARGET_ID)).willReturn(false);

        assertThatThrownBy(() -> sut.nudge(GROUP_ID, TARGET_ID, CALLER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NUDGE_TARGET_INVALID);

        then(sendDoseNudgeService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("대상이 본인(자기 자신) — NUDGE_TARGET_INVALID (400)")
    void nudge_whenTargetIsSelf_throwsInvalidTarget() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, CALLER_ID)).willReturn(true);

        assertThatThrownBy(() -> sut.nudge(GROUP_ID, CALLER_ID, CALLER_ID))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.NUDGE_TARGET_INVALID);

        then(sendDoseNudgeService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("놓친(overdue) PENDING dose 없음 — 스케줄 무관 일반 넛지로 폴백")
    void nudge_whenNoOverduePending_fallsBackToGeneralNudge() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, CALLER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, TARGET_ID)).willReturn(true);
        given(scheduleRepository.findAllByPatientId(TARGET_ID))
                .willReturn(List.of(scheduleOf(SCHEDULE_ID_IN_GROUP, GROUP_ID)));
        given(doseLogRepository.findEarliestOverduePendingByScheduleIds(
                eq(TARGET_ID), anyCollection(), eq(FIXED_NOW)))
                .willReturn(Optional.empty());
        given(sendDoseNudgeService.nudgeGeneral(GROUP_ID, TARGET_ID, CALLER_ID))
                .willReturn(new NudgeResponse(false));

        NudgeResponse response = sut.nudge(GROUP_ID, TARGET_ID, CALLER_ID);

        assertThat(response.alreadyNotified()).isFalse();
        then(sendDoseNudgeService).should().nudgeGeneral(GROUP_ID, TARGET_ID, CALLER_ID);
        then(sendDoseNudgeService).should(never()).nudge(anyLong(), anyLong());
    }

    @Test
    @DisplayName("대상이 이 그룹 소속 스케줄이 전혀 없음(다른 그룹 스케줄만 있음) — 빈 scheduleIds 로 조회 후 일반 넛지로 폴백")
    void nudge_whenTargetHasNoScheduleInThisGroup_fallsBackToGeneralNudge() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, CALLER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, TARGET_ID)).willReturn(true);
        given(scheduleRepository.findAllByPatientId(TARGET_ID))
                .willReturn(List.of(scheduleOf(SCHEDULE_ID_IN_OTHER_GROUP, OTHER_GROUP_ID)));
        given(doseLogRepository.findEarliestOverduePendingByScheduleIds(eq(TARGET_ID), eq(List.of()), eq(FIXED_NOW)))
                .willReturn(Optional.empty());
        given(sendDoseNudgeService.nudgeGeneral(GROUP_ID, TARGET_ID, CALLER_ID))
                .willReturn(new NudgeResponse(false));

        NudgeResponse response = sut.nudge(GROUP_ID, TARGET_ID, CALLER_ID);

        assertThat(response.alreadyNotified()).isFalse();
        then(sendDoseNudgeService).should().nudgeGeneral(GROUP_ID, TARGET_ID, CALLER_ID);
    }

    @Test
    @DisplayName("놓친 PENDING dose 있음 — SendDoseNudgeService.nudge 에 위임하고 그 응답을 그대로 반환")
    void nudge_whenOverduePendingExists_delegatesToSendDoseNudgeService() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, CALLER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, TARGET_ID)).willReturn(true);
        given(scheduleRepository.findAllByPatientId(TARGET_ID))
                .willReturn(List.of(scheduleOf(SCHEDULE_ID_IN_GROUP, GROUP_ID)));
        given(doseLogRepository.findEarliestOverduePendingByScheduleIds(
                eq(TARGET_ID), anyCollection(), eq(FIXED_NOW)))
                .willReturn(Optional.of(doseLogOf(DOSE_LOG_ID, SCHEDULE_ID_IN_GROUP)));
        given(sendDoseNudgeService.nudge(DOSE_LOG_ID, CALLER_ID)).willReturn(new NudgeResponse(false));

        NudgeResponse response = sut.nudge(GROUP_ID, TARGET_ID, CALLER_ID);

        assertThat(response.alreadyNotified()).isFalse();
        then(sendDoseNudgeService).should().nudge(DOSE_LOG_ID, CALLER_ID);
    }

    @Test
    @DisplayName("여러 그룹에 걸친 스케줄 중 이 groupId 것만 대상 (크로스그룹 오염 방지)")
    void nudge_multipleGroupsSchedules_onlyTargetGroupScheduleIdsPassed() {
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, CALLER_ID)).willReturn(true);
        given(membershipRepository.existsByCareGroupIdAndUserId(GROUP_ID, TARGET_ID)).willReturn(true);
        given(scheduleRepository.findAllByPatientId(TARGET_ID)).willReturn(List.of(
                scheduleOf(SCHEDULE_ID_IN_GROUP, GROUP_ID),
                scheduleOf(SCHEDULE_ID_IN_OTHER_GROUP, OTHER_GROUP_ID)));
        given(doseLogRepository.findEarliestOverduePendingByScheduleIds(
                eq(TARGET_ID), anyCollection(), eq(FIXED_NOW)))
                .willReturn(Optional.of(doseLogOf(DOSE_LOG_ID, SCHEDULE_ID_IN_GROUP)));
        given(sendDoseNudgeService.nudge(DOSE_LOG_ID, CALLER_ID)).willReturn(new NudgeResponse(false));

        sut.nudge(GROUP_ID, TARGET_ID, CALLER_ID);

        ArgumentCaptor<List<Long>> scheduleIdsCaptor = ArgumentCaptor.forClass(List.class);
        then(doseLogRepository).should().findEarliestOverduePendingByScheduleIds(
                eq(TARGET_ID), scheduleIdsCaptor.capture(), eq(FIXED_NOW));
        assertThat(scheduleIdsCaptor.getValue())
                .containsExactly(SCHEDULE_ID_IN_GROUP)
                .doesNotContain(SCHEDULE_ID_IN_OTHER_GROUP);
    }

    private Schedule scheduleOf(Long id, Long careGroupId) {
        Schedule schedule = Schedule.of(careGroupId, TARGET_ID, 1L, TimeOfDay.MORNING,
                LocalDate.now(), LocalDate.now().plusDays(30), TARGET_ID);
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }

    private DoseLog doseLogOf(Long id, Long scheduleId) {
        DoseLog log = DoseLog.of(scheduleId, TARGET_ID, FIXED_NOW.minusSeconds(600));
        ReflectionTestUtils.setField(log, "id", id);
        return log;
    }
}
