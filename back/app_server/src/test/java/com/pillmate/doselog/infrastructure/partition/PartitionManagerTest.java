package com.pillmate.doselog.infrastructure.partition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PartitionManager — dose_logs 월별 파티션 자동 생성")
class PartitionManagerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.parse("2026-06-26T00:00:00Z"), ZoneOffset.UTC);

    @Mock JdbcTemplate jdbcTemplate;

    private PartitionManager sut;

    @BeforeEach
    void setUp() {
        sut = new PartitionManager(jdbcTemplate, FIXED_CLOCK);
    }

    @Test
    @DisplayName("ensurePartitions() — 현재월부터 14개월 파티션 CREATE SQL 실행")
    void ensurePartitions_creates14Months() {
        sut.ensurePartitions();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(PartitionManager.MONTHS_AHEAD)).execute(captor.capture());

        List<String> sqls = captor.getAllValues();
        assertThat(sqls).hasSize(PartitionManager.MONTHS_AHEAD);
        // 첫 번째 파티션: 2026-06
        assertThat(sqls.get(0))
                .contains("CREATE TABLE IF NOT EXISTS")
                .contains("dose_logs_2026_06")
                .contains("PARTITION OF dose_logs")
                .contains("'2026-06-01'")
                .contains("'2026-07-01'");
        // 마지막 파티션: 2026-06 + 13개월 = 2027-07
        assertThat(sqls.get(PartitionManager.MONTHS_AHEAD - 1))
                .contains("dose_logs_2027_07");
    }

    @Test
    @DisplayName("파티션 이름 — dose_logs_YYYY_MM 두 자리 월 형식")
    void partitionName_formattedWithZeroPaddedMonth() {
        assertThat(PartitionManager.partitionName(YearMonth.of(2026, 6))).isEqualTo("dose_logs_2026_06");
        assertThat(PartitionManager.partitionName(YearMonth.of(2026, 12))).isEqualTo("dose_logs_2026_12");
        assertThat(PartitionManager.partitionName(YearMonth.of(2027, 1))).isEqualTo("dose_logs_2027_01");
    }

    @Test
    @DisplayName("연도 경계(12월) — 다음 달 TO 값이 2027-01-01 로 올바르게 생성")
    void ensurePartitions_yearBoundary_correctRanges() {
        sut.ensurePartitions(YearMonth.of(2026, 12), 2);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).execute(captor.capture());

        List<String> sqls = captor.getAllValues();
        assertThat(sqls.get(0))
                .contains("dose_logs_2026_12")
                .contains("'2026-12-01'")
                .contains("'2027-01-01'");
        assertThat(sqls.get(1))
                .contains("dose_logs_2027_01")
                .contains("'2027-01-01'")
                .contains("'2027-02-01'");
    }

    @Test
    @DisplayName("멱등 — 두 번 호출해도 예외 없이 2×14 번 실행 (IF NOT EXISTS 가 중복 방지)")
    void ensurePartitions_idempotent_doublesExecution() {
        sut.ensurePartitions();
        sut.ensurePartitions();

        verify(jdbcTemplate, times(PartitionManager.MONTHS_AHEAD * 2)).execute(anyString());
    }
}
