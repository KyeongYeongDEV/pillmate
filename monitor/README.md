# PillMate 모니터링 (포터블 스캐폴드)

> 목표: **실질 $0**, 앱 박스(오라클 A1 12GB/2OCPU) 부담 최소.
> 원칙: **자체호스팅 Prometheus/Grafana/Loki 금지**(박스 RAM·자기감시 안티패턴) → SaaS 무료 + 경량 에이전트.
> 이 폴더는 앱 코드와 분리된 **이식 가능한 설정 묶음**이다. 어느 서버든 `alloy/`를 올리고 env만 채우면 동작.

## 구성

```
monitor/
├── alloy/                 # Grafana Alloy 에이전트(경량, ~수십MB) — 메트릭 수집→Grafana Cloud
│   ├── config.alloy
│   ├── docker-compose.yml
│   └── .env.example
├── grafana-cloud/SETUP.md # Grafana Cloud 무료 계정→스택→Alloy 연결 (★여기부터)
├── sentry/SETUP.md        # Sentry 무료 + Spring/FastAPI SDK + Slack 알림
├── slack/SETUP.md         # Incoming Webhook + 앱 연동(예외·스케줄잡 실패)
├── firebase-analytics/SETUP.md  # 모바일 분석(무료 티어)
└── dashboards/            # (선택) Grafana 대시보드 JSON
```

## 배포 순서 (권장)
1. **앱이 메트릭을 노출** (app-side 코드, 아직 미적용 — 아래 "선행 작업")
2. **Grafana Cloud** 무료 스택 생성 → `grafana-cloud/SETUP.md`
3. **Alloy** 에이전트 실행(`alloy/`) → 앱 메트릭 scrape → Cloud로 remote_write
4. **Sentry + Slack** 연동 → `sentry/`, `slack/`
5. (모바일) **Firebase Analytics** → `firebase-analytics/`

## ⚠️ 선행 작업 (앱 코드 — 이 폴더 밖, 별도 Dev 작업)
Alloy가 긁을 엔드포인트를 앱이 노출해야 한다:
- **Spring**: `io.micrometer:micrometer-registry-prometheus` 의존성 추가 →
  `management.endpoints.web.exposure.include=health,prometheus` → `/api/v1/actuator/prometheus`
- **FastAPI**: `prometheus-fastapi-instrumentator` →
  `Instrumentator().instrument(app).expose(app)` → `/metrics`
- 앱 특화 지표(권장): 스케줄잡 성공/0건, FCM sent/failed, LLM 비용, JVM GC

## 비용
전부 무료 티어 범위. Alloy 에이전트만 앱 박스(또는 별도 micro)에서 ~수십MB.
