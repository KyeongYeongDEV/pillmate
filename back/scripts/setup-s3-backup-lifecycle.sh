#!/usr/bin/env bash
# S3 버전관리 + 라이프사이클 — db-backups/ prefix 를 30일 후 Deep Archive 전환, 365일 보관 후 만료. 멱등.
# (2026-09-28) 기존 "30일 완전삭제"에서 변경 — 실수/공격으로 인한 삭제를 버전관리로 방지하고,
# 장기보관은 Deep Archive($0.00099/GB/월, 덤프가 작아 실질 비용 거의 0)로 비용 부담 없이 늘림.
#
# ⚠️ put-bucket-lifecycle-configuration 은 버킷의 라이프사이클을 "전체 교체" 한다.
#    따라서 반드시 기존 설정을 get 으로 읽어 병합해야 한다.
#    이 버킷은 처방전 이미지(30d→IA, 90d→Glacier, 1095d→만료)와 공용이므로
#    기존 규칙을 절대 덮어쓰면 안 된다(의료 데이터 보관 규정).
#
# 사용:
#   S3_BUCKET_NAME=<bucket> bash setup-s3-backup-lifecycle.sh            # --dry-run (기본, 적용 안 함)
#   S3_BUCKET_NAME=<bucket> bash setup-s3-backup-lifecycle.sh --apply    # 실제 적용 (CTO/사용자 승인 후)
set -euo pipefail

S3_BUCKET_NAME="${S3_BUCKET_NAME:-}"
OLD_RULE_ID="pillmate-db-backups-expire-30d"
RULE_ID="pillmate-db-backups-archive-and-expire"
BACKUP_PREFIX="db-backups/"
TRANSITION_DAYS=30
EXPIRE_DAYS=365
NONCURRENT_EXPIRE_DAYS=35
MODE="${1:---dry-run}"

[[ -z "${S3_BUCKET_NAME}" ]] && { echo "ERROR: S3_BUCKET_NAME 미설정" >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "ERROR: jq 필요" >&2; exit 1; }

# aws 실행 래퍼 — 로컬 aws cli 있으면 사용, 없으면 docker amazon/aws-cli.
# 크리덴셜은 env 로만 주입 (값 argv 노출 0 — secret-safety).
aws_cli() {
    if command -v aws >/dev/null 2>&1; then
        aws "$@"
    else
        docker run --rm \
            -e AWS_ACCESS_KEY_ID \
            -e AWS_SECRET_ACCESS_KEY \
            -e AWS_REGION \
            amazon/aws-cli:latest "$@"
    fi
}

# 1) 버킷 버전관리 활성화 — 실수/공격으로 인한 삭제 시 이전 버전으로 복구 가능(추가 스토리지 비용만).
#    put-bucket-versioning 은 멱등(이미 Enabled 면 재호출해도 무해).
current_versioning="$(aws_cli s3api get-bucket-versioning --bucket "${S3_BUCKET_NAME}" 2>/dev/null | jq -r '.Status // "Disabled"')"
echo "=== 버전관리 현재 상태: ${current_versioning} ==="

# 2) 기존 라이프사이클 get — 없으면(NoSuchLifecycleConfiguration) 빈 규칙으로 시작
existing="$(aws_cli s3api get-bucket-lifecycle-configuration \
    --bucket "${S3_BUCKET_NAME}" 2>/dev/null || echo '{"Rules":[]}')"

# 3) 우리 규칙 정의 — db-backups/ prefix: 30일 후 Deep Archive 전환, 365일 후 만료,
#    (버전관리 활성화 전제) 만료로 생긴 이전 버전은 35일 뒤 정리해 스토리지 무한증가 방지.
new_rule="$(jq -n \
    --arg id "${RULE_ID}" \
    --arg pfx "${BACKUP_PREFIX}" \
    --argjson transDays "${TRANSITION_DAYS}" \
    --argjson expireDays "${EXPIRE_DAYS}" \
    --argjson noncurrentDays "${NONCURRENT_EXPIRE_DAYS}" \
    '{
        ID: $id,
        Filter: {Prefix: $pfx},
        Status: "Enabled",
        Transitions: [{Days: $transDays, StorageClass: "DEEP_ARCHIVE"}],
        Expiration: {Days: $expireDays},
        NoncurrentVersionExpiration: {NoncurrentDays: $noncurrentDays}
    }')"

# 4) 병합 — 동일 ID(신규/구 규칙 모두) 제거 후 재추가(멱등). 나머지 기존 규칙은 그대로 보존.
merged="$(echo "${existing}" | jq \
    --arg id "${RULE_ID}" \
    --arg oldId "${OLD_RULE_ID}" \
    --argjson rule "${new_rule}" \
    '{Rules: ((.Rules // [] | map(select(.ID != $id and .ID != $oldId))) + [$rule])}')"

echo "=== 기존 규칙 ID (보존 확인 — 처방전 이미지 규칙이 남아있어야 함) ==="
echo "${existing}" | jq -r '.Rules[]?.ID // "(기존 규칙 없음)"'
echo
echo "=== 병합 후 적용될 라이프사이클 JSON ==="
echo "${merged}" | jq .

if [[ "${MODE}" == "--apply" ]]; then
    echo
    if [[ "${current_versioning}" != "Enabled" ]]; then
        echo "[versioning] 적용 중 — put-bucket-versioning (Enabled)..."
        aws_cli s3api put-bucket-versioning \
            --bucket "${S3_BUCKET_NAME}" \
            --versioning-configuration Status=Enabled
        echo "[versioning] 완료."
    else
        echo "[versioning] 이미 Enabled — 건너뜀."
    fi
    echo "[lifecycle] 적용 중 — put-bucket-lifecycle-configuration (전체 교체, 위 병합본으로)..."
    aws_cli s3api put-bucket-lifecycle-configuration \
        --bucket "${S3_BUCKET_NAME}" \
        --lifecycle-configuration "${merged}"
    echo "[lifecycle] 완료: ${RULE_ID} (db-backups/ 30일후 Deep Archive, 365일 만료). 기존 규칙 보존됨."
else
    echo
    echo "[lifecycle] --dry-run: 적용하지 않음. 실제 적용은 '--apply' (CTO/사용자 승인 후)."
fi
