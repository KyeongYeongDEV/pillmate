package com.pillmate.user.infrastructure.scheduler;

import com.pillmate.user.application.DauMauQueryService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("DauMauMetricsScheduler — Gauge 등록 및 갱신")
class DauMauMetricsSchedulerTest {

    @Mock DauMauQueryService dauMauQueryService;

    private SimpleMeterRegistry meterRegistry;
    private DauMauMetricsScheduler scheduler;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        scheduler = new DauMauMetricsScheduler(dauMauQueryService, meterRegistry);
    }

    @Test
    @DisplayName("refresh() 호출 후 pillmate_dau Gauge가 DAU 값을 반환")
    void refresh_updatesGaugeWithDauValue() {
        given(dauMauQueryService.getDau()).willReturn(7L);
        given(dauMauQueryService.getMau()).willReturn(100L);

        scheduler.refresh();

        Gauge dau = meterRegistry.find("pillmate_dau").gauge();
        assertThat(dau).isNotNull();
        assertThat(dau.value()).isEqualTo(7.0);
    }

    @Test
    @DisplayName("refresh() 호출 후 pillmate_mau Gauge가 MAU 값을 반환")
    void refresh_updatesGaugeWithMauValue() {
        given(dauMauQueryService.getDau()).willReturn(7L);
        given(dauMauQueryService.getMau()).willReturn(200L);

        scheduler.refresh();

        Gauge mau = meterRegistry.find("pillmate_mau").gauge();
        assertThat(mau).isNotNull();
        assertThat(mau.value()).isEqualTo(200.0);
    }

    @Test
    @DisplayName("초기 상태: Gauge 는 등록되어 있고 값은 0")
    void initialState_gaugesRegisteredWithZeroValue() {
        assertThat(meterRegistry.find("pillmate_dau").gauge()).isNotNull();
        assertThat(meterRegistry.find("pillmate_mau").gauge()).isNotNull();
        assertThat(meterRegistry.find("pillmate_dau").gauge().value()).isEqualTo(0.0);
    }
}
