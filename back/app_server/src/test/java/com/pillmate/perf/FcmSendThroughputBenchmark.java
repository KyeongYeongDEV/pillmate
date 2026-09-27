package com.pillmate.perf;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.pillmate.notification.application.port.NotificationSenderPort.NotificationCommand;
import com.pillmate.notification.infrastructure.fcm.FcmSenderAdapter;
import com.pillmate.notification.infrastructure.fcm.FirebaseMessagingProvider;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * FCM 발송 처리량 벤치 — 실 Firebase 프로젝트에 "무효 토큰"으로 실제 왕복(비용 0, 실기기 무영향).
 * 현재 리마인더 경로(순차 1메시지) vs 500-배치 vs 병렬 을 실측해 병목/개선여지를 정량화한다.
 *
 * 안전장치: 환경변수 PERF_FCM=true 일 때만 실행(assumeTrue). 일반 빌드/CI 에선 skip.
 * 실행: PERF_FCM=true ./gradlew test --tests "*FcmSendThroughputBenchmark"
 */
@Tag("perf")
class FcmSendThroughputBenchmark {

    private static final String CREDS_DEFAULT = "../secrets/firebase-service-account.json";

    @Test
    void benchmark() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("PERF_FCM")),
                "PERF_FCM=true 로만 실행 (실 FCM 왕복)");
        Path credsPath = Path.of(envOr("PERF_FCM_CREDS", CREDS_DEFAULT));
        assumeTrue(Files.exists(credsPath), "creds 없음: " + credsPath.toAbsolutePath());

        FcmSenderAdapter adapter = buildAdapter(credsPath);

        int seqN = intEnv("PERF_SEQ_N", 100);
        int batchCount = intEnv("PERF_BATCH_COUNT", 2);
        int batchSize = intEnv("PERF_BATCH_SIZE", 500);
        int parN = intEnv("PERF_PAR_N", 500);
        int parThreads = intEnv("PERF_PAR_THREADS", 32);

        System.out.println("\n========== FCM 발송 처리량 벤치 (무효토큰, 실 왕복) ==========");
        warmup(adapter);

        runSequential(adapter, seqN);
        runBatch(adapter, batchCount, batchSize);
        runParallel(adapter, parN, parThreads);

        System.out.println("=============================================================\n");
    }

    // ── 모드 1: 현재 리마인더 패턴 — 1메시지 sendAll 을 순차 N회 ──
    private void runSequential(FcmSenderAdapter adapter, int n) {
        long[] lat = new long[n];
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            long t0 = System.nanoTime();
            adapter.sendAll(List.of(command(i)));
            lat[i] = System.nanoTime() - t0;
        }
        long elapsed = System.nanoTime() - start;
        report("① 순차 1메시지 (현재 리마인더 경로)", n, elapsed, lat);
    }

    // ── 모드 2: 500-배치 sendEach ──
    private void runBatch(FcmSenderAdapter adapter, int batches, int size) {
        long[] lat = new long[batches];
        long start = System.nanoTime();
        int idx = 0;
        for (int b = 0; b < batches; b++) {
            List<NotificationCommand> batch = new ArrayList<>(size);
            for (int i = 0; i < size; i++) batch.add(command(idx++));
            long t0 = System.nanoTime();
            adapter.sendAll(batch);
            lat[b] = System.nanoTime() - t0;
        }
        long elapsed = System.nanoTime() - start;
        report("② " + size + "-배치 sendEach", batches * size, elapsed, perMsgLatency(lat, size));
        System.out.printf("   (배치 %d개, 배치당 지연: %s)%n", batches, msList(lat));
    }

    // ── 모드 3: 병렬 1메시지 (워커풀 N스레드) ──
    private void runParallel(FcmSenderAdapter adapter, int n, int threads) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        long[] lat = new long[n];
        long start = System.nanoTime();
        List<Future<?>> futures = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            futures.add(pool.submit(() -> {
                long t0 = System.nanoTime();
                adapter.sendAll(List.of(command(idx)));
                lat[idx] = System.nanoTime() - t0;
            }));
        }
        for (Future<?> f : futures) f.get();
        long elapsed = System.nanoTime() - start;
        pool.shutdown();
        report("③ 병렬 1메시지 (" + threads + " 스레드)", n, elapsed, lat);
    }

    private void warmup(FcmSenderAdapter adapter) {
        for (int i = 0; i < 5; i++) adapter.sendAll(List.of(command(900000 + i)));
    }

    private void report(String label, int msgs, long elapsedNanos, long[] perCallNanos) {
        double sec = elapsedNanos / 1_000_000_000.0;
        double tps = msgs / sec;
        long[] sorted = perCallNanos.clone();
        Arrays.sort(sorted);
        System.out.printf("%-34s | msgs=%-5d time=%6.2fs | TPS=%7.1f | p50=%5.0fms p95=%5.0fms%n",
                label, msgs, sec, tps, ms(pct(sorted, 50)), ms(pct(sorted, 95)));
    }

    // ── 헬퍼 ──
    private FcmSenderAdapter buildAdapter(Path credsPath) throws Exception {
        FirebaseApp app = firebaseApp(credsPath);
        FirebaseMessaging messaging = FirebaseMessaging.getInstance(app);
        FirebaseMessagingProvider provider = () -> Optional.of(messaging);
        return new FcmSenderAdapter(provider, new SimpleMeterRegistry());
    }

    private FirebaseApp firebaseApp(Path credsPath) throws Exception {
        for (FirebaseApp a : FirebaseApp.getApps()) {
            if (a.getName().equals("perf-bench")) return a;
        }
        try (FileInputStream in = new FileInputStream(credsPath.toFile())) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(in))
                    .build();
            return FirebaseApp.initializeApp(options, "perf-bench");
        }
    }

    // 실제 FCM 토큰 길이/문자셋을 흉내낸 무효 토큰 → FCM 이 UNREGISTERED/INVALID 로 거부(실 왕복, 기기 무영향)
    private NotificationCommand command(int i) {
        return new NotificationCommand(
                (long) i, (long) i, fakeToken(i), "복약 시간이에요", "아침 약 드실 시간이에요",
                Map.of("type", "DOSE_REMINDER", "route", "/home", "notificationId", String.valueOf(i)));
    }

    private String fakeToken(int i) {
        StringBuilder sb = new StringBuilder("fPerf").append(i).append(":APA91b");
        String cs = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-";
        ThreadLocalRandom r = ThreadLocalRandom.current();
        while (sb.length() < 152) sb.append(cs.charAt(r.nextInt(cs.length())));
        return sb.toString();
    }

    private long[] perMsgLatency(long[] batchNanos, int size) {
        // 배치 지연을 메시지당으로 환산(근사) — report 의 p50/p95 표기용
        long[] out = new long[batchNanos.length];
        for (int i = 0; i < batchNanos.length; i++) out[i] = batchNanos[i] / size;
        return out;
    }

    private String msList(long[] nanos) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nanos.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(String.format("%.0fms", ms(nanos[i])));
        }
        return sb.toString();
    }

    private long pct(long[] sorted, int p) {
        if (sorted.length == 0) return 0;
        int idx = (int) Math.ceil(p / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(idx, sorted.length - 1))];
    }

    private double ms(long nanos) { return nanos / 1_000_000.0; }

    private String envOr(String k, String d) { String v = System.getenv(k); return v != null ? v : d; }

    private int intEnv(String k, int d) {
        String v = System.getenv(k);
        return v != null ? Integer.parseInt(v) : d;
    }
}
