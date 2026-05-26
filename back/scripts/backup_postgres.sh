#!/usr/bin/env bash
# pg_dump 백업 스크립트 — Phase 1 단일 서버용
# 의료 데이터 포함 → chmod 600 강제, git 적재 금지
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKUP_DIR="${REPO_ROOT}/back/.backups"
TIMESTAMP="$(date +%Y-%m-%d-%H%M)"
DUMP_FILENAME="postgres-${TIMESTAMP}.dump.gz"
DUMP_PATH="${BACKUP_DIR}/${DUMP_FILENAME}"
CONTAINER="pillmate-postgres"
DB_USER="pillmate"
DB_NAME="pillmate"
TMP_DUMP="/tmp/pillmate_dump_${TIMESTAMP}.bin"
RETENTION_DAYS=7

main() {
    mkdir -p "${BACKUP_DIR}"

    echo "[backup] $(date '+%Y-%m-%d %H:%M:%S') 시작"

    if ! docker inspect "${CONTAINER}" >/dev/null 2>&1; then
        echo "[backup] ERROR: 컨테이너 '${CONTAINER}' 없음 — docker compose up 확인" >&2
        exit 1
    fi

    # pg_dump (Custom 포맷) — read-only SELECT 기반
    docker exec "${CONTAINER}" pg_dump \
        -U "${DB_USER}" \
        -d "${DB_NAME}" \
        --format=custom \
        --compress=9 \
        -f "${TMP_DUMP}"

    docker cp "${CONTAINER}:${TMP_DUMP}" "${DUMP_PATH%.gz}"

    # 의료 데이터 보호 — 소유자만 읽기/쓰기
    chmod 600 "${DUMP_PATH%.gz}"

    gzip -9 "${DUMP_PATH%.gz}"
    chmod 600 "${DUMP_PATH}"

    # 컨테이너 내 임시 파일 삭제 (운영 DB 데이터 아님)
    docker exec "${CONTAINER}" rm -f "${TMP_DUMP}"

    SIZE="$(du -sh "${DUMP_PATH}" | cut -f1)"
    echo "[backup] 완료: ${DUMP_FILENAME} (${SIZE})"

    # 7일 이상 된 백업 파일 삭제 (OS 파일, DB 데이터 아님)
    find "${BACKUP_DIR}" -mtime +"${RETENTION_DAYS}" -name '*.dump.gz' -delete
    echo "[backup] 보관 정책: ${RETENTION_DAYS}일 초과 파일 정리 완료"
}

main "$@"
