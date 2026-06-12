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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;

@DisplayName("NotifyDueGroupDosesService — 60초 경과 TAKEN 그룹 알림 폴링")
@ExtendWith(MockitoExtension.class)
class NotifyDueGroupDosesServiceTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-06-12T10:00:00Z");

    @Mock DoseLogRepository doseLogRepository;
    @Mock SendGroupDoseNotificationService sendGroupDoseNotificationService;
    @Spy  Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    @InjectMocks NotifyDueGroupDosesService sut;

    private static final Long PATIENT_ID = 1L;

    @Test
    @DisplayName("선택 윈도우 — windowStart = now-10분 (과거 행 폭주 방지), cutoff = now-60초")
    void notifyDue_queriesWithRecencyWindowAndCutoff() {
        // given
        given(doseLogRepository.findTakenNotGroupNotifiedBetween(
                FIXED_NOW.minusSeconds(600), FIXED_NOW.minusSeconds(60)))
                .willReturn(List.of());

        // when
        sut.notifyDue();

        // then
        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        then(doseLogRepository).should()
                .findTakenNotGroupNotifiedBetween(fromCaptor.capture(), toCaptor.capture());
        assertThat(fromCaptor.getValue()).isEqualTo(Instant.parse("2026-06-12T09:50:00Z"));
        assertThat(toCaptor.getValue()).isEqualTo(Instant.parse("2026-06-12T09:59:00Z"));
    }

    @Test
    @DisplayName("due 2건 — 각 건 checkedBy 를 actor 로 send 호출, 2 반환")
    void notifyDue_sendsPerDueLogWithCheckedByAsActor() {
        // given
        DoseLog log1 = takenLog(11L, 100L);
        DoseLog log2 = takenLog(12L, 200L);
        given(doseLogRepository.findTakenNotGroupNotifiedBetween(
                FIXED_NOW.minusSeconds(600), FIXED_NOW.minusSeconds(60)))
                .willReturn(List.of(log1, log2));

        // when
        int sent = sut.notifyDue();

        // then
        assertThat(sent).isEqualTo(2);
        then(sendGroupDoseNotificationService).should().send(11L, 100L);
        then(sendGroupDoseNotificationService).should().send(12L, 200L);
    }

    @Test
    @DisplayName("due 없으면 0 반환, send 미호출")
    void notifyDue_whenEmpty_returnsZero() {
        given(doseLogRepository.findTakenNotGroupNotifiedBetween(
                FIXED_NOW.minusSeconds(600), FIXED_NOW.minusSeconds(60)))
                .willReturn(List.of());

        int sent = sut.notifyDue();

        assertThat(sent).isZero();
        then(sendGroupDoseNotificationService).should(never()).send(anyLong(), anyLong());
    }

    @Test
    @DisplayName("한 건 발송 실패해도 나머지 건 계속 진행")
    void notifyDue_whenOneFails_continuesOthers() {
        // given
        DoseLog log1 = takenLog(11L, 100L);
        DoseLog log2 = takenLog(12L, 200L);
        given(doseLogRepository.findTakenNotGroupNotifiedBetween(
                FIXED_NOW.minusSeconds(600), FIXED_NOW.minusSeconds(60)))
                .willReturn(List.of(log1, log2));
        willThrow(new RuntimeException("push down"))
                .given(sendGroupDoseNotificationService).send(11L, 100L);

        // when
        sut.notifyDue();

        // then
        then(sendGroupDoseNotificationService).should().send(12L, 200L);
    }

    private DoseLog takenLog(Long id, Long checkedBy) {
        DoseLog log = DoseLog.of(10L, PATIENT_ID, FIXED_NOW.minusSeconds(120));
        log.take(checkedBy, Clock.fixed(FIXED_NOW.minusSeconds(90), ZoneOffset.UTC));
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
