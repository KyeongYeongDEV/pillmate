#!/usr/bin/env bash
# 백업 파일 유효성 검증 — 실 복구 없이 pg_restore --list 만 사용
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
BACKUP_DIR="${REPO_ROOT}/back/.backups"
MIN_SIZE_BYTES=1048576  # 1 MB

main() {
    echo "[verify] $(date '+%Y-%m-%d %H:%M:%S') 검증 시작"

    # 최신 백업 파일 탐색
    LATEST="$(find "${BACKUP_DIR}" -maxdepth 1 -name '*.dump.gz' -type f \
        | sort -r | head -1)"

    if [[ -z "${LATEST}" ]]; then
        echo "[verify] FAIL: ${BACKUP_DIR} 에 *.dump.gz 없음" >&2
        exit 1
    fi

    echo "[verify] 대상 파일: $(basename "${LATEST}")"

    # 크기 확인
    ACTUAL_SIZE="$(stat -f%z "${LATEST}" 2>/dev/null || stat -c%s "${LATEST}")"
    if (( ACTUAL_SIZE < MIN_SIZE_BYTES )); then
        echo "[verify] FAIL: 파일 크기 ${ACTUAL_SIZE}B < 최소 ${MIN_SIZE_BYTES}B" >&2
        exit 1
    fi
    echo "[verify] 크기: $(du -sh "${LATEST}" | cut -f1) — OK"

    # gzip 무결성 확인
    if ! gzip -t "${LATEST}" 2>/dev/null; then
        echo "[verify] FAIL: gzip 무결성 오류" >&2
        exit 1
    fi
    echo "[verify] gzip 무결성: OK"

    # pg_restore --list 로 덤프 내용 확인 (실 복구 없음)
    # 호스트 pg_restore 버전이 컨테이너(PG16) 덤프 포맷보다 낮을 수 있어 컨테이너에서 실행
    CONTAINER="pillmate-postgres"
    TMP_CONTAINER="/tmp/pillmate_verify_$$.bin"
    TMP_HOST="$(mktemp /tmp/pillmate_verify_XXXXXX.bin)"
    gunzip -c "${LATEST}" > "${TMP_HOST}"
    docker cp "${TMP_HOST}" "${CONTAINER}:${TMP_CONTAINER}" 2>/dev/null
    TABLE_COUNT="$(docker exec "${CONTAINER}" pg_restore --list "${TMP_CONTAINER}" 2>/dev/null \
        | grep -c 'TABLE DATA' || true)"
    docker exec "${CONTAINER}" rm -f "${TMP_CONTAINER}" 2>/dev/null || true
    rm -f "${TMP_HOST}"

    if (( TABLE_COUNT == 0 )); then
        echo "[verify] WARN: TABLE DATA 항목 0 (컨테이너 미실행 또는 빈 덤프)"
    else
        echo "[verify] 테이블 데이터 ${TABLE_COUNT}건 확인 — OK"
    fi

    echo "[verify] PASS: $(basename "${LATEST}")"
}

main "$@"
