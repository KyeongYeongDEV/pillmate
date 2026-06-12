package com.pillmate.schedule.application;

import com.pillmate.common.security.UserContext;
import com.pillmate.schedule.application.dto.MonthScheduleResponse;
import com.pillmate.schedule.application.dto.MonthScheduleResponse.DayAdherenceView;
import com.pillmate.schedule.application.port.ScheduleMonthQueryPort;
import com.pillmate.schedule.application.port.ScheduleMonthQueryPort.DayDoseCount;
import com.pillmate.schedule.domain.model.Adherence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GetMonthScheduleService implements GetMonthScheduleUseCase {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final ScheduleMonthQueryPort scheduleMonthQueryPort;

    @Override
    @Transactional(readOnly = true)
    public MonthScheduleResponse execute(YearMonth month) {
        Long patientId = UserContext.get();
        Instant from = kstMonthStart(month);
        Instant to = kstMonthStart(month.plusMonths(1));
        List<DayDoseCount> counts = scheduleMonthQueryPort.findDailyDoseCounts(patientId, from, to);
        return new MonthScheduleResponse(month.toString(), counts.stream().map(this::toView).toList());
    }

    private Instant kstMonthStart(YearMonth month) {
        return month.atDay(1).atStartOfDay(KST).toInstant();
    }

    private DayAdherenceView toView(DayDoseCount count) {
        Adherence adherence = Adherence.of(count.takenCount(), count.totalCount());
        return new DayAdherenceView(count.date(), count.totalCount(), count.takenCount(), adherence.name());
    }
}
