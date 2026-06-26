package com.pillmate.user.infrastructure.scheduler;

import com.pillmate.user.application.DauMauQueryService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class DauMauMetricsScheduler {

    private final DauMauQueryService dauMauQueryService;
    private final AtomicLong dauValue = new AtomicLong(0);
    private final AtomicLong mauValue = new AtomicLong(0);

    public DauMauMetricsScheduler(DauMauQueryService dauMauQueryService, MeterRegistry meterRegistry) {
        this.dauMauQueryService = dauMauQueryService;
        Gauge.builder("pillmate_dau", dauValue, AtomicLong::get)
                .description("Daily Active Users (오늘 distinct 사용자 수)")
                .register(meterRegistry);
        Gauge.builder("pillmate_mau", mauValue, AtomicLong::get)
                .description("Monthly Active Users (30일 distinct 사용자 수)")
                .register(meterRegistry);
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void refresh() {
        try {
            dauValue.set(dauMauQueryService.getDau());
            mauValue.set(dauMauQueryService.getMau());
        } catch (Exception e) {
            log.warn("DAU/MAU Gauge 갱신 실패 (이전 값 유지)", e);
        }
    }
}
