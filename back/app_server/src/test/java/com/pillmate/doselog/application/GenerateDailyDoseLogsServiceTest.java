package com.pillmate.doselog.application;

import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.doselog.domain.service.DoseLogSchedulePolicy;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@DisplayName("GenerateDailyDoseLogsService — 일일 dose_logs 생성 + 멱등")
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
        given(doseLogRepository.findByScheduleIdAndScheduledAt(anyLong(), any())).willReturn(Optional.empty());
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        int created = sut.generate(TODAY);

        // then
        assertThat(created).isEqualTo(4);
        then(doseLogRepository).should(times(4)).save(any(DoseLog.class));
    }

    @Test
    @DisplayName("이미 생성된 날 재실행 — 0건 추가 (멱등)")
    void generate_whenLogsAlreadyExist_createsZero() {
        // given
        given(scheduleRepository.findAllActiveOn(TODAY))
                .willReturn(List.of(schedule(1L, PATIENT_1, TimeOfDay.MORNING)));
        given(doseLogRepository.findByScheduleIdAndScheduledAt(anyLong(), any()))
                .willReturn(Optional.of(DoseLog.of(1L, PATIENT_1, Instant.now())));

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
        given(doseLogRepository.findByScheduleIdAndScheduledAt(anyLong(), any())).willReturn(Optional.empty());
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        int created = sut.generate(TODAY);

        // then
        assertThat(created).isEqualTo(2);
    }

    @Test
    @DisplayName("일부 기존 + 일부 신규 — 신규만 생성")
    void generate_whenSomeExist_createsOnlyMissing() {
        // given
        Schedule s1 = schedule(10L, PATIENT_1, TimeOfDay.MORNING);
        Schedule s2 = schedule(20L, PATIENT_1, TimeOfDay.NOON);
        given(scheduleRepository.findAllActiveOn(TODAY)).willReturn(List.of(s1, s2));

        Instant morningKst = policy.scheduledAtFor(TimeOfDay.MORNING, TODAY);
        Instant noonKst    = policy.scheduledAtFor(TimeOfDay.NOON, TODAY);

        given(doseLogRepository.findByScheduleIdAndScheduledAt(eq(10L), eq(morningKst)))
                .willReturn(Optional.of(DoseLog.of(10L, PATIENT_1, morningKst)));
        given(doseLogRepository.findByScheduleIdAndScheduledAt(eq(20L), eq(noonKst)))
                .willReturn(Optional.empty());
        given(doseLogRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        int created = sut.generate(TODAY);

        // then — 신규 1건만
        assertThat(created).isEqualTo(1);
        then(doseLogRepository).should(times(1)).save(any(DoseLog.class));
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
