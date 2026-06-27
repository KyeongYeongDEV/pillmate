package com.pillmate.doselog.infrastructure.scheduler;

import com.pillmate.doselog.infrastructure.partition.PartitionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DoseLogPartitionScheduler {

    private final PartitionManager partitionManager;

    @EventListener(ApplicationReadyEvent.class)
    public void ensurePartitionsOnStartup() {
        log.info("DoseLogPartitionScheduler startup: ensuring {} months of partitions", PartitionManager.MONTHS_AHEAD);
        partitionManager.ensurePartitions();
    }

    @Scheduled(cron = "0 0 1 1 * *", zone = "Asia/Seoul")
    public void ensurePartitionsMonthly() {
        log.info("DoseLogPartitionScheduler monthly: ensuring {} months of partitions", PartitionManager.MONTHS_AHEAD);
        partitionManager.ensurePartitions();
    }
}
