#!/usr/bin/env bash
# Developer panel watcher: polls dev/inbox, invokes `claude -p` to implement,
# writes outbox, then notifies both QA panels.

set -euo pipefail
ROLE="dev"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

INBOX="$MSG_DIR/dev/inbox"
OUTBOX="$MSG_DIR/dev/outbox"
SYSTEM_PROMPT="$(cat "$PROMPTS/dev.md")"

color dev "════════════════════════════════════════════"
color dev "  DEVELOPER panel — watching $INBOX"
color dev "════════════════════════════════════════════"

while :; do
  msg="$(wait_for_message "$INBOX")"
  task_id="$(basename "$msg" | cut -d- -f1)"
  payload="$(read_payload "$msg")"

  color dev ""
  color dev "[$(ts)] ▶ Task received: $task_id"
  color dev "─── spec ───"
  echo "$payload"
  color dev "────────────"

  out_payload="$OUTBOX/${task_id}.json"
  mkdir -p "$OUTBOX"

  full_prompt=$(cat <<EOF
$SYSTEM_PROMPT

---
TASK ID: $task_id
SPEC:
$payload

When done, write your summary JSON to: $out_payload
The orchestra workspace is: $WORKSPACE
EOF
)

  color dev "[$(ts)] ⚙  invoking claude -p (this may take a while)…"
  log_file="$LOG_DIR/dev-${task_id}.log"
  if claude -p --permission-mode acceptEdits --add-dir "$WORKSPACE" "$full_prompt" \
        2>&1 | tee "$log_file"; then
    color dev "[$(ts)] ✓ implementation finished"
  else
    color err "[$(ts)] ✗ claude exited non-zero — see $log_file"
  fi

  if [[ ! -f "$out_payload" ]]; then
    color err "[$(ts)] ⚠  dev did not write $out_payload — synthesizing failure"
    cat > "$out_payload" <<EOF
{"task_id":"$task_id","status":"failed","files_changed":[],"how_to_run":"","test_hints":"","summary":"Developer agent did not produce output. See $log_file."}
EOF
  fi

  send_message "qa-claude" "review_request" "$task_id" "$out_payload" >/dev/null
  send_message "qa-gemini" "review_request" "$task_id" "$out_payload" >/dev/null
  color dev "[$(ts)] → dispatched to QA-Claude + QA-Gemini"

  archive_message "$msg"
done
