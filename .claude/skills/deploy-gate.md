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
- [x] **prod 마스터 데이터 적재 확인**: drugs/drug_master/drug_alias/drug_interactions/drug_embeddings 건수 = 로컬 (2026-07-13 사고: drugs 0건 채로 배포되어 검색·OCR매칭·병용금기 전부 빈 결과 — 사용자 발견. pg_dump 이관으로 해소, 47,021/67,682/656,774 일치 검증)
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

## G8. 2026-09-20 배포감사 (4 감사관: 보안·정합성·동시성·운영준비도) — 총 P0 1·P1 13·P2 16

### 코드 픽스 완료 (TDD GREEN, 이 세션)
- [x] **AddPrescriptionSlot IDOR** — 슬롯추가 인가가드 누락(BOLA, 의료 write) → 형제 유스케이스 패턴대로 patientAccessGuard+careGroupGuard 추가. **Tier 1 → 커밋 전 adversarial 검증**
- [x] **SendActivityPraiseService** — @Transactional 안 FCM 발송 + 이중쓰기 + 비원자 멱등 → tx 제거·칭찬행 선커밋·DataIntegrityViolationException 멱등 catch
- [x] **ActivityFeedAppender** — 커밋 전 캐시 evict(15s stale) → TransactionSynchronization afterCommit 으로 미룸
- [x] **GenerateWeeklyReport** — 멱등성 부재(blue-green 자정 이중실행 시 리포트·푸시 2벌) → findByPatientId...WEEKLY 존재 시 skip
- [x] **JVM MaxRAMPercentage 75→65** — 2g mem_limit 대비 OOM-kill 여지 완화

### 코드 픽스 보류 (신중 처리 필요 — 아래 근거)
- [ ] **GenerateWeeklyReport LLM-out-of-tx** — @Transactional 안 LLM(최대 170s) 커넥션 점유. 분리는 tx경계 재설계(별도 빈 or TransactionTemplate + AFTER_COMMIT 이벤트 정합) 필요 → 백그라운드 스케줄러라 비-사용자경로, 별도 태스크로
- [ ] **dose_logs UNIQUE(schedule_id, scheduled_at) 백스톱** — check-then-act 원자성 없음. **로컬 DB에 이미 중복행 1건 존재 확인(schedule 45)** → 순진한 UNIQUE 인덱스는 마이그레이션 실패. ①기존 중복 정리(DELETE=db-safety P0, 사용자 명시 동의 필수) → ②파티션 유니크 추가(파티션키 scheduled_at 포함, 락 주의) 순서. **사용자 동의 없이 진행 금지**

### 운영/직접 항목 (코드 밖 — CTO 수행)
- [ ] **[P0] 백업 복원 리허설 + verify_backup.sh cron 배선** — 검증 안 된 백업 = 백업 없음 (db-safety)
- [ ] 관측성 CI 배선: Alloy prod compose + GRAFANA_CLOUD_* 시크릿 + deploy.sh Slack 성공/실패 훅
- [ ] 호스트 리소스(mem/disk/swap) 지표 재도입 + 디스크 80%·mem 알림
- [ ] 배포 트리거 [main] 승격 + 브랜치보호 + 전환 직후 스모크(health+로그인) 
- [ ] 블루그린 마이그레이션 규율: 파괴적 변경 2단계(expand→contract) 강제 + Flyway validate CI 게이트 (신규 enum값 forward-incompat 500 창 주의)
- [ ] P2 잔여 16건 (404→500 매핑, TTL<폴링, LLM 일일예산 알람, docker image prune -af cron 등) — 출시 후

## 운영 규칙
- 게이트 실행 주체: CTO. Tier 1 항목(G1·G3·G4)은 트리오 검증 병행.
- 각 항목 통과 시 이 파일에 날짜 기록 후 커밋 (게이트 증적).
