package com.pillmate.doselog.infrastructure.partition;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;

@Slf4j
@Component
@RequiredArgsConstructor
public class PartitionManager {

    public static final int MONTHS_AHEAD = 14;

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public void ensurePartitions() {
        ensurePartitions(YearMonth.now(clock), MONTHS_AHEAD);
    }

    void ensurePartitions(YearMonth from, int monthsAhead) {
        for (int i = 0; i < monthsAhead; i++) {
            createPartitionIfAbsent(from.plusMonths(i));
        }
    }

    private void createPartitionIfAbsent(YearMonth ym) {
        String tableName = partitionName(ym);
        String fromDate  = ym.atDay(1).toString();
        String toDate    = ym.plusMonths(1).atDay(1).toString();
        String sql = String.format(
                "CREATE TABLE IF NOT EXISTS %s PARTITION OF dose_logs FOR VALUES FROM ('%s') TO ('%s')",
                tableName, fromDate, toDate);
        log.info("PartitionManager ensuring partition={}", tableName);
        jdbcTemplate.execute(sql);
    }

    static String partitionName(YearMonth ym) {
        return String.format("dose_logs_%d_%02d", ym.getYear(), ym.getMonthValue());
    }
}
