#!/usr/bin/env bash
# deploy.sh — PillMate 무중단 배포 (Caddy blue-green + ai-server 롤링)
# 사용:
#   bash scripts/deploy.sh            # 배포
#   bash scripts/deploy.sh --rollback # 직전 컬러로 즉시 롤백
#
# 동작:
#   1. 현재 활성 컬러(blue/green) 판별
#   2. 반대 컬러 이미지 빌드 + 기동
#   3. /actuator/health 200 대기 (타임아웃 시 안전 폐기)
#   4. Caddyfile 업스트림 교체 + caddy reload (끊김 0)
#   5. 기존 컬러 stop (롤백 대비 컨테이너 유지)
#   6. ai-server 롤링 재기동
#
# 환경:
#   DEPLOY_ROOT (optional) — 서버 내 고정 배포 경로. 기본 /opt/pillmate.
#   CI에서 실행 시 workspace → DEPLOY_ROOT/back 으로 rsync 후 compose 실행.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE_BACK="$(cd "$SCRIPT_DIR/.." && pwd)"
DEPLOY_ROOT="${DEPLOY_ROOT:-/opt/pillmate}"
BACK_DIR="$DEPLOY_ROOT/back"

COMPOSE="docker compose \
  -f $BACK_DIR/docker-compose.yml \
  -f $BACK_DIR/docker-compose.prod.yml \
  --env-file $BACK_DIR/.env.prod"

HEALTH_TIMEOUT=120   # seconds: healthcheck start_period(60) + margin
CADDYFILE="$BACK_DIR/Caddyfile"
CADDY_CONTAINER="pillmate-caddy"

# ── Helpers ────────────────────────────────────────────────────────────

log() { echo "[deploy] $*"; }

# CI workspace → 서버 고정 경로 동기화
# .env.prod / secrets / Caddyfile(현재 업스트림 유지)는 제외
sync_workspace() {
    log "Syncing workspace → $BACK_DIR"
    mkdir -p "$BACK_DIR"
    rsync -a --delete \
        --exclude='.git' \
        --exclude='__pycache__' \
        --exclude='*.pyc' \
        --exclude='.env.prod' \
        --exclude='secrets/' \
        --exclude='Caddyfile' \
        "$WORKSPACE_BACK/" "$BACK_DIR/"
}

# 현재 실행 중인 컬러 반환 (blue|green|none)
active_color() {
    if docker ps --format '{{.Names}}' | grep -q '^pillmate-app-blue$'; then
        echo "blue"
    elif docker ps --format '{{.Names}}' | grep -q '^pillmate-app-green$'; then
        echo "green"
    else
        echo "none"
    fi
}

# Caddyfile 업스트림 교체 (동일 inode write → bind mount 반영)
# ⚠️ rate_limit 블록은 반드시 유지 — 누락 시 계층① 엣지 방어(per-IP)가 사라진다.
# (T-BE-GLOBAL-RATE-LIMIT: mholt/caddy-ratelimit, caddy/Dockerfile 커스텀 이미지 필요)
write_caddyfile() {
    local color="$1"
    printf '{
    email {env.ACME_EMAIL}
    order rate_limit before basicauth
}

