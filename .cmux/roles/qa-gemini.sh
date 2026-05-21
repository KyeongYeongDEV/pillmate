#!/usr/bin/env bash
# QA-Gemini panel watcher: independent functional QA using Gemini CLI.

set -euo pipefail
ROLE="qa-gemini"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

INBOX="$MSG_DIR/qa-gemini/inbox"
OUTBOX="$MSG_DIR/qa-gemini/outbox"
PENDING="$MSG_DIR/reconcile/pending"
SYSTEM_PROMPT="$(cat "$PROMPTS/qa-gemini.md")"

color gemini "════════════════════════════════════════════"
color gemini "  QA-GEMINI panel — watching $INBOX"
color gemini "════════════════════════════════════════════"

while :; do
  msg="$(wait_for_message "$INBOX")"
  task_id="$(basename "$msg" | cut -d- -f1)"
  dev_summary="$(read_payload "$msg")"

  color gemini ""
  color gemini "[$(ts)] ▶ Review request: $task_id"

  out_payload="$OUTBOX/${task_id}.json"
  mkdir -p "$OUTBOX" "$PENDING"

  full_prompt=$(cat <<EOF
$SYSTEM_PROMPT

---
TASK ID: $task_id
DEV SUMMARY (JSON):
$dev_summary

The implementation lives at: $WORKSPACE
Run, read, and test. Then write your verdict JSON to: $out_payload
EOF
)

  log_file="$LOG_DIR/qa-gemini-${task_id}.log"
  color gemini "[$(ts)] ⚙  invoking gemini -p (QA)…"
  # gemini CLI: -y auto-approves tool calls, -p enters prompt mode.
  if (cd "$WORKSPACE" && gemini -y -p "$full_prompt") 2>&1 | tee "$log_file"; then
    color gemini "[$(ts)] ✓ QA-Gemini finished"
  else
    color err "[$(ts)] ✗ gemini exited non-zero — see $log_file"
  fi

  if [[ ! -f "$out_payload" ]]; then
    color err "[$(ts)] ⚠  no verdict produced — attempting to extract JSON from log"
    # Try to recover a JSON block from the log as a fallback.
    awk '/^\{/,/^\}/' "$log_file" | head -c 8000 > "$out_payload" || true
    if [[ ! -s "$out_payload" ]]; then
      cat > "$out_payload" <<EOF
{"task_id":"$task_id","verdict":"fail","confidence":0.0,"tested":[],"issues":[{"severity":"critical","where":"qa-gemini","what":"agent failed to produce a verdict"}],"summary":"QA-Gemini agent did not produce output."}
EOF
    fi
  fi

  touch "$PENDING/${task_id}.qa-gemini"
  color gemini "[$(ts)] → reconciler notified"

  archive_message "$msg"
done
