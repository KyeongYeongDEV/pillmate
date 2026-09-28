#!/usr/bin/env bash
# 복구 리허설 — "백업은 되는데 복구가 안 되는" 사고를 막기 위해 실제 pg_restore 를 월 1회 수행.
# (2026-09-28) verify_backup.sh 는 pg_restore --list 로 목록만 확인 — 이 스크립트는 한 걸음 더 나가
# 최신 덤프를 실제로 복원하고 핵심 테이블 row count 를 스팟체크한다.
#
# db-safety: 운영 DB 컨테이너(pillmate-postgres)는 절대 건드리지 않는다.
# 별도 임시 컨테이너에만 복원 후 스크립트 종료 시 반드시 제거(trap) — 실사용 데이터 변경 0.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKUP_DIR="${REPO_ROOT}/back/.backups"
REHEARSAL_CONTAINER="pillmate-restore-rehearsal"
REHEARSAL_DB="pillmate_rehearsal"
REHEARSAL_USER="rehearsal"
REHEARSAL_PASSWORD="rehearsal_local_only"
PG_IMAGE="pgvector/pgvector:pg16"  # 운영과 동일 이미지 — vanilla postgres 는 pgvector 확장 없어 drug_embeddings 복원 실패
# row count 가 0 이면 실패로 간주할 핵심 테이블 — 운영 데이터가 있다면 반드시 채워져 있어야 하는 것들.
SPOT_CHECK_TABLES=(users care_groups memberships prescribed_drugs dose_logs notifications)

SLACK_WEBHOOK_URL="${SLACK_WEBHOOK_URL:-}"
ENV_FILE="${ENV_FILE:-/opt/pillmate/back/.env.prod}"
if [[ -f "${ENV_FILE}" ]]; then
    set -a; . "${ENV_FILE}"; set +a
fi

notify_failure() {
    local reason="$1"
    [[ -z "${SLACK_WEBHOOK_URL}" ]] && return 0
    local payload
    payload="$(printf '{"text":"🚨 PillMate 복구 리허설 실패: %s (host=%s time=%s)"}' \
        "${reason}" "$(hostname)" "$(date '+%Y-%m-%d %H:%M:%S')")"
    curl -fsS -m 10 -X POST -H 'Content-Type: application/json' \
        -d "${payload}" "${SLACK_WEBHOOK_URL}" >/dev/null 2>&1 || true
}

cleanup() {
    docker rm -f "${REHEARSAL_CONTAINER}" >/dev/null 2>&1 || true
}
trap cleanup EXIT

fail() {
    echo "[rehearsal] FAIL: $1" >&2
    notify_failure "$1"
    exit 1
}

latest_backup() {
    find "${BACKUP_DIR}" -maxdepth 1 -name '*.dump.gz' -type f | sort -r | head -1
}

start_rehearsal_container() {
    cleanup
    echo "[rehearsal] 임시 컨테이너 기동 (${PG_IMAGE}, 운영 컨테이너와 완전 분리)"
    docker run -d --name "${REHEARSAL_CONTAINER}" \
        -e POSTGRES_USER="${REHEARSAL_USER}" \
        -e POSTGRES_PASSWORD="${REHEARSAL_PASSWORD}" \
        -e POSTGRES_DB="${REHEARSAL_DB}" \
        "${PG_IMAGE}" >/dev/null

    for _ in $(seq 1 30); do
        if docker exec "${REHEARSAL_CONTAINER}" pg_isready -U "${REHEARSAL_USER}" >/dev/null 2>&1; then
            return 0
        fi
        sleep 1
    done
    fail "임시 컨테이너가 30초 내 준비되지 않음"
}

restore_dump() {
    local dump_gz="$1"
    local tmp_container="/tmp/rehearsal_restore.bin"
    local tmp_host
    tmp_host="$(mktemp /tmp/pillmate_rehearsal_XXXXXX.bin)"

    gunzip -c "${dump_gz}" > "${tmp_host}"
    docker cp "${tmp_host}" "${REHEARSAL_CONTAINER}:${tmp_container}"
    rm -f "${tmp_host}"

    echo "[rehearsal] pg_restore 실행 중 (임시 컨테이너 전용, 운영 무관)"
    if ! docker exec "${REHEARSAL_CONTAINER}" pg_restore \
        -U "${REHEARSAL_USER}" -d "${REHEARSAL_DB}" \
        --no-owner --no-privileges \
        "${tmp_container}" 2>&1 | tee /tmp/pillmate_rehearsal_restore.log; then
        # pg_restore 는 확장/권한 관련 non-fatal 경고에도 0이 아닌 exit 를 낼 수 있어
        # 실제 실패 여부는 이어지는 row count 체크로 판단한다.
        echo "[rehearsal] WARN: pg_restore 가 경고와 함께 종료됨 — row count 로 실질 성공 여부 판단"
    fi
}

# 사람이 읽는 진단 로그는 stderr 로, 최종 실패 개수만 stdout 으로 반환한다.
# (2026-09-28 수정) 둘 다 stdout 으로 섞으면 호출부의 `failure_count="$(spot_check_row_counts)"` 가
# "count" 대신 로그 전체를 캡처해 이후 [[ -gt 0 ]] 비교가 깨지고 실패가 PASS 로 잘못 보고된다 — 실제 발견된 버그.
spot_check_row_counts() {
    local failures=0
    echo "[rehearsal] 핵심 테이블 row count 스팟체크" >&2
    for table in "${SPOT_CHECK_TABLES[@]}"; do
        local count
        count="$(docker exec "${REHEARSAL_CONTAINER}" psql -U "${REHEARSAL_USER}" -d "${REHEARSAL_DB}" \
            -tAc "SELECT COUNT(*) FROM ${table};" 2>/dev/null || echo "ERROR")"
        if [[ "${count}" == "ERROR" ]]; then
            echo "[rehearsal]   ${table}: 조회 실패 (테이블 없음?)" >&2
            failures=$((failures + 1))
        else
            echo "[rehearsal]   ${table}: ${count} rows" >&2
            if [[ "${count}" -eq 0 ]]; then
                failures=$((failures + 1))
            fi
        fi
    done
    echo "${failures}"
}

main() {
    local dump
    dump="$(latest_backup)"
    [[ -z "${dump}" ]] && fail "복원할 백업 파일 없음 (${BACKUP_DIR})"
    echo "[rehearsal] $(date '+%Y-%m-%d %H:%M:%S') 시작 — 대상: $(basename "${dump}")"

    start_rehearsal_container
    restore_dump "${dump}"

    local failure_count
    failure_count="$(spot_check_row_counts)"
    if [[ "${failure_count}" -gt 0 ]]; then
        fail "핵심 테이블 ${failure_count}개가 비어있거나 조회 실패 — 백업 손상 의심"
    fi

    echo "[rehearsal] PASS: $(basename "${dump}") 복구 검증 완료 (핵심 테이블 전부 데이터 존재)"
}

main "$@"
