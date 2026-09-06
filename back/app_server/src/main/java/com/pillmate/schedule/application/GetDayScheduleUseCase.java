package com.pillmate.schedule.application;

import com.pillmate.schedule.application.dto.DayScheduleResponse;

import java.time.LocalDate;

public interface GetDayScheduleUseCase {

    DayScheduleResponse execute(LocalDate date);

    DayScheduleResponse execute(LocalDate date, Long patientId);

    /**
     * @param groupId 타인(patientId != 본인) 조회 시 L2(알약 정보) 공유 판정에 사용되는 케어그룹.
     *                본인 조회면 무시된다. 타인 조회인데 null 이면 안전하게 L2 를 마스킹한다.
     */
    DayScheduleResponse execute(LocalDate date, Long patientId, Long groupId);
}
