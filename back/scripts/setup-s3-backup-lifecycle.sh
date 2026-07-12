#!/usr/bin/env bash
# S3 라이프사이클 — db-backups/ prefix 30일 자동 만료(오프사이트 백업 비용 절감). 멱등.
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
RULE_ID="pillmate-db-backups-expire-30d"
BACKUP_PREFIX="db-backups/"
EXPIRE_DAYS=30
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

# 1) 기존 라이프사이클 get — 없으면(NoSuchLifecycleConfiguration) 빈 규칙으로 시작
existing="$(aws_cli s3api get-bucket-lifecycle-configuration \
    --bucket "${S3_BUCKET_NAME}" 2>/dev/null || echo '{"Rules":[]}')"

# 2) 우리 규칙 정의 (db-backups/ prefix 30일 만료)
new_rule="$(jq -n \
    --arg id "${RULE_ID}" \
    --arg pfx "${BACKUP_PREFIX}" \
    --argjson days "${EXPIRE_DAYS}" \
    '{ID:$id, Filter:{Prefix:$pfx}, Status:"Enabled", Expiration:{Days:$days}}')"

# 3) 병합 — 동일 ID 규칙 제거 후 재추가(멱등). 나머지 기존 규칙은 그대로 보존.
merged="$(echo "${existing}" | jq \
    --arg id "${RULE_ID}" \
    --argjson rule "${new_rule}" \
    '{Rules: ((.Rules // [] | map(select(.ID != $id))) + [$rule])}')"

echo "=== 기존 규칙 ID (보존 확인 — 처방전 이미지 규칙이 남아있어야 함) ==="
echo "${existing}" | jq -r '.Rules[]?.ID // "(기존 규칙 없음)"'
echo
echo "=== 병합 후 적용될 라이프사이클 JSON ==="
echo "${merged}" | jq .

if [[ "${MODE}" == "--apply" ]]; then
    echo
    echo "[lifecycle] 적용 중 — put-bucket-lifecycle-configuration (전체 교체, 위 병합본으로)..."
    aws_cli s3api put-bucket-lifecycle-configuration \
        --bucket "${S3_BUCKET_NAME}" \
        --lifecycle-configuration "${merged}"
    echo "[lifecycle] 완료: ${RULE_ID} (db-backups/ 30일 만료). 기존 규칙 보존됨."
else
    echo
    echo "[lifecycle] --dry-run: 적용하지 않음. 실제 적용은 '--apply' (CTO/사용자 승인 후)."
fi
