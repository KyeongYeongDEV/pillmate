# Context Hygiene — compact vs clear 강제 (2026-07-07, 사용자 정의 반영)

> 기존 정책이 강제 장치 없이 방치되어 패널이 798k/653k 까지 부풀어 degraded/멈춤(2026-07-07).
> 단, **clear 는 맥락 손실 위험이 있어 신중해야 한다** — compact 와 용도를 명확히 구분한다(사용자 명시).

## 🔑 compact 와 clear 는 용도가 다르다 (혼동 금지)

| 도구 | 언제 | 효과 | 맥락 |
|------|------|------|------|
| **/compact** | **같은 작업이 길어질 때** (토큰 부풀음, 아직 진행 중) | 대화를 요약·압축하고 **계속 진행** | **보존** (작업 흐름 유지) |
| **/clear** | **한 작업이 끝나고 다른 종류로 전환**할 때 (스크린→도메인, FE→BE, 기능A→기능B 등) | 컨텍스트 완전 리셋, 새 출발 | **버림** (이전 작업 맥락 불필요할 때만) |

**핵심 원칙**: 진행 중 작업의 맥락은 잊으면 안 된다. 길어지면 → **compact**. 끝나고 성격이 바뀌면 → **clear**.
토큰이 많다고 무조건 clear 하지 말 것 — 작업이 안 끝났으면 **compact** 가 정답.

## 트리거 규칙

### /compact (진행 중 압축)
- 같은 작업 진행 중 토큰 **≥ 120k** → compact 로 부풀음 억제하며 계속.
- 여러 라운드 반복되는 긴 디버깅·구현은 중간중간 compact (맥락 유지한 채 가볍게).
- **busy 여도 turn 사이 idle 순간에** compact 가능. 우선 시도.

### /clear (작업 전환 시)
- 조건 **모두** 충족해야:
  1. 현재 태스크 **완결**(DONE 회수 + 결과가 파일에 영속: 워킹트리/outbox/specs).
  2. 다음이 **다른 종류의 작업**(다른 화면·도메인·기능·레이어).
  3. 패널 **idle** (busy 면 불가 — 대기).
- 즉 clear 는 "작업 경계 + 성격 전환"에서만. 같은 기능 이어서 하면 clear 아님(compact).
- clear 전 체크: "이 패널의 이전 대화 맥락을 이후 작업에서 다시 참조할 일이 없는가?" → 예 여야 clear.

## degraded 복구 (예외)
- 토큰이 수백k(≥400k)까지 방치되어 **compact/clear 조차 안 먹는** degraded 상태:
  - 작업 미완결이면 → **먼저 현재 상태를 spec/outbox 로 명시 저장**(맥락 파일화) 후 `bin/restart-panel`.
  - 재기동은 `claude --continue`(세션 이어받기) — 워킹트리·specs·outbox 로 맥락 복원.
- 이 지경까지 오기 전에 compact 를 자주 했어야 함(예방이 최선).

## CTO 강제 절차 (dispatch 루프 내장)
1. 긴 작업이 라운드 반복되면 **중간에 compact** (맥락 유지, 부풀음 방지). 800k까지 방치 금지.
2. 패널이 DONE 보고 + **다른 종류 작업으로 넘어갈 때** → 그 시점에 /clear.
3. 다패널 구간: `./.cmux/bin/panel-health` 로 토큰·busy 스캔. 진행 중 고토큰 = compact 대상, 완결+전환 = clear 대상으로 **구분** 판단.
4. **의심되면 clear 대신 compact** (맥락 손실 < 토큰 절감. 안전 우선).

## 도구
- `./.cmux/bin/panel-health` — 전 패널 토큰·busy 표 (idle 시 토큰 정확, busy 면 0=측정불가)
- `./.cmux/bin/panel-health --compact-over 150` — 임계 초과 패널 **compact**(안전, 맥락 보존)
- `./.cmux/bin/clear-all` — 전 패널 clear (전부 idle + 작업 전환 시에만)
- `./.cmux/bin/restart-panel <role>` — degraded 복구(맥락 파일화 후)

## 참조
- 메모리 `feedback_context_compaction`
- `.claude/rules/common/verification-evidence.md` — clear 전 작업물 파일 영속 확인
