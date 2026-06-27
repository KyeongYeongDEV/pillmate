package com.pillmate.doselog.infrastructure.scheduler;

import com.pillmate.doselog.infrastructure.partition.PartitionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("DoseLogPartitionScheduler — 파티션 보장 트리거")
class DoseLogPartitionSchedulerTest {

    @Mock PartitionManager partitionManager;

    private DoseLogPartitionScheduler sut;

    @BeforeEach
    void setUp() {
        sut = new DoseLogPartitionScheduler(partitionManager);
    }

    @Test
    @DisplayName("ApplicationReadyEvent — partitionManager.ensurePartitions() 호출")
    void onStartup_callsEnsurePartitions() {
        sut.ensurePartitionsOnStartup();
        verify(partitionManager, times(1)).ensurePartitions();
    }

    @Test
    @DisplayName("월간 스케줄 — partitionManager.ensurePartitions() 호출")
    void onMonthly_callsEnsurePartitions() {
        sut.ensurePartitionsMonthly();
        verify(partitionManager, times(1)).ensurePartitions();
    }
}
