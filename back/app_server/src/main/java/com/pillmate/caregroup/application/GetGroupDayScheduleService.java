package com.pillmate.caregroup.application;

import com.pillmate.caregroup.application.dto.GroupDayScheduleResponse;
import com.pillmate.caregroup.application.dto.GroupDayScheduleResponse.MemberDayView;
import com.pillmate.caregroup.domain.model.Membership;
import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.schedule.application.GetDayScheduleUseCase;
import com.pillmate.user.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetGroupDayScheduleService {

    private static final String DEFAULT_MEMBER_NAME = "멤버";

    private final CareGroupGuard careGroupGuard;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final GetDayScheduleUseCase getDayScheduleUseCase;

    @Transactional(readOnly = true)
    public GroupDayScheduleResponse execute(Long groupId, LocalDate date) {
        careGroupGuard.requireAccessible(groupId);
        List<Membership> members = membershipRepository.findByCareGroupId(groupId);
        return new GroupDayScheduleResponse(date, toMemberViews(members, groupId, date));
    }

    private List<MemberDayView> toMemberViews(List<Membership> members, Long groupId, LocalDate date) {
        return members.stream()
                .map(member -> toMemberView(member, groupId, date))
                .toList();
    }

    private MemberDayView toMemberView(Membership member, Long groupId, LocalDate date) {
        Long userId = member.getUserId();
        String name = loadMemberName(userId);
        return new MemberDayView(userId, name, getDayScheduleUseCase.execute(date, userId, groupId));
    }

    private String loadMemberName(Long userId) {
        return userRepository.findById(userId).map(user -> user.getName()).orElse(DEFAULT_MEMBER_NAME);
    }
}
