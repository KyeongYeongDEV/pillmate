#!/usr/bin/env bash
set -euo pipefail

# 세션 종료 시 요약 출력

echo ""
echo "─────────────────────────────────────"
echo "🧪 PillMate 세션 종료 체크"
echo "─────────────────────────────────────"

# 미커밋 변경 확인
if [[ -d .git ]]; then
    CHANGED=$(git status --porcelain 2>/dev/null | wc -l | tr -d ' ')
    echo "📝 미커밋 변경: $CHANGED 개"
fi

# TDD 페어 누락 확인
if [[ -d src/main/java ]]; then
    MISSING=0
    while IFS= read -r f; do
        TEST="${f/src\/main\/java/src\/test\/java}"
        TEST="${TEST%.java}Test.java"
        if [[ ! -f "$TEST" ]]; then
            MISSING=$((MISSING+1))
        fi
    done < <(find src/main/java -path "*/domain/*.java" 2>/dev/null || true)
    echo "🧪 도메인 테스트 누락: $MISSING 개"
fi

# 비용 알림 (간이)
TODAY=$(date +%Y-%m-%d)
echo "💰 오늘($TODAY) 비용 감사: /pill-cost 로 확인"
echo "─────────────────────────────────────"
