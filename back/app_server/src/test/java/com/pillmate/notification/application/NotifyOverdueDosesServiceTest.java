package com.pillmate.notification.application;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@DisplayName("NotifyOverdueDosesService — 예정시각+30분 경과 PENDING 지연 알림 폴링")
@ExtendWith(MockitoExtension.class)
class NotifyOverdueDosesServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-12T10:00:00Z");
    private static final Long PATIENT_ID = 1L;
    private static final Long SCHEDULE_ID = 10L;

    @Mock DoseLogRepository doseLogRepository;
    @Mock SendOverdueDoseNotificationService sendOverdueDoseNotificationService;
    @Spy  Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    @InjectMocks NotifyOverdueDosesService sut;

    @Test
    @DisplayName("선택 윈도우 — cutoff = now-30분, windowStart = cutoff-10분(과거 행 폭주 방지)")
    void notifyDue_queriesWithOverdueThresholdAndRecencyWindow() {
        given(doseLogRepository.findPendingOverdueNotNotifiedBetween(
                Instant.parse("2026-06-12T09:20:00Z"), Instant.parse("2026-06-12T09:30:00Z")))
                .willReturn(List.of());

        sut.notifyDue();

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        then(doseLogRepository).should()
                .findPendingOverdueNotNotifiedBetween(fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getValue()).isEqualTo(Instant.parse("2026-06-12T09:20:00Z"));
        assertThat(toCaptor.getValue()).isEqualTo(Instant.parse("2026-06-12T09:30:00Z"));
    }

    @Test
    @DisplayName("due 없으면 0 반환, 발송 미호출")
    void notifyDue_whenEmpty_returnsZero() {
        given(doseLogRepository.findPendingOverdueNotNotifiedBetween(any(), any())).willReturn(List.of());

        int sent = sut.notifyDue();

        assertThat(sent).isZero();
        then(sendOverdueDoseNotificationService).should(never()).send(anyLong());
    }

    @Test
    @DisplayName("원자 클레임 성공(1행) — send() 호출")
    void notifyDue_whenClaimSucceeds_sendsOnce() {
        DoseLog log = pendingLog(21L);
        given(doseLogRepository.findPendingOverdueNotNotifiedBetween(any(), any())).willReturn(List.of(log));
        given(doseLogRepository.markOverdueNotifiedIfPending(21L, FIXED_NOW)).willReturn(1);

        int sent = sut.notifyDue();

        assertThat(sent).isEqualTo(1);
        then(sendOverdueDoseNotificationService).should().send(21L);
    }

    @Test
    @DisplayName("원자 클레임 실패(0행 — 동시 처리/상태 변경) — send() 미호출 (멱등)")
    void notifyDue_whenClaimFails_doesNotSend() {
        DoseLog log = pendingLog(22L);
        given(doseLogRepository.findPendingOverdueNotNotifiedBetween(any(), any())).willReturn(List.of(log));
        given(doseLogRepository.markOverdueNotifiedIfPending(22L, FIXED_NOW)).willReturn(0);

        sut.notifyDue();

        then(sendOverdueDoseNotificationService).should(never()).send(22L);
    }

    @Test
    @DisplayName("폴러를 두 번 돌려도 같은 dose 는 1회만 발송 — 두 번째 폴에선 claim 0행 (원자 클레임 멱등)")
    void notifyDue_calledTwice_onlyFirstPollSends() {
        DoseLog log = pendingLog(23L);
        given(doseLogRepository.findPendingOverdueNotNotifiedBetween(any(), any())).willReturn(List.of(log));
        given(doseLogRepository.markOverdueNotifiedIfPending(23L, FIXED_NOW))
                .willReturn(1)  // 1차 폴 — 클레임 성공
                .willReturn(0); // 2차 폴 — 이미 클레임됨(overdue_notified_at 세팅됨) → 0행

        sut.notifyDue();
        sut.notifyDue();

        then(sendOverdueDoseNotificationService).should(times(1)).send(23L);
    }

    @Test
    @DisplayName("한 건 처리 실패해도 나머지 건 계속 진행")
    void notifyDue_whenOneFails_continuesOthers() {
        DoseLog log1 = pendingLog(11L);
        DoseLog log2 = pendingLog(12L);
        given(doseLogRepository.findPendingOverdueNotNotifiedBetween(any(), any()))
                .willReturn(List.of(log1, log2));
        given(doseLogRepository.markOverdueNotifiedIfPending(11L, FIXED_NOW)).willReturn(1);
        given(doseLogRepository.markOverdueNotifiedIfPending(12L, FIXED_NOW)).willReturn(1);
        willThrow(new RuntimeException("push down"))
                .given(sendOverdueDoseNotificationService).send(11L);

        sut.notifyDue();

        then(sendOverdueDoseNotificationService).should().send(12L);
    }

    private DoseLog pendingLog(Long id) {
        DoseLog log = DoseLog.of(SCHEDULE_ID, PATIENT_ID, FIXED_NOW.minusSeconds(1800));
        setId(log, id);
        return log;
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException("테스트 픽스처 id 주입 실패", e);
        }
    }
}
