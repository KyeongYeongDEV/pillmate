---
name: security-audit
description: 유료 SaaS 출시 전 보안 종합 점검 워크플로우 (13개 관점). QA 트리오로 감사 후 출시 게이트 판정.
---

# Security Audit — 출시 전 보안 종합 점검

> 유료 서비스 공개 전 위험 요소를 13개 관점에서 점검한다.
> QA 트리오(Reviewer·QA-Claude·Adversarial)로 각 렌즈 감사 후, 출시 게이트로 판정.

## 언제 실행

- **유료/공개 출시 직전** (필수)
- 인증·권한·결제·개인정보 관련 대규모 변경 후
- 정기 (분기 1회)

## 실행 방법

```
1. CTO: 사전 recon — 아래 13항목 중 구현/미구현 파악 (grep)
2. spec 작성 → QA 트리오 병렬 디스패치 (say reviewer/qa-claude/adv)
   - Reviewer: 구현 코드 정확성 (1,2,3,5,7,9,10)
   - QA-Claude: 전 13항목 출시 준비도 체크리스트
   - Adversarial: 공격 시나리오 (3,7,8,1,5 — "어떻게 깨지나")
3. outbox 취합 → 13항목별 ✅/⚠️/🔴 + 출시 게이트 판정
```

## 13개 점검 관점

| # | 관점 | 핵심 체크 |
|---|------|-----------|
| 1 | **인증/로그인** | OAuth 실구현, JWT 발급·검증·TTL, dev-fallback prod 차단, 토큰 위조/재사용/만료 |
| 2 | **사용자 권한** | 그룹 권한 가드, 본인 리소스만, 권한 상승 경로 |
| 3 | **다른 사용자 데이터 접근(IDOR)** | 클라이언트 헤더(X-User-Id) 불신, JWT sub만 신뢰, 모든 query에 소유자/그룹 필터, presigned URL 소유권 |
| 4 | **결제/구독/환불** | PG 연동 보안, webhook 서명검증, 환불 권한, 멱등성, 이중결제 |
| 5 | **개인정보 저장·삭제** | 민감데이터 암호화(SSE-S3), 객체키에 식별자 없음, 로그 PII 마스킹, **회원탈퇴 시 삭제 경로**, 보관기간 |
| 6 | **관리자 페이지** | 별도 인증/RBAC, 접근통제, 감사로그 |
| 7 | **API key·환경변수 노출** | .env gitignore, git history 유출, 클라이언트 번들 시크릿(EXPO_PUBLIC_* 남용), example 파일 실키, 로그 |
| 8 | **사용량 제한·비용 폭탄** | 전역 per-user rate limit, 비용성 endpoint(LLM/OCR) 일일한도, 엣지 per-IP, 캐시 우회 공격 |
| 9 | **에러 로그·장애 대응** | 스택트레이스/PII 응답 노출 금지, 외부 장애 graceful, Redis/DB 장애 fail-open/closed 적정성 |
| 10 | **배포 환경·DB 백업** | 무중단 배포, **DB 백업(pg_dump)**, 마이그레이션 expand-contract, 시크릿 관리(Secrets Manager) |
| 11 | **이메일 발송** | 스푸핑/SPF·DKIM, 스팸, 레이트, 링크 검증 |
| 12 | **약관·개인정보처리방침·환불정책** | 법적 필수 문서(개인정보보호법·의료정보), 동의 흐름 |
| 13 | **론칭 전 필수 테스트 시나리오** | 각 렌즈에서 "출시 전 반드시 통과할 시나리오" 목록 |

## 심각도·상태 표기

- **🔴 위험(P0/P1)** — 출시 전 반드시 수정
- **⚠️ 출시 전 필요** — 미구현/미비, 출시 시점에 반드시 갖춰야 함
- **✅ 안전** — 확인됨

## PillMate 프로젝트 컨텍스트 (감사 시 참조)

### 이미 안전 확인된 패턴 (재확인만)
- IDOR: `UserContextInterceptor` 가 Bearer JWT 시 X-User-Id 무시하고 JWT sub 사용 (fail-closed). dev-fallback flag off일 때만 헤더 신뢰.
- 비용 방어: 전역 per-user rate limit(`GlobalRateLimitInterceptor`) + OCR 일일한도(`RateLimiterPort`) + Caddy per-IP.
- 시크릿: `.env` gitignore, git history 유출 없음.
- 처방전 이미지: private presigned(민감), 약 이미지: 공개 캐시 대상.
- 무중단 배포: graceful shutdown + stop_grace_period + Caddy blue-green.

### 알려진 출시 전 필수(게이트) — [[project_deploy_security_checklist]] 메모리
- **DB 백업(pg_dump) 부재 — critical** (의료 데이터, 복구 불가)
- JWT 시크릿 prod 교체 (현재 개발용 고정값)
- `PILLMATE_DEV_FALLBACK=false` prod 강제 (하드게이트)
- 회원탈퇴/개인정보 삭제 경로 확인
- 약관·개인정보처리방침·환불정책 문서 (법적)

### 미구현 (출시 시 도입 시 재감사)
- 결제/구독/환불 (수익화 보류 — [[feedback_monetization_deferred]])
- 별도 관리자 페이지 (care group role만 존재)
- 이메일 발송 (현재 FCM push만)

## 출시 게이트 판정

```
🔴 P0/P1 잔존 → 출시 불가 (수정 후 재감사)
⚠️ 출시 전 필요 항목 → 각각 담당 배정 + 완료 확인
✅ 전부 통과 → 출시 승인
```

## 참조

- `.claude/rules/common/medical-safety.md`, `db-safety.md` — 의료·DB P0 규칙
- `.claude/rules/common/cost-aware.md` — 비용/rate limit
- 이전 감사: `.cmux/messages/*/outbox/T-AUDIT-*.json`
- 메모리: `project_deploy_security_checklist`, `project_deploy_image_cdn`
