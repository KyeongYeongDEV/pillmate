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

# S3 오프사이트 — 로컬 기본 off (dev 덤프를 prod 버킷에 올리는 실수 방지). VM 에선 BACKUP_S3=true.
BACKUP_S3="${BACKUP_S3:-false}"
S3_BUCKET_NAME="${S3_BUCKET_NAME:-}"
S3_BACKUP_PREFIX="db-backups"

# VM cron 은 최소 env 로 실행 → BACKUP_S3=true 일 때만 .env.prod 에서 S3/AWS 설정 로드(있을 때).
# 시크릿은 프로세스 내부에서만 사용, 로그 출력 없음(secret-safety). 크론라인에 시크릿 미포함.
ENV_FILE="${ENV_FILE:-/opt/pillmate/back/.env.prod}"
if [[ "${BACKUP_S3}" == "true" && -f "${ENV_FILE}" ]]; then
    set -a; . "${ENV_FILE}"; set +a
fi

# 로컬 덤프를 S3 오프사이트로 업로드 (SSE-S3). 크리덴셜은 env 로만 주입 — argv 노출 0.
upload_to_s3() {
    local file="$1"
    local dest="s3://${S3_BUCKET_NAME}/${S3_BACKUP_PREFIX}/$(date +%Y/%m)/$(basename "${file}")"

    if [[ -z "${S3_BUCKET_NAME}" ]]; then
        echo "[backup] ERROR: BACKUP_S3=true 이나 S3_BUCKET_NAME 미설정" >&2
        return 1
    fi

    echo "[backup] S3 업로드 → ${dest} (SSE AES256)"
    if command -v aws >/dev/null 2>&1; then
        aws s3 cp "${file}" "${dest}" --sse AES256
    else
        docker run --rm \
            -e AWS_ACCESS_KEY_ID \
            -e AWS_SECRET_ACCESS_KEY \
            -e AWS_REGION \
            -v "$(dirname "${file}")":/backup:ro \
            amazon/aws-cli:latest \
            s3 cp "/backup/$(basename "${file}")" "${dest}" --sse AES256
    fi
}

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

    # S3 오프사이트 (로컬 백업은 이미 안전 — 업로드 실패해도 유지, fail-soft)
    if [[ "${BACKUP_S3}" != "true" ]]; then
        echo "[backup] S3 업로드 스킵 (BACKUP_S3=${BACKUP_S3})"
        return 0
    fi
    if ! upload_to_s3 "${DUMP_PATH}"; then
        echo "[backup] ERROR: S3 업로드 실패 — 로컬 백업(${DUMP_FILENAME})은 유지됨" >&2
        exit 1
    fi
    echo "[backup] S3 오프사이트 완료: ${DUMP_FILENAME}"
}

main "$@"
