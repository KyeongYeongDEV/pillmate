package com.pillmate.doselog.application;

import com.pillmate.activity.application.ActivityFeedAppender;
import com.pillmate.common.exception.ErrorCode;
import com.pillmate.common.exception.PillmateException;
import com.pillmate.common.security.UserContext;
import com.pillmate.doselog.application.dto.CheckDoseRequest;
import com.pillmate.doselog.application.dto.DoseLogResponse;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.model.DoseStatus;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import com.pillmate.user.domain.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@DisplayName("CheckDoseUseCase — TAKE/SKIP/권한/멱등 단위")
@ExtendWith(MockitoExtension.class)
class CheckDoseUseCaseTest {

    @Mock DoseLogRepository doseLogRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock UserRepository userRepository;
    @Mock ActivityFeedAppender activityFeedAppender;
    @InjectMocks CheckDoseUseCase sut;

    private static final Long PATIENT_ID = 1L;
    private static final Long DOSE_LOG_ID = 5L;
    private static final Long SCHEDULE_ID = 10L;

    @BeforeEach
    void setUp() {
        UserContext.set(PATIENT_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("TAKE 액션 시 DoseLog status TAKEN 전환 + ActivityFeedAppender 호출")
    void check_whenActionTake_marksDoseLogTaken() {
        // given
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, Instant.now());
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.empty());

        // when
        DoseLogResponse response = sut.check(new CheckDoseRequest(DOSE_LOG_ID, "TAKE", null), PATIENT_ID);

        // then
        assertThat(response.status()).isEqualTo(DoseStatus.TAKEN);
        assertThat(response.checkedBy()).isEqualTo(PATIENT_ID);
    }

    @Test
    @DisplayName("UserContext 와 patient_id 불일치 시 PATIENT_ACCESS_DENIED (PILL_016) 예외")
    void check_whenAuthorIsNotPatient_throws() {
        // given
        UserContext.set(99L);
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, Instant.now());
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));

        // when / then
        assertThatThrownBy(() -> sut.check(new CheckDoseRequest(DOSE_LOG_ID, "TAKE", null), 99L))
                .isInstanceOf(PillmateException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PATIENT_ACCESS_DENIED);
    }

    @Test
    @DisplayName("이미 TAKEN 상태에서 재호출 시 멱등 — status 변경 없음")
    void check_whenAlreadyTaken_isIdempotent() {
        // given
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, Instant.now());
        doseLog.take(PATIENT_ID);
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(scheduleRepository.findById(SCHEDULE_ID)).willReturn(Optional.empty());

        // when
        DoseLogResponse response = sut.check(new CheckDoseRequest(DOSE_LOG_ID, "TAKE", null), PATIENT_ID);

        // then
        assertThat(response.status()).isEqualTo(DoseStatus.TAKEN);
    }

    @Test
    @DisplayName("SKIP 액션 시 DoseLog status SKIPPED + skipReason 저장")
    void check_whenSkip_marksSkippedWithReason() {
        // given
        DoseLog doseLog = DoseLog.of(SCHEDULE_ID, PATIENT_ID, Instant.now());
        given(doseLogRepository.findById(DOSE_LOG_ID)).willReturn(Optional.of(doseLog));
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        DoseLogResponse response = sut.check(new CheckDoseRequest(DOSE_LOG_ID, "SKIP", "운동 중"), PATIENT_ID);

        // then
        assertThat(response.status()).isEqualTo(DoseStatus.SKIPPED);
        assertThat(response.skipReason()).isEqualTo("운동 중");
    }
}