{env.DOMAIN} {
    rate_limit {
        zone per_ip {
            key {remote_host}
            events 40
            window 1s
        }
        zone auth_ip {
            match {
                path /api/v1/auth/*
            }
            key {remote_host}
            events 5
            window 1s
        }
    }
    reverse_proxy pillmate-app-%s:8080
}\n' "$color" > "$CADDYFILE"
    log "Caddyfile → app-$color (rate_limit 유지)"
}

# Caddy 무중단 reload
reload_caddy() {
    docker exec "$CADDY_CONTAINER" caddy reload --config /etc/caddy/Caddyfile
    log "Caddy reloaded."
}

# Docker healthcheck 상태 대기
wait_healthy() {
    local container="$1"
    local deadline=$((SECONDS + HEALTH_TIMEOUT))
    log "Waiting for $container (timeout=${HEALTH_TIMEOUT}s)..."
    while [[ $SECONDS -lt $deadline ]]; do
        local status
        status=$(docker inspect --format='{{.State.Health.Status}}' "$container" 2>/dev/null || echo "missing")
        if [[ "$status" == "healthy" ]]; then
            log "✓ $container healthy"
            return 0
        fi
        log "  status=$status — waiting 10s..."
        sleep 10
    done
    log "✗ Timeout: $container did not become healthy in ${HEALTH_TIMEOUT}s"
    return 1
}

# ── 첫 배포 ────────────────────────────────────────────────────────────
first_deploy() {
    log "First deploy — bringing up all services (blue)..."
    $COMPOSE build app-blue ai-server
    write_caddyfile "blue"
    # depends_on 이 pg/redis/ai-server 를 자동 기동
    $COMPOSE up -d app-blue caddy
    if ! wait_healthy "pillmate-app-blue"; then
        log "First deploy health check failed."
        $COMPOSE stop app-blue
        exit 1
    fi
    log "✓ First deploy complete. Active=blue"
}

# ── 롤백 ───────────────────────────────────────────────────────────────
rollback() {
    local current old
    current=$(active_color)
    if [[ "$current" == "none" ]]; then
        log "ERROR: No active container found."
        exit 1
    fi
    old=$([[ "$current" == "blue" ]] && echo "green" || echo "blue")
    if ! docker ps -a --format '{{.Names}}' | grep -q "^pillmate-app-${old}$"; then
        log "ERROR: pillmate-app-$old container not found (cannot rollback)."
        exit 1
    fi
    log "Rolling back $current → $old..."
    $COMPOSE start "app-$old" 2>/dev/null || $COMPOSE up -d "app-$old"
    if ! wait_healthy "pillmate-app-$old"; then
        log "ERROR: Rollback target $old is not healthy."
        exit 1
    fi
    write_caddyfile "$old"
    reload_caddy
    $COMPOSE stop "app-$current"
    log "✓ Rollback complete. Active=$old"
}

# ── 일반 배포 (blue-green 전환) ────────────────────────────────────────
deploy() {
    local current new_color
    current=$(active_color)

    if [[ "$current" == "none" ]]; then
        first_deploy
        return
    fi

    new_color=$([[ "$current" == "blue" ]] && echo "green" || echo "blue")
    log "Active=$current → Deploying to $new_color"

    # 1. 새 컬러 빌드 + 기동
    log "Building app-$new_color..."
    $COMPOSE build "app-$new_color"
    log "Starting app-$new_color..."
    $COMPOSE up -d "app-$new_color"

    # 2. 헬스체크 — 실패 시 안전 폐기, 기존 유지
    if ! wait_healthy "pillmate-app-$new_color"; then
        log "Health check failed — aborting. Stopping app-$new_color. $current stays active."
        $COMPOSE stop "app-$new_color"
        exit 1
    fi

    # 3. Caddy 업스트림 전환 (무중단)
    write_caddyfile "$new_color"
    reload_caddy

    # 4. 기존 컬러 stop (컨테이너 유지 → 즉시 롤백 가능)
    log "Stopping app-$current (kept for rollback: bash scripts/deploy.sh --rollback)"
    $COMPOSE stop "app-$current"

    # 5. ai-server 롤링 재기동 (수초 블립 허용 — 내부용)
    log "Rolling ai-server..."
    $COMPOSE build ai-server
    $COMPOSE up -d --no-deps ai-server

    log "✓ Deployment complete. Active=$new_color"
}

# ── Entrypoint ─────────────────────────────────────────────────────────

# CI workspace 에서 실행 시 서버 고정 경로로 동기화
if [[ "$WORKSPACE_BACK" != "$BACK_DIR" ]]; then
    sync_workspace
fi

cd "$BACK_DIR"

case "${1:-deploy}" in
    --rollback) rollback ;;
    deploy|"")  deploy   ;;
    *)
        echo "Usage: deploy.sh [deploy|--rollback]"
        exit 1
        ;;
esac
