#!/usr/bin/env bash
set -euo pipefail

# Java 파일의 import 문이 DDD 레이어 규칙을 위반하는지 확인
# 호출: ./check_layer_dependency.sh src/main/java/com/pillmate/prescription/domain/Prescription.java

FILE="${1:-}"

if [[ -z "$FILE" || ! -f "$FILE" ]]; then
    exit 0
fi

case "$FILE" in
    */domain/*)
        # domain은 application/presentation/infrastructure에 의존 금지
        if grep -E "^import com\.pillmate\.[a-z]+\.(application|presentation|infrastructure)\." "$FILE" > /dev/null; then
            echo "❌ DDD 위반: domain 레이어가 외부 레이어에 의존" >&2
            grep -nE "^import com\.pillmate\.[a-z]+\.(application|presentation|infrastructure)\." "$FILE" >&2
            exit 1
        fi
        ;;
    */presentation/*)
        if grep -E "^import com\.pillmate\.[a-z]+\.infrastructure\." "$FILE" > /dev/null; then
            echo "❌ DDD 위반: presentation이 infrastructure에 직접 의존" >&2
            grep -nE "^import com\.pillmate\.[a-z]+\.infrastructure\." "$FILE" >&2
            exit 1
        fi
        ;;
    */application/*)
        if grep -E "^import com\.pillmate\.[a-z]+\.infrastructure\.[a-z]+\." "$FILE" > /dev/null; then
            echo "❌ DDD 위반: application이 infrastructure 구현체에 의존 (Port만 허용)" >&2
            grep -nE "^import com\.pillmate\.[a-z]+\.infrastructure\.[a-z]+\." "$FILE" >&2
            exit 1
        fi
        ;;
esac

exit 0
