package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.GroupMonthScheduleResponse;
import com.pillmate.caregroup.application.dto.GroupMonthScheduleResponse.GroupDayView;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.schedule.application.GetMonthScheduleUseCase;
import com.pillmate.schedule.application.dto.MonthScheduleResponse;
import com.pillmate.schedule.application.dto.MonthScheduleResponse.DayAdherenceView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@DisplayName("GetGroupMonthScheduleService — 그룹 구성원별 월간 스케줄 집계")
@ExtendWith(MockitoExtension.class)
class GetGroupMonthScheduleServiceTest {

    private static final Long GROUP_ID = 20L;
    private static final Long MEMBER_A = 1L;
    private static final Long MEMBER_B = 2L;
    private static final YearMonth MONTH = YearMonth.of(2026, 9);
    private static final LocalDate DAY_1 = LocalDate.of(2026, 9, 1);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 9, 2);

    @Mock CareGroupGuard careGroupGuard;
    @Mock MembershipRepository membershipRepository;
    @Mock GetMonthScheduleUseCase getMonthScheduleUseCase;

    private GetGroupMonthScheduleService sut;

    @BeforeEach
    void setUp() {
        sut = new GetGroupMonthScheduleService(careGroupGuard, membershipRepository, getMonthScheduleUseCase);
    }

    @Test
    @DisplayName("호출자가 해당 그룹 ACTIVE 멤버가 아니면 CareGroupGuard 예외가 그대로 전파된다")
    void execute_whenCallerNotAccessible_propagatesGuardException() {
        willThrow(new PillmateException(ErrorCode.GROUP_ACCESS_DENIED))
                .given(careGroupGuard).requireAccessible(GROUP_ID);

        assertThatThrownBy(() -> sut.execute(GROUP_ID, MONTH))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);

        then(membershipRepository).shouldHaveNoInteractions();
        then(getMonthScheduleUseCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("멤버 2명이 서로 다른 날짜에 데이터가 있으면 날짜별로 올바른 멤버만 묶인다")
    void execute_membersWithDifferentDays_mergesByDateCorrectly() {
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                memberOf(MEMBER_A), memberOf(MEMBER_B)));
        given(getMonthScheduleUseCase.execute(MONTH, MEMBER_A))
                .willReturn(new MonthScheduleResponse(MONTH.toString(),
                        List.of(new DayAdherenceView(DAY_1, 2, 2, "FULL"))));
        given(getMonthScheduleUseCase.execute(MONTH, MEMBER_B))
                .willReturn(new MonthScheduleResponse(MONTH.toString(),
                        List.of(new DayAdherenceView(DAY_2, 1, 0, "MISS"))));

        GroupMonthScheduleResponse response = sut.execute(GROUP_ID, MONTH);

        assertThat(response.days()).hasSize(2);
        GroupDayView day1 = response.days().get(0);
        assertThat(day1.date()).isEqualTo(DAY_1);
        assertThat(day1.members()).extracting("userId").containsExactly(MEMBER_A);
        GroupDayView day2 = response.days().get(1);
        assertThat(day2.date()).isEqualTo(DAY_2);
        assertThat(day2.members()).extracting("userId").containsExactly(MEMBER_B);
    }

    @Test
    @DisplayName("겹치는 날짜엔 두 멤버 모두 묶인다")
    void execute_membersWithOverlappingDay_bothIncluded() {
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                memberOf(MEMBER_A), memberOf(MEMBER_B)));
        given(getMonthScheduleUseCase.execute(MONTH, MEMBER_A))
                .willReturn(new MonthScheduleResponse(MONTH.toString(),
                        List.of(new DayAdherenceView(DAY_1, 2, 2, "FULL"))));
        given(getMonthScheduleUseCase.execute(MONTH, MEMBER_B))
                .willReturn(new MonthScheduleResponse(MONTH.toString(),
                        List.of(new DayAdherenceView(DAY_1, 1, 0, "MISS"))));

        GroupMonthScheduleResponse response = sut.execute(GROUP_ID, MONTH);

        assertThat(response.days()).hasSize(1);
        assertThat(response.days().get(0).members())
                .extracting("userId")
                .containsExactlyInAnyOrder(MEMBER_A, MEMBER_B);
    }

    @Test
    @DisplayName("반환된 days 는 날짜 오름차순으로 정렬된다")
    void execute_returnsDaysSortedAscending() {
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(memberOf(MEMBER_A)));
        given(getMonthScheduleUseCase.execute(MONTH, MEMBER_A))
                .willReturn(new MonthScheduleResponse(MONTH.toString(), List.of(
                        new DayAdherenceView(DAY_2, 1, 1, "FULL"),
                        new DayAdherenceView(DAY_1, 1, 0, "MISS"))));

        GroupMonthScheduleResponse response = sut.execute(GROUP_ID, MONTH);

        assertThat(response.days()).extracting("date").containsExactly(DAY_1, DAY_2);
    }

    @Test
    @DisplayName("그룹 멤버가 0명이면 빈 days 를 반환하고 예외를 던지지 않는다")
    void execute_noMembers_returnsEmptyDays() {
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of());

        GroupMonthScheduleResponse response = sut.execute(GROUP_ID, MONTH);

        assertThat(response.days()).isEmpty();
        then(getMonthScheduleUseCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("각 멤버의 userId 로만 GetMonthScheduleUseCase 가 호출된다 (크로스그룹 오염 방지)")
    void execute_callsUseCaseWithExactMemberUserIds() {
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                memberOf(MEMBER_A), memberOf(MEMBER_B)));
        given(getMonthScheduleUseCase.execute(MONTH, MEMBER_A))
                .willReturn(new MonthScheduleResponse(MONTH.toString(), List.of()));
        given(getMonthScheduleUseCase.execute(MONTH, MEMBER_B))
                .willReturn(new MonthScheduleResponse(MONTH.toString(), List.of()));

        sut.execute(GROUP_ID, MONTH);

        then(getMonthScheduleUseCase).should().execute(MONTH, MEMBER_A);
        then(getMonthScheduleUseCase).should().execute(MONTH, MEMBER_B);
    }

    private Membership memberOf(Long userId) {
        return Membership.of(GROUP_ID, userId, MemberRole.PATIENT, null);
    }
}
