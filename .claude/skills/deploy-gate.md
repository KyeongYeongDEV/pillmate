# Deploy Gate — 배포 전 필수 게이트 체크리스트

> main 머지/배포 실행 전 CTO 가 이 목록을 순서대로 통과시킨다. 하나라도 미통과면 배포 중단.
> 갱신: 배포 관련 결정·사고가 생길 때마다 이 파일에 누적 (2026-07-07 제정).

## G1. 시크릿 (secret-safety.md 연동)
- [ ] **GEMINI_API_KEY 로테이션** — 2026-07-06 로컬 로그 노출건. 새 키 발급 → GitHub Secrets + 로컬 .env 교체
- [ ] **OPENAI_API_KEY 로테이션** — 동일
- [ ] **PILLMATE_JWT_SECRET**: prod 전용 강한 값 (`openssl rand -base64 48`) — 로컬 placeholder 재사용 시 ProductionSecurityValidator 가 부팅 거부
- [ ] GitHub Secrets/Variables 등록 완료 (`gh-secrets-from-env.sh --dry-run` 으로 목록 검증 후 실등록)
- [ ] git 히스토리에 시크릿 0 (`git log -p | grep -iE 'api_key|secret' ` 스팟체크)

## G2. 인프라 (오라클 VM + DuckDNS)
- [ ] self-hosted runner 온라인 (repo Settings → Actions → Runners)
- [ ] 포트 80/443: 오라클 Security List **그리고** VM iptables 양쪽 오픈 (하나만 열면 안 됨)
- [ ] DuckDNS `pillmatefriend.duckdns.org` → VM IP 최신 (IP 변경 대비 duckdns cron 갱신 스크립트 권장)
- [ ] Caddy HTTPS 발급 확인 (`curl -I https://pillmatefriend.duckdns.org`)
- [ ] DB 백업 cron 배선 확인 (`backup_postgres.sh` + 복원 리허설 1회) — **백업 없는 배포 금지** (db-safety)
- [ ] 주기적 `docker image prune -af --filter until=168h` cron (blue-green 이미지 누적 방지)
- [ ] DB 백업 **S3 오프사이트 + 30일 자동 만료** (`BACKUP_S3=true` cron + `setup-s3-backup-lifecycle.sh --apply`, `db-backups/` prefix) — VM 유실 대비. 라이프사이클은 기존 규칙 병합(처방전 이미지 규칙 불변)

## G3. 프로파일/설정 정합
- [ ] `SPRING_PROFILES_ACTIVE=production` + `PILLMATE_DEV_FALLBACK=false` (워크플로우 하드코딩 확인)
- [ ] ProductionSecurityValidator 부팅 통과 (약한 JWT/dev-fallback 거부 동작 확인)
- [ ] ai-server 8001 호스트 미노출 (expose only), admin 엔드포인트 allowlist 설정
- [ ] `eas.json` production/preview 에 도메인 API URL + 카카오 prod env 채워짐
- [ ] 카카오 콘솔: `https://pillmatefriend.duckdns.org/api/v1/auth/kakao/callback` Redirect URI 등록 + Client Secret prod 반영
- [ ] 미존재 경로 404 매핑 (NoResourceFoundException → 500 PILL_999 버그 수정 여부)
- [ ] Android `app.json` `expo.android.allowBackup: false` 반영된 APK 배포 (2026-07-13 ADV 발견 — 콜드캐시 AsyncStorage 에 복약/처방 정보 평문 저장, Expo 기본값 true 면 Google 자동백업으로 기기 밖 유출 가능. `T-FE-COLD-CACHE-FIX-R2` 에서 코드 반영, **재빌드 확인 필수**)

## G4. 데이터/DB
- [ ] 시드 시퀀스 정합: 모든 테이블 `setval(seq, max(id))` 점검 스크립트 1회 (users_id_seq desync 사고 재발 방지)
- [ ] Flyway 전체 마이그레이션 클린 DB 리허설 (`validate` + 신규 DB up)
- [ ] dose_logs 파티션: 다음 달 파티션 존재 확인
- [ ] WeeklyReportScheduler `health_reports.care_group_id` NOT NULL 위반 픽스 확인 (2026-07-05 발생분)

## G5. 법/개인정보 (출시 시)
- [ ] docs/legal/ 초안 → placeholder 채움 + 전문가 검토
- [ ] 앱 내 동의 UI: 민감정보(§23)·국외이전(§28-8) **별도 체크박스** (간주 동의 불가)
- [ ] 개인정보처리방침 URL 호스팅 (스토어 심사 필수)

## G6. 검증/QA
- [ ] `/security-audit` skill 재실행 → P0=0 확인
- [ ] 2026-07-12 감사 발견 3건 픽스 확인: OCR 후보 IDOR(소유권 가드+resolverId), Swagger prod off, Actuator 외부 차단 — 트리오 PASS 필수
- [ ] QA Tier 1 미검증 잔여 태스크 0 (qa-risk-tiers.md)
- [ ] 핵심 여정 e2e: 로그인(카카오 실계정)→약봉투 등록→오늘 체크→그룹 공유→알림 — 프로드 환경에서 1회
- [ ] 블루그린 전환 리허설 (부하 중 드랍 0 — 로컬 250req 선례 재현)
- [ ] 롤백 리허설: `deploy.sh --rollback` 1회

## G7. 관측성
- [ ] Sentry DSN prod 연결 (BE/AI 서버) + 테스트 이벤트 1건 수신
- [ ] Grafana Cloud/Alloy 연결 + 핵심 대시보드 (요청수·에러율·LLM 비용)
- [ ] Slack 웹훅 알림 (deploy 성공/실패, 헬스체크 다운)

## 운영 규칙
- 게이트 실행 주체: CTO. Tier 1 항목(G1·G3·G4)은 트리오 검증 병행.
- 각 항목 통과 시 이 파일에 날짜 기록 후 커밋 (게이트 증적).
