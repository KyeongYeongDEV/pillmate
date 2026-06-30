#!/usr/bin/env bash
set -euo pipefail

# Mac LAN IP 자동 감지 (en0 → en1 fallback)
LAN_IP="$(ipconfig getifaddr en0 2>/dev/null || true)"
[[ -z "$LAN_IP" ]] && LAN_IP="$(ipconfig getifaddr en1 2>/dev/null || true)"

ENV_FILE=".env.local"
NEW_URL="EXPO_PUBLIC_API_BASE_URL=http://${LAN_IP}:8080/api/v1"

if [[ -n "$LAN_IP" ]]; then
  echo "[auto-lan-ip] detected LAN IP: $LAN_IP"
  # 활성 EXPO_PUBLIC_API_BASE_URL 라인만 교체 (다른 키/주석 보존). 없으면 append.
  if grep -q "^EXPO_PUBLIC_API_BASE_URL=" "$ENV_FILE" 2>/dev/null; then
    sed -i.bak "s|^EXPO_PUBLIC_API_BASE_URL=.*|$NEW_URL|" "$ENV_FILE"
    rm -f "$ENV_FILE.bak"
  else
    echo "$NEW_URL" >> "$ENV_FILE"
  fi
  echo "[auto-lan-ip] .env.local updated → $NEW_URL"
else
  echo "[auto-lan-ip] no LAN IP (offline?) — .env.local 변경 안 함, Platform.OS 분기 사용"
fi
