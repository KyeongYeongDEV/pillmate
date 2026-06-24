# Slack Incoming Webhook (무료 알림)

> 용도: 예외/스케줄잡 실패/배포 같은 "터지면 바로 안다" 푸시. 무료.
> ★환자정보·처방내용은 메시지에 넣지 말 것(요약/카운트만).

## 1. Webhook URL 발급
1. https://api.slack.com/apps → **Create New App** → From scratch
2. 워크스페이스 선택 → **Incoming Webhooks** 활성화(Activate)
3. **Add New Webhook to Workspace** → 알림 받을 채널 선택(예: `#pillmate-alerts`)
4. 생성된 `https://hooks.slack.com/services/XXX/YYY/ZZZ` 복사 → 서버 env `SLACK_WEBHOOK_URL` (★git 금지)

## 2. 테스트
```bash
curl -X POST -H 'Content-Type: application/json' \
  -d '{"text":"PillMate alert test ✅"}' "$SLACK_WEBHOOK_URL"
```

## 3. Spring 연동 (app-side 코드 — 별도 Dev 작업)
경량 Notifier(RestClient) 한 개:
```java
@Component
@RequiredArgsConstructor
public class SlackNotifier {
    private final RestClient.Builder builder;
    @Value("${slack.webhook-url:}") private String webhookUrl;

    public void send(String text) {            // 비어있으면 no-op(로컬 안전)
        if (webhookUrl == null || webhookUrl.isBlank()) return;
        builder.build().post().uri(webhookUrl)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("text", text)).retrieve().toBodilessEntity();
    }
}
```
연동 지점(환자정보 없이 요약만):
- `GlobalExceptionHandler` 5xx → `"[prod] 500 at POST /prescriptions/ocr"`
- 자정 dose_log 생성 잡 **0건/실패** → `"⚠️ dose_log 생성 0건 (날짜 X)"` (과거 0건 사고 재발 감지)
- 그룹 알림 폴러 실패, FCM 실패율 급증

## 4. Grafana/Sentry 에서도 같은 webhook 재사용
- Grafana Alerting Contact point → Slack(webhook URL)
- Sentry Alert → Slack integration

## 주의
- 메시지에 이름/처방내용/이미지URL/토큰 금지 → "무엇이/몇 건/어느 엔드포인트"만.
- 알림 폭주 방지: 동일 알림 rate-limit/그룹핑.
