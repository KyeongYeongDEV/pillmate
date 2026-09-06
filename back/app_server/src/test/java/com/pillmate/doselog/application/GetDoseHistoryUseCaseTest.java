package com.pillmate.doselog.application;

import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.CareGroupGuard;
import com.pillmate.doselog.application.dto.DoseLogResponse;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@DisplayName("GetDoseHistoryUseCase — 그룹 접근 가드 단위 테스트 (P0 유출 취약점 회귀 방지)")
@ExtendWith(MockitoExtension.class)
class GetDoseHistoryUseCaseTest {

    @Mock DoseLogRepository doseLogRepository;
    @Mock CareGroupGuard careGroupGuard;
    @InjectMocks GetDoseHistoryUseCase sut;

    private static final Long PATIENT_ID = 1L;
    private static final Long SCHEDULE_ID = 10L;
    private static final Instant FROM = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-08-31T23:59:59Z");

    @Test
    @DisplayName("본인/같은 그룹 patientId → 가드 통과 후 이력 조회")
    void getHistory_whenAccessible_returnsHistory() {
        // given
        given(doseLogRepository.findByPatientIdAndScheduledAtBetween(PATIENT_ID, FROM, TO))
                .willReturn(List.of(DoseLog.of(SCHEDULE_ID, PATIENT_ID, FROM)));

        // when
        List<DoseLogResponse> result = sut.getHistory(PATIENT_ID, FROM, TO);

        // then
        then(careGroupGuard).should().requirePatientAccessible(PATIENT_ID);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).patientId()).isEqualTo(PATIENT_ID);
    }

    @Test
    @DisplayName("비그룹원 patientId → GROUP_ACCESS_DENIED 로 차단, doseLogRepository 무호출(유출 전 차단)")
    void getHistory_whenStrangerPatientId_throwsAndNeverQueries() {
        // given
        Long strangerId = 999L;
        willThrow(new PillmateException(ErrorCode.GROUP_ACCESS_DENIED))
                .given(careGroupGuard).requirePatientAccessible(strangerId);

        // when & then
        assertThatThrownBy(() -> sut.getHistory(strangerId, FROM, TO))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.GROUP_ACCESS_DENIED);
        then(doseLogRepository).shouldHaveNoInteractions();
    }
}
