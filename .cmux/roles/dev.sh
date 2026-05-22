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

  color dev "[$(ts)] ⚙  invoking claude -p (stream-json, --bare)…"
  log_file="$LOG_DIR/dev-${task_id}.log"
  raw_log="$LOG_DIR/dev-${task_id}.stream.jsonl"
  # --bare: hooks/auto-memory/CLAUDE.md auto-discovery 비활성화 (CMUX 환경 hang 방지)
  # stream-json: 도구 호출 단위로 진행 가시화. jq로 핵심 이벤트만 사람용 로그로.
  if claude -p --bare --dangerously-skip-permissions \
        --output-format stream-json --verbose --include-partial-messages \
        --add-dir "$WORKSPACE" "$full_prompt" \
        2> "$log_file.err" \
      | tee "$raw_log" \
      | jq -r --unbuffered '
          select(.type=="system" and .subtype=="init") | "[init] model=\(.model) cwd=\(.cwd)",
          select(.type=="assistant") | "[asst] " + (.message.content[0].text // (.message.content[0].name // "tool") | tostring | .[0:200]),
          select(.type=="user" and .message.content[0].tool_use_id != null) | "[tool-result] " + ((.message.content[0].content // "") | tostring | .[0:200]),
          select(.type=="result") | "[result] subtype=\(.subtype) duration_ms=\(.duration_ms) cost=\(.total_cost_usd // 0)"
        ' 2>&1 | tee "$log_file"; then
    color dev "[$(ts)] ✓ implementation finished"
  else
    color err "[$(ts)] ✗ claude exited non-zero — see $log_file (stderr in $log_file.err)"
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
