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

/**
 * 그룹 멤버 카드에서 특정 멤버를 바로 재촉 — 특정 dose 를 고르지 않고, 그 멤버의 이 그룹 소속
 * 스케줄 중 가장 오래 놓친(scheduledAt 가장 이른) PENDING dose 를 찾아
 * {@link SendDoseNudgeService} 에 위임한다. 쿨다운·당사자 캡·L2 무관 발송 로직은
 * SendDoseNudgeService 를 그대로 재사용하고 여기서 재구현하지 않는다.
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

        DoseLog overdue = findEarliestOverduePending(groupId, targetUserId);
        return sendDoseNudgeService.nudge(overdue.getId(), callerUserId);
    }

    private DoseLog findEarliestOverduePending(Long groupId, Long targetUserId) {
        List<Long> scheduleIds = scheduleIdsInGroup(groupId, targetUserId);
        return doseLogRepository
                .findEarliestOverduePendingByScheduleIds(targetUserId, scheduleIds, Instant.now(clock))
                .orElseThrow(() -> new PillmateException(ErrorCode.NUDGE_NO_OVERDUE_DOSE));
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
