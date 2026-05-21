#!/usr/bin/env bash
set -euo pipefail

# AI 서버에서 LLM 응답을 그대로 반환하는 코드 검출
# 호출: ./check_medical_source.sh ai_server/app/api/chat.py

FILE="${1:-}"

if [[ -z "$FILE" || ! -f "$FILE" ]]; then
    exit 0
fi

# 검출 패턴:
# - return llm.invoke(...)
# - return await llm.acall(...)
# - return response.content  (검증 없이)
if grep -nE "return\s+(llm|client|chain|response)\.(invoke|acall|content|text)" "$FILE" > /dev/null; then
    cat >&2 <<EOF
⚠️  의료 안전 위반 의심: LLM 응답을 검증 없이 반환하는 코드

  파일: $FILE
EOF
    grep -nE "return\s+(llm|client|chain|response)\.(invoke|acall|content|text)" "$FILE" >&2
    echo "" >&2
    echo "필수 패턴: 응답을 medical-domain-validator로 검증 후 반환" >&2
    echo "참조: rules/common/medical-safety.md" >&2
    exit 1
fi

exit 0
