#!/usr/bin/env bash
# Shared helpers for the team-orchestra agents.
# Sourced by every role script. Do not run directly.

set -euo pipefail

ORCHESTRA_ROOT="${ORCHESTRA_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
MSG_DIR="$ORCHESTRA_ROOT/messages"
LOG_DIR="$ORCHESTRA_ROOT/logs"
# PillMate: 실제 코드는 레포 루트에 있다. WORKSPACE는 .cmux의 부모(repo root).
WORKSPACE="${WORKSPACE:-$(cd "$ORCHESTRA_ROOT/.." && pwd)}"
PROMPTS="$ORCHESTRA_ROOT/prompts"
POLL_INTERVAL="${POLL_INTERVAL:-2}"

mkdir -p "$LOG_DIR"

ts()   { date '+%Y-%m-%d %H:%M:%S'; }
uid()  { date +%s%N | cut -c1-16; }

color() {
  local c="$1"; shift
  case "$c" in
    cto)   printf '\033[1;36m%s\033[0m\n' "$*";;
    dev)   printf '\033[1;33m%s\033[0m\n' "$*";;
    qa)    printf '\033[1;35m%s\033[0m\n' "$*";;
    gemini)printf '\033[1;32m%s\033[0m\n' "$*";;
    err)   printf '\033[1;31m%s\033[0m\n' "$*";;
    *)     printf '%s\n' "$*";;
  esac
}

# send_message <to_role> <type> <task_id> <payload_file>
# Atomically drops a message into <to_role>/inbox/.
send_message() {
  local to="$1" type="$2" task_id="$3" payload_file="$4"
  local inbox="$MSG_DIR/$to/inbox"
  mkdir -p "$inbox"
  local id; id="$(uid)"
  local tmp="$inbox/.tmp-$id.json"
  local final="$inbox/${task_id}-${id}.json"
  cat > "$tmp" <<EOF
{
  "id": "$id",
  "task_id": "$task_id",
  "type": "$type",
  "from": "${ROLE:-unknown}",
  "to": "$to",
  "ts": "$(ts)",
  "payload_file": "$payload_file"
}
EOF
  mv "$tmp" "$final"
  echo "$final"
}

# wait_for_message <inbox_dir>
# Blocks until a non-hidden .json file appears, prints its path, then exits.
wait_for_message() {
  local inbox="$1"
  while :; do
    local first
    first="$(ls -1tr "$inbox"/*.json 2>/dev/null | head -n 1 || true)"
    if [[ -n "$first" ]]; then
      echo "$first"
      return 0
    fi
    sleep "$POLL_INTERVAL"
  done
}

# archive_message <message_path>
archive_message() {
  local msg="$1"
  local arch="$LOG_DIR/processed"
  mkdir -p "$arch"
  mv "$msg" "$arch/" 2>/dev/null || rm -f "$msg"
}

read_payload() {
  # Reads the .payload_file from a message JSON; falls back to inline payload.
  local msg="$1"
  local pf
  pf="$(grep -o '"payload_file": *"[^"]*"' "$msg" | sed 's/.*"\([^"]*\)"$/\1/')"
  if [[ -n "$pf" && -f "$pf" ]]; then
    cat "$pf"
  else
    cat "$msg"
  fi
}
