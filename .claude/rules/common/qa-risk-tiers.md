# QA Risk Tiers — 리스크 기반 2단계 검증 (2026-07-07 개정)

> 기존 "매 T-task 트리오(Reviewer+QA-Claude+Adversarial) 의무" 정책(2026-05-25 Option A)이
> 다작업 구간에서 사실상 미이행됨 → 현실화. **중요한 건 절대 안 놓치고, 가벼운 건 빠르게.**

## Tier 1 — 트리오 의무 (Reviewer + QA-Claude + ⚔️Adversarial)

다음 중 **하나라도** 해당하면 트리오 검증 없이 커밋/DONE 확정 금지:

| 트리거 | 예 |
|--------|-----|
| **의료 안전** | 복용 체크 상태전이, 병용금기, OCR 매칭 임계치, 용량 표시 |
| **보안/인증** | 로그인·JWT·권한 가드, 그룹 격리 쿼리, admin 엔드포인트 |
| **데이터 변경** | Flyway 마이그레이션, 시퀀스/시드 조작, soft-delete·익명화 로직 |
| **결제/과금** | LLM 비용 가드, rate limit, (향후) 구독·결제 |
| **배포/인프라** | deploy.yml, compose, Caddy, 시크릿 처리, blue-green 스크립트 |
| **개인정보** | PII 검출·마스킹, 처방전 이미지 접근, 동의·법적 플로우 |

- Adversarial 은 "어떻게 깨지나/우회되나" 시나리오 + 마지막 줄 `ADV_<TASK>_PASS|FAIL`.
- Tier 1 은 **커밋 게이트와 무관하게 태스크 단위로 즉시** 실행.

## Tier 2 — CTO 직접 검증 (트리오 생략 가능)

- UI 스타일·문구·레이아웃, 간단한 config, placeholder, 폴링 주기 등
- 단 **verification-evidence.md 의 실측 증거는 동일하게 필수** (스크린샷/픽셀/curl)
- CTO 가 독립 재측정으로 확인 후 확정

## 운영 규칙

1. CTO 는 디스패치 시점에 spec 헤더에 `QA-Tier: 1|2` 명시. 애매하면 **Tier 1**.
2. Tier 2 로 분류했더라도 작업 중 Tier 1 트리거 영역을 건드리게 되면 즉시 승격.
3. **커밋 게이트**: 커밋 묶음에 Tier 1 변경이 하나라도 포함되면 게이트에서 트리오 리포트 존재 확인.
4. 트리오 FAIL → 수정 → 해당 항목만 재검증 (전체 재실행 아님).

## 실행 방식 (2026-09-02 단일세션 모델)
- 트리오(Reviewer + QA-Claude + ⚔️Adversarial)는 상시 패널이 아니라 **필요 시 Agent 툴로 서브에이전트 호출**한다. Tier 1 변경이면 메인 세션이 리뷰/QA/adversarial 서브에이전트를 띄워 병렬 검증 후, 결과를 교차확인하고 커밋한다.
- Adversarial 은 "어떻게 깨지나/우회되나" 시나리오 + 마지막 줄 `ADV_<TASK>_PASS|FAIL`.

## 참조
- `.claude/rules/common/verification-evidence.md` — Tier 무관 공통 증거 의무
