package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.GroupDayScheduleResponse;
import com.pillmate.caregroup.application.dto.GroupDayScheduleResponse.MemberDayView;
import com.pillmate.caregroup.domain.model.MemberRole;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.schedule.application.GetDayScheduleUseCase;
import com.pillmate.schedule.application.dto.DayScheduleResponse;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@DisplayName("GetGroupDayScheduleService — 그룹 구성원별 하루 스케줄 집계")
@ExtendWith(MockitoExtension.class)
class GetGroupDayScheduleServiceTest {

    private static final Long GROUP_ID = 20L;
    private static final Long MEMBER_A = 1L;
    private static final Long MEMBER_B = 2L;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 7);

    @Mock CareGroupGuard careGroupGuard;
    @Mock MembershipRepository membershipRepository;
    @Mock UserRepository userRepository;
    @Mock GetDayScheduleUseCase getDayScheduleUseCase;

    private GetGroupDayScheduleService sut;

    @BeforeEach
    void setUp() {
        sut = new GetGroupDayScheduleService(
                careGroupGuard, membershipRepository, userRepository, getDayScheduleUseCase);
    }

    @Test
    @DisplayName("호출자가 해당 그룹 ACTIVE 멤버가 아니면 CareGroupGuard 예외가 그대로 전파된다")
    void execute_whenCallerNotAccessible_propagatesGuardException() {
        willThrow(new PillmateException(ErrorCode.GROUP_ACCESS_DENIED))
                .given(careGroupGuard).requireAccessible(GROUP_ID);

        assertThatThrownBy(() -> sut.execute(GROUP_ID, DATE))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);

        then(membershipRepository).shouldHaveNoInteractions();
        then(userRepository).shouldHaveNoInteractions();
        then(getDayScheduleUseCase).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("멤버 2명이면 각각의 이름+해당 날짜 스케줄이 매핑되어 2건 반환된다")
    void execute_twoMembers_returnsBothMemberViews() {
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                memberOf(MEMBER_A), memberOf(MEMBER_B)));
        given(userRepository.findById(MEMBER_A)).willReturn(Optional.of(userOf(MEMBER_A, "아버지")));
        given(userRepository.findById(MEMBER_B)).willReturn(Optional.of(userOf(MEMBER_B, "어머니")));
        DayScheduleResponse scheduleA = new DayScheduleResponse(DATE, 2, 1, List.of());
        DayScheduleResponse scheduleB = new DayScheduleResponse(DATE, 3, 3, List.of());
        given(getDayScheduleUseCase.execute(DATE, MEMBER_A, GROUP_ID)).willReturn(scheduleA);
        given(getDayScheduleUseCase.execute(DATE, MEMBER_B, GROUP_ID)).willReturn(scheduleB);

        GroupDayScheduleResponse response = sut.execute(GROUP_ID, DATE);

        assertThat(response.date()).isEqualTo(DATE);
        assertThat(response.members()).hasSize(2);
        assertThat(response.members()).extracting(MemberDayView::userId)
                .containsExactlyInAnyOrder(MEMBER_A, MEMBER_B);
        assertThat(response.members()).extracting(MemberDayView::name)
                .containsExactlyInAnyOrder("아버지", "어머니");
        assertThat(response.members()).extracting(MemberDayView::schedule)
                .containsExactlyInAnyOrder(scheduleA, scheduleB);
    }

    @Test
    @DisplayName("각 멤버 조회에 groupId 가 그대로 전달된다 (L2 공유 마스킹 판정 필수, 회귀 방지)")
    void execute_callsUseCaseWithExactGroupIdPerMember() {
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(
                memberOf(MEMBER_A), memberOf(MEMBER_B)));
        given(userRepository.findById(MEMBER_A)).willReturn(Optional.of(userOf(MEMBER_A, "아버지")));
        given(userRepository.findById(MEMBER_B)).willReturn(Optional.of(userOf(MEMBER_B, "어머니")));
        given(getDayScheduleUseCase.execute(DATE, MEMBER_A, GROUP_ID))
                .willReturn(new DayScheduleResponse(DATE, 0, 0, List.of()));
        given(getDayScheduleUseCase.execute(DATE, MEMBER_B, GROUP_ID))
                .willReturn(new DayScheduleResponse(DATE, 0, 0, List.of()));

        sut.execute(GROUP_ID, DATE);

        then(getDayScheduleUseCase).should().execute(DATE, MEMBER_A, GROUP_ID);
        then(getDayScheduleUseCase).should().execute(DATE, MEMBER_B, GROUP_ID);
    }

    @Test
    @DisplayName("이름 조회 실패 시 \"멤버\" 로 폴백한다")
    void execute_userNameLookupFails_fallsBackToDefaultName() {
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of(memberOf(MEMBER_A)));
        given(userRepository.findById(MEMBER_A)).willReturn(Optional.empty());
        given(getDayScheduleUseCase.execute(DATE, MEMBER_A, GROUP_ID))
                .willReturn(new DayScheduleResponse(DATE, 0, 0, List.of()));

        GroupDayScheduleResponse response = sut.execute(GROUP_ID, DATE);

        assertThat(response.members()).extracting(MemberDayView::name).containsExactly("멤버");
    }

    @Test
    @DisplayName("그룹 멤버가 0명이면 빈 members 를 반환하고 예외를 던지지 않는다")
    void execute_noMembers_returnsEmptyMembers() {
        given(membershipRepository.findByCareGroupId(GROUP_ID)).willReturn(List.of());

        GroupDayScheduleResponse response = sut.execute(GROUP_ID, DATE);

        assertThat(response.members()).isEmpty();
        then(userRepository).shouldHaveNoInteractions();
        then(getDayScheduleUseCase).shouldHaveNoInteractions();
    }

    private Membership memberOf(Long userId) {
        return Membership.of(GROUP_ID, userId, MemberRole.PATIENT, null);
    }

    private User userOf(Long id, String name) {
        return User.dummy(name);
    }
}
