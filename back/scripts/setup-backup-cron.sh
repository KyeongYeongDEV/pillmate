#!/usr/bin/env bash
# DB 백업 cron 배선 — 서버에서 1회 실행.
# 매일 04:00(KST) backup_postgres.sh 실행 → back/.backups/ 에 pg_dump(read-only) 적재, 7일 보관.
# 멱등: 이미 등록돼 있으면 중복 추가하지 않음. db-safety: pg_dump 는 read-only, DELETE/DROP 없음.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKUP_SCRIPT="${SCRIPT_DIR}/backup_postgres.sh"
LOG_FILE="/var/log/pillmate-db-backup.log"
CRON_SCHEDULE="0 4 * * *"
CRON_MARK="# pillmate-db-backup"
# BACKUP_S3=true → 백업 스크립트가 .env.prod 를 로드해 S3 오프사이트 업로드까지 수행.
# 시크릿 값은 크론라인에 넣지 않는다(secret-safety) — 스크립트가 .env.prod 에서 읽음.
CRON_LINE="${CRON_SCHEDULE} BACKUP_S3=true bash ${BACKUP_SCRIPT} >> ${LOG_FILE} 2>&1 ${CRON_MARK}"

if [ ! -x "${BACKUP_SCRIPT}" ]; then
    echo "[cron] ERROR: 실행 가능한 백업 스크립트 없음: ${BACKUP_SCRIPT}" >&2
    exit 1
fi

existing="$(crontab -l 2>/dev/null || true)"

if echo "${existing}" | grep -qF "${CRON_MARK}"; then
    echo "[cron] 이미 등록됨 — 중복 추가 생략 (${CRON_MARK})"
    exit 0
fi

printf '%s\n%s\n' "${existing}" "${CRON_LINE}" | grep -v '^$' | crontab -

echo "[cron] 등록 완료: 매일 04:00 DB 백업"
echo "[cron]   ${CRON_LINE}"
echo "[cron] 로그: ${LOG_FILE}"
