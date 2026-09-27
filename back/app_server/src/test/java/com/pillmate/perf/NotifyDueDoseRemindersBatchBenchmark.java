package com.pillmate.perf;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.pillmate.doselog.domain.model.DoseLog;
import com.pillmate.doselog.domain.repository.DoseLogRepository;
import com.pillmate.notification.application.NotificationPersistenceService;
import com.pillmate.notification.application.NotifyDueDoseRemindersService;
import com.pillmate.notification.application.port.DrugNameLookupPort;
import com.pillmate.notification.application.port.NotificationSenderPort;
import com.pillmate.notification.application.port.PrescriptionSummaryPort;
import com.pillmate.notification.domain.model.Notification;
import com.pillmate.notification.infrastructure.fcm.FcmSenderAdapter;
import com.pillmate.notification.infrastructure.fcm.FirebaseMessagingProvider;
import com.pillmate.schedule.domain.model.Schedule;
import com.pillmate.schedule.domain.model.TimeOfDay;
import com.pillmate.schedule.domain.repository.ScheduleRepository;
import com.pillmate.user.domain.model.PushProvider;
import com.pillmate.user.domain.model.User;
import com.pillmate.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * NotifyDueDoseRemindersService.notifyDue() 를 "끼니시간 스파이크"처럼 다건 due 로 실행해
 * 실제 폴러 오케스트레이션(claim N회 + saveAll 1회 + sendAll 1회, 실 FCM 왕복) 처리량을 잰다.
 * 로컬 전용 — docker 재배포 없이 순수 JUnit 프로세스에서 서비스 빈을 직접 조립한다.
 *
 * 실행: PERF_FCM=true ./gradlew test --tests "*NotifyDueDoseRemindersBatchBenchmark"
 */
@Tag("perf")
class NotifyDueDoseRemindersBatchBenchmark {

    private static final String CREDS_DEFAULT = "../secrets/firebase-service-account.json";
    private static final Instant NOW = Instant.parse("2026-09-27T08:00:00Z");

    @Test
    void benchmark() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("PERF_FCM")),
                "PERF_FCM=true 로만 실행 (실 FCM 왕복)");
        Path credsPath = Path.of(envOr("PERF_FCM_CREDS", CREDS_DEFAULT));
        assumeTrue(Files.exists(credsPath), "creds 없음: " + credsPath.toAbsolutePath());

        NotificationSenderPort realFcm = buildRealFcmAdapter(credsPath);

        System.out.println("\n===== NotifyDueDoseRemindersService.notifyDue() 배치 처리량 (실 FCM, 무효토큰) =====");
        for (int n : List.of(100, 1000, 7000)) {
            runOnce(realFcm, n);
        }
        System.out.println("=======================================================================\n");
    }

    private void runOnce(NotificationSenderPort realFcm, int n) {
        DoseLogRepository doseLogRepository = Mockito.mock(DoseLogRepository.class);
        ScheduleRepository scheduleRepository = Mockito.mock(ScheduleRepository.class);
        UserRepository userRepository = Mockito.mock(UserRepository.class);
        NotificationPersistenceService persistence = Mockito.mock(NotificationPersistenceService.class);
        PrescriptionSummaryPort prescriptionSummaryPort = Mockito.mock(PrescriptionSummaryPort.class);
        DrugNameLookupPort drugNameLookupPort = Mockito.mock(DrugNameLookupPort.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

        List<DoseLog> due = new ArrayList<>(n);
        List<User> users = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            long doseLogId = i + 1L;
            long patientId = 100_000L + i; // 환자마다 스케줄 1개 — 실제 끼니시간 분포 근사
            DoseLog log = DoseLog.of((long) i + 1, patientId, NOW.minusSeconds(120));
            ReflectionTestUtils.setField(log, "id", doseLogId);
            due.add(log);

            Schedule schedule = Schedule.forPrescription(null, patientId, 1L,
                    TimeOfDay.MORNING, null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), patientId);
            given(scheduleRepository.findById((long) i + 1)).willReturn(Optional.of(schedule));
            given(doseLogRepository.markRemindedIfPending(doseLogId, NOW)).willReturn(1);

            User user = User.dummy("perf-" + i);
            ReflectionTestUtils.setField(user, "id", patientId);
            user.registerPushToken(fakeToken(i), PushProvider.EXPO);
            users.add(user);
        }
        given(doseLogRepository.findPendingNotRemindedBetween(any(), any())).willReturn(due);
        given(userRepository.findAllByIdIn(any())).willReturn(users);
        given(prescriptionSummaryPort.findById(1L)).willReturn(Optional.of(
                new PrescriptionSummaryPort.PrescriptionSummary(LocalDate.of(2026, 9, 1), "혈압약", false)));
        given(persistence.saveAll(any())).willAnswer(inv -> {
            List<Notification> list = inv.getArgument(0);
            long id = 1;
            for (Notification notif : list) {
                ReflectionTestUtils.setField(notif, "id", id++);
            }
            return list;
        });

        NotifyDueDoseRemindersService sut = new NotifyDueDoseRemindersService(
                doseLogRepository, scheduleRepository, persistence, userRepository,
                realFcm, prescriptionSummaryPort, drugNameLookupPort, clock);

        long start = System.nanoTime();
        int processed = sut.notifyDue();
        long elapsedNanos = System.nanoTime() - start;
        double sec = elapsedNanos / 1_000_000_000.0;
        System.out.printf("N=%-5d processed=%-5d time=%6.2fs | TPS=%7.1f | 순차(2.5 TPS) 대비 예상소요=%6.1fs%n",
                n, processed, sec, n / sec, n / 2.5);
    }

    private FcmSenderAdapter buildRealFcmAdapter(Path credsPath) throws Exception {
        FirebaseApp app = firebaseApp(credsPath);
        FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);
        FirebaseMessagingProvider provider = () -> Optional.of(messaging);
        return new FcmSenderAdapter(provider, new SimpleMeterRegistry());
    }

    private FirebaseApp firebaseApp(Path credsPath) throws Exception {
        for (FirebaseApp a : FirebaseApp.getApps()) {
            if (a.getName().equals("perf-bench-poller")) return a;
        }
        try (FileInputStream in = new FileInputStream(credsPath.toFile())) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            return FirebaseApp.initializeApp(options, "perf-bench-poller");
        }
    }

    private String fakeToken(int i) {
        StringBuilder sb = new StringBuilder("fPoll").append(i).append(":APA91b");
        String cs = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-";
        ThreadLocalRandom r = ThreadLocalRandom.current();
        while (sb.length() < 152) sb.append(cs.charAt(r.nextInt(cs.length())));
        return sb.toString();
    }

    private String envOr(String k, String d) { String v = System.getenv(k); return v != null ? v : d; }
}
