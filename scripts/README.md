---
name: scripts
description: PillMate CLI 도구 모음
---

# PillMate Scripts

자동화 훅과 일상 운영에 사용되는 스크립트입니다.

## 스크립트 목록

| 스크립트 | 용도 | 호출 |
|----------|------|------|
| `check_tdd_pair.sh` | 도메인 파일과 테스트 페어 확인 | PostToolUse 훅 |
| `check_layer_dependency.sh` | DDD 레이어 의존 검증 (정적) | PostToolUse 훅 |
| `check_medical_source.sh` | LLM 응답 출처 강제 패턴 | PostToolUse 훅 |
| `post_session_summary.sh` | 세션 종료 요약 | Stop 훅 |
| `eval_rag.py` | RAG 평가 (RAGAS) | 수동 또는 cron |

## 실행

모든 스크립트는 `scripts/` 디렉터리에서 실행 가능.
훅에서 자동 호출되거나 수동 실행 가능.

## 작성 규칙

- bash 스크립트는 `#!/usr/bin/env bash` + `set -euo pipefail`
- python 스크립트는 type hint 사용
- 인자 검증 후 실행 (방어적)
