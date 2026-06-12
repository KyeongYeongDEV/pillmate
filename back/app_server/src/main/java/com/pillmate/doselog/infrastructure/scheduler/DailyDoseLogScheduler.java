package com.pillmate.doselog.infrastructure.scheduler;

import com.pillmate.doselog.application.GenerateDailyDoseLogsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class DailyDoseLogScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final GenerateDailyDoseLogsUseCase generateDailyDoseLogs;
    private final Clock clock;

    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void generateAtMidnight() {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        log.info("DailyDoseLogScheduler midnight trigger date={}", today);
        runSafely(today);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void generateOnStartup() {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        log.info("DailyDoseLogScheduler startup trigger date={}", today);
        runSafely(today);
    }

    private void runSafely(LocalDate date) {
        try {
            int created = generateDailyDoseLogs.generate(date);
            log.info("DailyDoseLogScheduler done date={} created={}", date, created);
        } catch (RuntimeException ex) {
            log.error("DailyDoseLogScheduler failed date={} reason={}", date, ex.getMessage(), ex);
        }
    }
}
