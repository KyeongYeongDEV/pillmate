package com.pillmate.caregroup.application;

import com.pillmate.caregroup.domain.repository.MembershipRepository;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.notification.application.SendDoseNudgeService;
import com.pillmate.notification.application.dto.NudgeResponse;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 그룹 멤버 카드에서 특정 멤버를 바로 재촉 — 그 멤버의 이 그룹 소속 스케줄 중 가장 오래 놓친
 * (scheduledAt 가장 이른) PENDING dose 가 있으면 그 dose 에 묶어 알리고, 없으면 스케줄과
 * 무관한 일반 알림으로 폴백한다. {@link SendDoseNudgeService} 에 위임하고 여기서 재구현하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class SendMemberNudgeService {

    private final MembershipRepository membershipRepository;
    private final ScheduleRepository scheduleRepository;
    private final DoseLogRepository doseLogRepository;
    private final SendDoseNudgeService sendDoseNudgeService;
    private final Clock clock;

    public NudgeResponse nudge(Long groupId, Long targetUserId, Long callerUserId) {
        requireActiveMember(groupId, callerUserId);
        requireValidTarget(groupId, targetUserId, callerUserId);

        return findEarliestOverduePending(groupId, targetUserId)
                .map(overdue -> sendDoseNudgeService.nudge(overdue.getId(), callerUserId))
                .orElseGet(() -> sendDoseNudgeService.nudgeGeneral(groupId, targetUserId, callerUserId));
    }

    private Optional<DoseLog> findEarliestOverduePending(Long groupId, Long targetUserId) {
        List<Long> scheduleIds = scheduleIdsInGroup(groupId, targetUserId);
        return doseLogRepository
                .findEarliestOverduePendingByScheduleIds(targetUserId, scheduleIds, Instant.now(clock));
    }

    private List<Long> scheduleIdsInGroup(Long groupId, Long targetUserId) {
        return scheduleRepository.findAllByPatientId(targetUserId).stream()
                .filter(schedule -> groupId.equals(schedule.getCareGroupId()))
                .map(Schedule::getId)
                .toList();
    }

    private void requireActiveMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByCareGroupIdAndUserId(groupId, userId)) {
            throw new PillmateException(ErrorCode.GROUP_ACCESS_DENIED);
        }
    }

    private void requireValidTarget(Long groupId, Long targetUserId, Long callerUserId) {
        boolean isSelf = callerUserId.equals(targetUserId);
        boolean isMember = membershipRepository.existsByCareGroupIdAndUserId(groupId, targetUserId);
        if (isSelf || !isMember) {
            throw new PillmateException(ErrorCode.NUDGE_TARGET_INVALID);
        }
    }
}
