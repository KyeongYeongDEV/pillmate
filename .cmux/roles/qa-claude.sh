#!/usr/bin/env bash
# QA-Claude panel watcher: independent functional QA using Claude.

set -euo pipefail
ROLE="qa-claude"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/../lib/common.sh"

INBOX="$MSG_DIR/qa-claude/inbox"
OUTBOX="$MSG_DIR/qa-claude/outbox"
PENDING="$MSG_DIR/reconcile/pending"
SYSTEM_PROMPT="$(cat "$PROMPTS/qa-claude.md")"

color qa "════════════════════════════════════════════"
color qa "  QA-CLAUDE panel — watching $INBOX"
color qa "════════════════════════════════════════════"

while :; do
  msg="$(wait_for_message "$INBOX")"
  task_id="$(basename "$msg" | cut -d- -f1)"
  dev_summary="$(read_payload "$msg")"

  color qa ""
  color qa "[$(ts)] ▶ Review request: $task_id"

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

  log_file="$LOG_DIR/qa-claude-${task_id}.log"
  color qa "[$(ts)] ⚙  invoking claude -p (QA)…"
  if claude -p --permission-mode acceptEdits --add-dir "$WORKSPACE" "$full_prompt" \
        2>&1 | tee "$log_file"; then
    color qa "[$(ts)] ✓ QA-Claude finished"
  else
    color err "[$(ts)] ✗ claude exited non-zero — see $log_file"
  fi

  if [[ ! -f "$out_payload" ]]; then
    color err "[$(ts)] ⚠  no verdict produced — synthesizing 'fail'"
    cat > "$out_payload" <<EOF
{"task_id":"$task_id","verdict":"fail","confidence":0.0,"tested":[],"issues":[{"severity":"critical","where":"qa-claude","what":"agent failed to produce a verdict"}],"summary":"QA-Claude agent did not produce output."}
EOF
  fi

  # Notify reconciler
  touch "$PENDING/${task_id}.qa-claude"
  color qa "[$(ts)] → reconciler notified"

  archive_message "$msg"
done
