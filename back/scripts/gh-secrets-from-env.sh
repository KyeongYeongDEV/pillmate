#!/usr/bin/env bash
# gh-secrets-from-env.sh — 로컬 back/.env 를 읽어 GitHub repo Secrets/Variables 로 등록.
#
# 사용:
#   bash scripts/gh-secrets-from-env.sh --dry-run   # 실제 등록 없이 계획만 출력 (값은 절대 출력 안 함)
#   bash scripts/gh-secrets-from-env.sh             # 실제 등록
#   bash scripts/gh-secrets-from-env.sh --generate-jwt-secret   # PILLMATE_JWT_SECRET 을 openssl 로 새로 생성해 등록(로컬 값 재사용 안 함)
#
# 안전 규칙:
#   - 값은 gh secret/variable set NAME < <(printf '%s' "$value") 방식으로만 전달 (명령줄 인자 X, 화면 echo X).
#   - PILLMATE_JWT_SECRET / POSTGRES_PASSWORD 는 로컬 값을 절대 그대로 올리지 않음 (prod 전용 강한 값 필요).
#   - PILLMATE_DEV_FALLBACK / SPRING_PROFILES_ACTIVE 는 등록 대상에서 제외 (workflow 하드코딩 안전상수).
#   - KAKAO_REDIRECT_URI 는 등록하지 않음 — deploy.yml 이 PROD_HOST Variable 로 https URL 을 조립.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$SCRIPT_DIR/../.env"

DRY_RUN=false
GENERATE_JWT=false
for arg in "$@"; do
    case "$arg" in
        --dry-run) DRY_RUN=true ;;
        --generate-jwt-secret) GENERATE_JWT=true ;;
        *) echo "알 수 없는 옵션: $arg (지원: --dry-run, --generate-jwt-secret)" >&2; exit 1 ;;
    esac
done

if [[ ! -f "$ENV_FILE" ]]; then
    echo "ERROR: $ENV_FILE 없음 — 로컬 개발 .env 파일이 필요합니다." >&2
    exit 1
fi

# ── 로컬 .env 파싱 (key=value, 값은 변수에만 보관 — 로그/화면 출력 금지) ──
get_local() {
    local key="$1"
    grep -E "^${key}=" "$ENV_FILE" 2>/dev/null | tail -1 | cut -d'=' -f2- || true
}

has_local() {
    local value
    value="$(get_local "$1")"
    [[ -n "$value" ]]
}

# ── gh 등록 헬퍼 — 값은 항상 프로세스 치환으로 stdin 전달(명령줄 인자·echo 금지) ──
push_secret() {
    local name="$1" value="$2"
    if [[ "$DRY_RUN" == true ]]; then
        echo "  [SECRET]   gh secret set $name   (dry-run — 실제 등록 안 함)"
        return
    fi
    gh secret set "$name" < <(printf '%s' "$value")
    echo "  ✓ secret $name 등록 완료"
}

push_variable() {
    local name="$1" value="$2"
    if [[ "$DRY_RUN" == true ]]; then
        echo "  [VARIABLE] gh variable set $name = '$value'   (dry-run — 실제 등록 안 함, 값은 비민감정보라 표시)"
        return
    fi
    gh variable set "$name" --body "$value"
    echo "  ✓ variable $name = '$value' 등록 완료"
}

echo "═══════════════════════════════════════════════════════════════"
echo " PillMate — GitHub Secrets/Variables 등록 계획"
[[ "$DRY_RUN" == true ]] && echo " 모드: --dry-run (실제 등록 없음, 어떤 시크릿 값도 출력하지 않음)"
echo "═══════════════════════════════════════════════════════════════"

# ── 1. Secrets — 로컬 .env 값을 그대로 매핑 (키명이 다른 경우 명시적 매핑) ──
echo ""
echo "── Secrets (값은 로컬 .env 에서 읽어 그대로 전달, 여기 출력 안 함) ──"

# 연관배열(declare -A) 미사용 — macOS 기본 /bin/bash(3.2)는 associative array 미지원.
# 병렬 인덱스 배열로 로컬 키명 ↔ GitHub Secret 이름 매핑 (bash 3.2 호환).
SECRET_LOCAL_KEYS=(KAKAO_OAUTH_KEY KAKAO_CLIENT_SECRET GEMINI_API_KEY OpenAI_API_KEY AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY SENTRY_DSN SENTRY_DSN_AI SLACK_WEBHOOK_URL)
SECRET_REMOTE_KEYS=(KAKAO_REST_API_KEY KAKAO_CLIENT_SECRET GEMINI_API_KEY OPENAI_API_KEY AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY SENTRY_DSN SENTRY_DSN_AI SLACK_WEBHOOK_URL)
# 매핑 메모: KAKAO_OAUTH_KEY(로컬)→KAKAO_REST_API_KEY(prod), OpenAI_API_KEY(로컬 mixed-case)→OPENAI_API_KEY(prod 대문자)

