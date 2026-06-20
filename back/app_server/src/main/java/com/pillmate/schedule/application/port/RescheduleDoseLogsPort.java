package com.pillmate.schedule.application.port;

import java.time.LocalDate;
import java.time.LocalTime;

public interface RescheduleDoseLogsPort {

    void rescheduleFuturePending(Long scheduleId, LocalTime newTime, LocalDate fromDate);
}
