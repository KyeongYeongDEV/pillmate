package com.pillmate.doselog.application;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.doselog.domain.service.DoseLogSchedulePolicy;
import com.pillmate.prescription.application.port.DoseLogBackfillPort.BackfillSlot;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@DisplayName("GenerateDailyDoseLogsService — 일일 dose_logs 생성 + (schedule, 날짜) 멱등")
@ExtendWith(MockitoExtension.class)
class GenerateDailyDoseLogsServiceTest {

    @Mock ScheduleRepository scheduleRepository;
    @Mock DoseLogRepository doseLogRepository;
    @Spy  DoseLogSchedulePolicy policy = new DoseLogSchedulePolicy();
    @InjectMocks GenerateDailyDoseLogsService sut;

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 12);
    private static final Long PATIENT_1 = 1L;
    private static final Long PATIENT_2 = 2L;

    @Test
    @DisplayName("미생성 날 — 활성 스케줄 수만큼 dose_log 신규 생성")
    void generate_whenNoExistingLogs_createsOnePerSchedule() {
        // given
        List<Schedule> schedules = List.of(
                schedule(1L, PATIENT_1, TimeOfDay.MORNING),
                schedule(2L, PATIENT_1, TimeOfDay.NOON),
                schedule(3L, PATIENT_1, TimeOfDay.EVENING),
                schedule(4L, PATIENT_1, TimeOfDay.BEDTIME)
        );
        given(scheduleRepository.findAllActiveOn(TODAY)).willReturn(schedules);
        given(doseLogRepository.existsByScheduleIdAndScheduledAtInRange(anyLong(), any(), any())).willReturn(false);
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        int created = sut.generate(TODAY);

        // then
        assertThat(created).isEqualTo(4);
        then(doseLogRepository).should(times(4)).save(any(DoseLog.class));
    }

    @Test
    @DisplayName("같은 날 이미 dose_log 존재(시각 무관) — 0건 추가 (날짜 기준 멱등)")
    void generate_whenLogExistsOnDate_createsZero() {
        // given — 시각이 달라도(시간 변경 후 재실행) 같은 날 존재하면 신규 생성 X
        given(scheduleRepository.findAllActiveOn(TODAY))
                .willReturn(List.of(schedule(1L, PATIENT_1, TimeOfDay.MORNING)));
        given(doseLogRepository.existsByScheduleIdAndScheduledAtInRange(anyLong(), any(), any())).willReturn(true);

        // when
        int created = sut.generate(TODAY);

        // then
        assertThat(created).isZero();
        then(doseLogRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("활성 스케줄 없는 날 — 0건 생성, save 미호출")
    void generate_whenNoActiveSchedules_createsZero() {
        given(scheduleRepository.findAllActiveOn(TODAY)).willReturn(List.of());

        int created = sut.generate(TODAY);

        assertThat(created).isZero();
        then(doseLogRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("두 환자 각각 스케줄 보유 — 각자 dose_log 생성")
    void generate_multiplePatients_createsForAll() {
        // given
        given(scheduleRepository.findAllActiveOn(TODAY)).willReturn(List.of(
                schedule(1L, PATIENT_1, TimeOfDay.MORNING),
                schedule(2L, PATIENT_2, TimeOfDay.MORNING)
        ));
        given(doseLogRepository.existsByScheduleIdAndScheduledAtInRange(anyLong(), any(), any())).willReturn(false);
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        int created = sut.generate(TODAY);

        // then
        assertThat(created).isEqualTo(2);
    }

    @Test
    @DisplayName("일부 기존 + 일부 신규 — 신규만 생성 (schedule 별 날짜 멱등)")
    void generate_whenSomeExist_createsOnlyMissing() {
        // given
        Schedule s1 = schedule(10L, PATIENT_1, TimeOfDay.MORNING);
        Schedule s2 = schedule(20L, PATIENT_1, TimeOfDay.NOON);
        given(scheduleRepository.findAllActiveOn(TODAY)).willReturn(List.of(s1, s2));

        given(doseLogRepository.existsByScheduleIdAndScheduledAtInRange(eq(10L), any(), any())).willReturn(true);
        given(doseLogRepository.existsByScheduleIdAndScheduledAtInRange(eq(20L), any(), any())).willReturn(false);
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        int created = sut.generate(TODAY);

        // then — 신규 1건만
        assertThat(created).isEqualTo(1);
        then(doseLogRepository).should(times(1)).save(any(DoseLog.class));
    }

    // ─── T-BE-DOSELOG-BACKFILL-ON-REGISTER — 처방전 등록 직후 오늘자 즉시 백필 ───

    @Test
    @DisplayName("(a) 오늘 등록 → 오늘 dose_logs 즉시 생성(슬롯 수만큼)")
    void backfillToday_newSchedulesToday_createsOnePerSlot() {
        // given — 오늘 등록된 처방전의 신규 스케줄 2개(아침/저녁), 활성기간이 오늘 포함
        List<BackfillSlot> slots = List.of(
                new BackfillSlot(1L, LocalTime.of(8, 0), TODAY, TODAY.plusDays(6)),
                new BackfillSlot(2L, LocalTime.of(19, 0), TODAY, TODAY.plusDays(6))
        );
        given(doseLogRepository.existsByScheduleIdAndScheduledAtInRange(anyLong(), any(), any())).willReturn(false);
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        int created = sut.backfillToday(PATIENT_1, slots, TODAY);

        // then
        assertThat(created).isEqualTo(2);
        then(doseLogRepository).should(times(2)).save(any(DoseLog.class));
    }

    @Test
    @DisplayName("(b) 이미 오늘 dose_log 존재(배치와 겹침) — 재호출해도 중복 생성 0 (멱등)")
    void backfillToday_whenAlreadyExists_createsZero_idempotent() {
        // given — 야간 배치가 먼저 실행되어 이미 오늘자 dose_log 존재하는 상황을 시뮬레이션
        List<BackfillSlot> slots = List.of(new BackfillSlot(1L, LocalTime.of(8, 0), TODAY, TODAY.plusDays(6)));
        given(doseLogRepository.existsByScheduleIdAndScheduledAtInRange(anyLong(), any(), any())).willReturn(true);

        // when
        int created = sut.backfillToday(PATIENT_1, slots, TODAY);

        // then
        assertThat(created).isZero();
        then(doseLogRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("(c) 활성 시작일이 미래인 슬롯 — 오늘 미생성(시작일 도래 시 야간 배치가 담당)")
    void backfillToday_futureStartDate_doesNotCreateToday() {
        // given — 스케줄 startDate 가 오늘보다 이후
        List<BackfillSlot> slots = List.of(
                new BackfillSlot(1L, LocalTime.of(8, 0), TODAY.plusDays(1), TODAY.plusDays(10)));

        // when
        int created = sut.backfillToday(PATIENT_1, slots, TODAY);

        // then
        assertThat(created).isZero();
        then(doseLogRepository).should(never()).existsByScheduleIdAndScheduledAtInRange(anyLong(), any(), any());
        then(doseLogRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("종료일이 오늘 이전인 슬롯(이미 만료) — 오늘 미생성")
    void backfillToday_endDateBeforeToday_doesNotCreate() {
        List<BackfillSlot> slots = List.of(
                new BackfillSlot(1L, LocalTime.of(8, 0), TODAY.minusDays(10), TODAY.minusDays(1)));

        int created = sut.backfillToday(PATIENT_1, slots, TODAY);

        assertThat(created).isZero();
        then(doseLogRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("빈 슬롯 목록 — 0건, repository 미호출")
    void backfillToday_emptySlots_createsZero() {
        int created = sut.backfillToday(PATIENT_1, List.of(), TODAY);

        assertThat(created).isZero();
        then(doseLogRepository).shouldHaveNoInteractions();
    }

    // ─────────────────────────────────────────────────────
    // 헬퍼 — reflection으로 id 주입 (JPA 미영속 객체)
    // ─────────────────────────────────────────────────────

    private Schedule schedule(Long id, Long patientId, TimeOfDay timeOfDay) {
        Schedule s = Schedule.of(1L, patientId, 10L, timeOfDay,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), 1L);
        setId(s, id);
        return s;
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