for i in "${!SECRET_LOCAL_KEYS[@]}"; do
    local_key="${SECRET_LOCAL_KEYS[$i]}"
    remote_key="${SECRET_REMOTE_KEYS[$i]}"
    if has_local "$local_key"; then
        push_secret "$remote_key" "$(get_local "$local_key")"
        if [[ "$local_key" != "$remote_key" ]]; then
            echo "             (로컬 키명 $local_key → GitHub Secret 이름 $remote_key)"
        fi
    else
        echo "  [SKIP]     $remote_key — 로컬 .env 에 $local_key 없음(optional 이면 무시, 필수면 수동 등록)"
    fi
done

# ── JWT_SECRET / POSTGRES_PASSWORD — 로컬 값 재사용 절대 금지 ──
echo ""
echo "── Secrets (로컬 값 재사용 금지 — prod 전용 강한 값 필요) ──"

if [[ "$GENERATE_JWT" == true ]]; then
    NEW_JWT="$(openssl rand -base64 48)"
    push_secret "PILLMATE_JWT_SECRET" "$NEW_JWT"
    echo "             (openssl rand -base64 48 로 신규 생성 — 로컬 .env 값 미사용)"
    unset NEW_JWT
else
    echo "  [MANUAL]   PILLMATE_JWT_SECRET — 로컬 placeholder 재사용 금지(ProductionSecurityValidator 가 prod 부팅 거부)."
    echo "             강한 값 생성 예시: openssl rand -base64 48"
    echo "             생성 후 등록: gh secret set PILLMATE_JWT_SECRET < <(openssl rand -base64 48)"
    echo "             또는 이 스크립트를 --generate-jwt-secret 옵션과 함께 재실행."
fi

echo "  [MANUAL]   POSTGRES_PASSWORD — 로컬 dev 비밀번호 재사용 금지. prod 전용 강한 값 별도 생성 후 수동 등록:"
echo "             gh secret set POSTGRES_PASSWORD < <(openssl rand -base64 32)"

echo "  [MANUAL]   FIREBASE_CREDENTIALS_JSON_B64 — FCM 사용 시에만 필요. 로컬 .env 로 도출 불가(서비스계정 JSON 파일 별도)."
echo "             등록 예시: gh secret set FIREBASE_CREDENTIALS_JSON_B64 < <(base64 -i firebase-service-account.json)"

# ── 2. Variables — 비시크릿, 로컬 값 있으면 사용, 없으면 기본값 ──
echo ""
echo "── Variables (비시크릿 — 값 자체는 민감하지 않아 표시) ──"

var_value() {
    local key="$1" default="$2"
    local v
    v="$(get_local "$key")"
    [[ -n "$v" ]] && echo "$v" || echo "$default"
}

push_variable "AWS_REGION" "$(var_value AWS_REGION ap-northeast-2)"
push_variable "S3_BUCKET_NAME" "$(var_value S3_BUCKET_NAME pillmate-prescriptions)"
push_variable "PILLMATE_NOTIFICATION_PROVIDER" "$(var_value PILLMATE_NOTIFICATION_PROVIDER log)"

echo ""
echo "── Variables (로컬 .env 로 도출 불가 — 서버/도메인 정보, 수동 등록 필요) ──"
echo "  [MANUAL]   PROD_HOST        — 예: gh variable set PROD_HOST --body pillmatefriend.duckdns.org"
echo "  [MANUAL]   ACME_EMAIL       — 예: gh variable set ACME_EMAIL --body you@example.com"
echo "  [MANUAL]   DEPLOY_ROOT      — (선택) 기본 /opt/pillmate. 다르면: gh variable set DEPLOY_ROOT --body /custom/path"

# ── 3. 제외 (workflow 하드코딩 안전상수 / 미사용 / prod 무관) ──
echo ""
echo "── 제외 (등록 대상 아님) ──"
echo "  - SPRING_PROFILES_ACTIVE, PILLMATE_DEV_FALLBACK : 안전상수, deploy.yml 하드코딩"
echo "  - KAKAO_REDIRECT_URI                            : deploy.yml 이 PROD_HOST 로 https URL 조립"
echo "  - MFDS_API_KEY                                  : compose/app 어디서도 미사용(일회성 스크립트 전용)"
echo "  - POSTGRES_DB/HOST/PORT/USER, REDIS_HOST/PORT   : prod compose 하드코딩(내부 서비스명)"
echo "  - JWT_SECRET(로컬 legacy alt)                    : PILLMATE_JWT_SECRET 만 실제 사용됨"

echo ""
echo "═══════════════════════════════════════════════════════════════"
[[ "$DRY_RUN" == true ]] && echo " dry-run 완료 — 실제 등록 없음." || echo " 등록 완료."
echo "═══════════════════════════════════════════════════════════════"
