# Sentry (에러 추적) — 무료 티어

> 무료: 5k errors/월, 1 프로젝트팀. 신용카드 불필요.
> ★의료 앱: 환자 정보·처방 내용·토큰이 에러 payload에 절대 안 들어가게 PII 스크러빙 필수.

## 1. 프로젝트 생성
1. https://sentry.io → 가입
2. Create Project → 플랫폼 **2개** 만들기:
   - `pillmate-app` (Java / Spring Boot)
   - `pillmate-ai` (Python)
   - (선택) `pillmate-mobile` (React Native)
3. 각 프로젝트의 **DSN** 복사 → 서버 env(`SENTRY_DSN`)로 주입 (★git 금지)

## 2. Spring Boot 연동 (app-side 코드 — 별도 Dev 작업)
`build.gradle`:
```gradle
implementation 'io.sentry:sentry-spring-boot-starter-jakarta:7.+'
```
`application.yml` (prod 프로필):
```yaml
sentry:
  dsn: ${SENTRY_DSN:}            # 비면 비활성(로컬 안전)
  environment: ${SPRING_PROFILES_ACTIVE:local}
  traces-sample-rate: 0.1
  send-default-pii: false        # ★PII 전송 금지
```
- `GlobalExceptionHandler`에서 도메인 예외(PillmateException)는 Sentry로 안 보내거나 level 낮게,
  진짜 5xx/예상외 예외만 캡처. 메시지에 환자정보 넣지 말 것(기존 룰).

## 3. FastAPI 연동 (app-side 코드)
```bash
uv add sentry-sdk
```
```python
import sentry_sdk
sentry_sdk.init(
    dsn=os.environ.get("SENTRY_DSN", ""),
    environment=settings.env,
    traces_sample_rate=0.1,
    send_default_pii=False,          # ★PII 금지
    before_send=scrub_pii,           # 처방/약품명/이미지URL 마스킹 훅
)
```
- `before_send`에서 request body·약품명·이미지 키 마스킹.

## 4. Slack 알림 연동
- Sentry 프로젝트 → **Settings > Integrations > Slack** 연결(무료) → 새 이슈 시 채널 알림
- 또는 Sentry Alert Rule → webhook(아래 slack/SETUP.md의 webhook URL)

## 체크리스트
- [ ] DSN은 서버 env만, git 금지
- [ ] send_default_pii=false 양쪽 다
- [ ] 환자정보/처방내용/JWT 마스킹 (before_send / 로깅 룰)
- [ ] 도메인 예외(4xx)는 노이즈라 필터, 5xx만 alert
