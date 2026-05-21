#!/usr/bin/env bash
set -euo pipefail

# domain 클래스에 대응 테스트 파일이 있는지 확인
# 호출: ./check_tdd_pair.sh src/main/java/com/pillmate/prescription/domain/Prescription.java

FILE="${1:-}"

if [[ -z "$FILE" ]]; then
    echo "Usage: $0 <java-file>" >&2
    exit 1
fi

if [[ "$FILE" != *"/domain/"* ]]; then
    exit 0   # domain 레이어 아니면 통과
fi

# src/main/... → src/test/...
TEST_FILE="${FILE/src\/main\/java/src\/test\/java}"
TEST_FILE="${TEST_FILE%.java}Test.java"

if [[ ! -f "$TEST_FILE" ]]; then
    cat >&2 <<EOF
⚠️  TDD 위반: domain 클래스에 대응 테스트가 없습니다.

  파일:    $FILE
  필요:    $TEST_FILE

  RED 테스트를 먼저 작성하세요.
  스킬:    /pill-tdd <context> "동작 설명"
EOF
    exit 1
fi

exit 0
