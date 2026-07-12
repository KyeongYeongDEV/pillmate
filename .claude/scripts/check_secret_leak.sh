#!/usr/bin/env bash
# check_secret_leak.sh <file> — Write/Edit 된 파일에 실키 패턴이 들어갔는지 검사 (secret-safety.md)
# .env* 자체는 대상 외 (원래 시크릿 보관처). 그 외 파일에 키 패턴 발견 시 경고.
set -uo pipefail
FILE="${1:-}"
[[ -z "$FILE" || ! -f "$FILE" ]] && exit 0

base="$(basename "$FILE")"
case "$base" in
  .env|.env.*|*.env) exit 0 ;;               # 시크릿 보관처 자체는 스킵
esac
case "$FILE" in
  */node_modules/*|*/build/*|*/.git/*) exit 0 ;;
esac

# 실키 시그니처: AWS AKIA, OpenAI sk-, Google AQ./AIza, JWT-비밀 후보(base64 40+), private key 블록
PATTERN='AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{35}|AQ\.[A-Za-z0-9_-]{20,}|-----BEGIN (RSA |EC )?PRIVATE KEY-----'
hits="$(grep -nE "$PATTERN" "$FILE" 2>/dev/null | head -3 || true)"

if [[ -n "$hits" ]]; then
  echo "🚨 [secret-safety] 실키 패턴 의심 — $FILE"
  echo "$hits" | sed -E 's/(AKIA[0-9A-Z]{4}|sk-[A-Za-z0-9]{4}|AIza[0-9A-Za-z]{4}|AQ\.[A-Za-z0-9]{4})[A-Za-z0-9_.-]*/\1***MASKED***/g'
  echo "→ placeholder 로 교체하거나, 의도된 시크릿 파일이면 .gitignore 확인. 룰: .claude/rules/common/secret-safety.md"
fi
exit 0
