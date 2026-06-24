# Grafana Cloud 설정 (★여기부터 시작)

> 무료 티어: 메트릭 10k series, 로그 50GB, 14일 보관. 신용카드 불필요.
> 우리 규모(앱 박스 1대 + 컨테이너 몇 개)면 보통 수천 series → 1만 안에 넉넉.

## 1. 계정 + 스택 생성
1. https://grafana.com → **Create free account** (GitHub/Google 로그인 가능)
2. 가입 시 **스택(stack)** 1개 자동 생성됨 (예: `pillmate.grafana.net`)
3. 이 스택이 호스팅 Grafana(대시보드) + Prometheus(메트릭) + Loki(로그) 를 다 포함

## 2. Prometheus remote_write 자격증명 발급
1. 스택 대시보드 → 좌측 **Connections** → **Prometheus** (또는 "Hosted Prometheus metrics")
2. **Remote Write Endpoint URL** 복사 → `.env` 의 `GRAFANA_CLOUD_PROM_URL`
3. **Username / Instance ID**(숫자) 복사 → `GRAFANA_CLOUD_PROM_USER`
4. **API Token 생성**: Account → **Access Policies** → Create token
   - scope: `metrics:write` (로그도 보낼 거면 `logs:write` 추가)
   - 생성된 토큰(`glc_...`) → `GRAFANA_CLOUD_API_KEY`

## 3. 앱 메트릭 노출 (선행 작업 — monitor/README.md 참고)
- Spring: `/api/v1/actuator/prometheus` 살아있는지 확인 (`curl`)
- FastAPI: `/metrics` 확인

## 4. Alloy 에이전트 실행
```bash
cd monitor/alloy
cp .env.example .env      # 위에서 받은 값들 채우기
docker compose up -d
docker logs pillmate-alloy   # remote_write 성공 로그 확인
```

## 5. Grafana에서 확인
1. 스택 Grafana → **Explore** → 데이터소스 `grafanacloud-...-prom`
2. 쿼리 `up` 실행 → `app-server`, `ai-server` 타겟이 `1`로 보이면 성공
3. 대시보드: **Dashboards > New > Import**
   - JVM: Grafana.com 대시보드 ID **4701**(JVM Micrometer)
   - 또는 `monitor/dashboards/` 의 JSON import

## 6. 알림(선택)
- Grafana Cloud **Alerting** → 규칙(예: `up == 0` 5분) → Contact point에 Slack webhook(아래 slack/SETUP.md) 연결

## 비용 가드
- scrape_interval 30s 유지(15s 미만 금지 — series 폭증)
- 불필요한 고-cardinality 라벨 금지(예: userId를 라벨로 X)
