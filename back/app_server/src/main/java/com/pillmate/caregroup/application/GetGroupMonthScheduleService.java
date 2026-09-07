package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.GroupMonthScheduleResponse;
import com.pillmate.caregroup.application.dto.GroupMonthScheduleResponse.GroupDayView;
import com.pillmate.caregroup.application.dto.GroupMonthScheduleResponse.MemberAdherenceView;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.schedule.application.GetMonthScheduleUseCase;
import com.pillmate.schedule.application.dto.MonthScheduleResponse.DayAdherenceView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class GetGroupMonthScheduleService {

    private final CareGroupGuard careGroupGuard;
    private final MembershipRepository membershipRepository;
    private final GetMonthScheduleUseCase getMonthScheduleUseCase;

    @Transactional(readOnly = true)
    public GroupMonthScheduleResponse execute(Long groupId, YearMonth month) {
        careGroupGuard.requireAccessible(groupId);
        List<Membership> members = membershipRepository.findByCareGroupId(groupId);
        Map<LocalDate, List<MemberAdherenceView>> byDate = mergeByDate(members, month);
        return new GroupMonthScheduleResponse(month.toString(), toDayViews(byDate));
    }

    private Map<LocalDate, List<MemberAdherenceView>> mergeByDate(List<Membership> members, YearMonth month) {
        Map<LocalDate, List<MemberAdherenceView>> byDate = new TreeMap<>();
        for (Membership member : members) {
            accumulateMemberDays(byDate, member, month);
        }
        return byDate;
    }

    private void accumulateMemberDays(
            Map<LocalDate, List<MemberAdherenceView>> byDate, Membership member, YearMonth month) {
        Long userId = member.getUserId();
        List<DayAdherenceView> days = getMonthScheduleUseCase.execute(month, userId).days();
        for (DayAdherenceView day : days) {
            byDate.computeIfAbsent(day.date(), key -> new ArrayList<>())
                    .add(new MemberAdherenceView(userId, day.adherence()));
        }
    }

    private List<GroupDayView> toDayViews(Map<LocalDate, List<MemberAdherenceView>> byDate) {
        return byDate.entrySet().stream()
                .map(entry -> new GroupDayView(entry.getKey(), entry.getValue()))
                .toList();
    }
}
