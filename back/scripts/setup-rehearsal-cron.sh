#!/usr/bin/env bash
# 복구 리허설 cron 배선 — 서버에서 1회 실행.
# 매월 1일 05:00(KST, 일일 백업 이후) restore_rehearsal.sh 실행 → 임시 컨테이너에만 복원, 운영 DB 무관.
# 멱등: 이미 등록돼 있으면 중복 추가하지 않음.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REHEARSAL_SCRIPT="${SCRIPT_DIR}/restore_rehearsal.sh"
LOG_FILE="/var/log/pillmate-restore-rehearsal.log"
CRON_SCHEDULE="0 5 1 * *"
CRON_MARK="# pillmate-restore-rehearsal"
CRON_LINE="${CRON_SCHEDULE} bash ${REHEARSAL_SCRIPT} >> ${LOG_FILE} 2>&1 ${CRON_MARK}"

if [ ! -x "${REHEARSAL_SCRIPT}" ]; then
    echo "[cron] ERROR: 실행 가능한 리허설 스크립트 없음: ${REHEARSAL_SCRIPT}" >&2
    exit 1
fi

existing="$(crontab -l 2>/dev/null || true)"

if echo "${existing}" | grep -qF "${CRON_MARK}"; then
    echo "[cron] 이미 등록됨 — 중복 추가 생략 (${CRON_MARK})"
    exit 0
fi

printf '%s\n%s\n' "${existing}" "${CRON_LINE}" | grep -v '^$' | crontab -

echo "[cron] 등록 완료: 매월 1일 05:00 복구 리허설"
echo "[cron]   ${CRON_LINE}"
echo "[cron] 로그: ${LOG_FILE}"
