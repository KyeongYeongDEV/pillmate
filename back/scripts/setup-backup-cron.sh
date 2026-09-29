#!/usr/bin/env bash
# DB 백업 cron 배선 — 서버에서 1회 실행.
# 6시간마다(00/06/12/18시 KST) backup_postgres.sh 실행 → back/.backups/ 에 pg_dump(read-only) 적재, 7일 보관.
# (2026-09-28) 일 1회(RPO 24h)에서 6시간 간격(RPO 6h)으로 단축 — pg_dump 는 read-only라 빈도 올려도 부담 적음.
# 멱등: 마커 라인이 있으면 "내용까지 동일할 때만" 스킵 — 스케줄 등 내용이 바뀌었으면 교체한다.
# (2026-09-28 수정) 예전엔 마커 텍스트 존재만 보고 무조건 skip 해, 이미 등록된 서버에서
# 04:00 1일1회 → 6시간마다로 스케줄을 바꿔도 재실행 시 반영이 안 되는 버그가 있었다.
# db-safety: pg_dump 는 read-only, DELETE/DROP 없음.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKUP_SCRIPT="${SCRIPT_DIR}/backup_postgres.sh"
LOG_FILE="/var/log/pillmate-db-backup.log"
CRON_SCHEDULE="0 */6 * * *"
CRON_MARK="# pillmate-db-backup"
# BACKUP_S3=true → 백업 스크립트가 .env.prod 를 로드해 S3 오프사이트 업로드까지 수행.
# 시크릿 값은 크론라인에 넣지 않는다(secret-safety) — 스크립트가 .env.prod 에서 읽음.
CRON_LINE="${CRON_SCHEDULE} BACKUP_S3=true bash ${BACKUP_SCRIPT} >> ${LOG_FILE} 2>&1 ${CRON_MARK}"

if [ ! -x "${BACKUP_SCRIPT}" ]; then
    echo "[cron] ERROR: 실행 가능한 백업 스크립트 없음: ${BACKUP_SCRIPT}" >&2
    exit 1
fi

existing="$(crontab -l 2>/dev/null || true)"

if echo "${existing}" | grep -qF "${CRON_LINE}"; then
    echo "[cron] 동일 내용 이미 등록됨 — 변경 없음"
    exit 0
fi

# 마커가 있는 기존 라인(구 스케줄 등)은 제거 후 최신 라인으로 재등록.
filtered="$(echo "${existing}" | grep -vF "${CRON_MARK}" || true)"
printf '%s\n%s\n' "${filtered}" "${CRON_LINE}" | grep -v '^$' | crontab -

echo "[cron] 등록/갱신 완료: 6시간마다 DB 백업 (RPO 6h)"
echo "[cron]   ${CRON_LINE}"
echo "[cron] 로그: ${LOG_FILE}"
