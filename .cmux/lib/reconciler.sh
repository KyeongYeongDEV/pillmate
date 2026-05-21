#!/usr/bin/env bash
# Reconciler daemon: waits for both QA verdicts on the same task_id,
# merges them, and posts a unified report to messages/cto/inbox.

set -euo pipefail
ROLE="reconciler"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$SCRIPT_DIR/common.sh"

PENDING="$MSG_DIR/reconcile/pending"
DONE="$MSG_DIR/reconcile/done"
QA_C_OUT="$MSG_DIR/qa-claude/outbox"
QA_G_OUT="$MSG_DIR/qa-gemini/outbox"
mkdir -p "$PENDING" "$DONE"

color qa "════════════════════════════════════════════"
color qa "  RECONCILER — watching $PENDING"
color qa "════════════════════════════════════════════"

extract() {
  # extract <file> <key>  — naive grep-based JSON value extractor (string or scalar)
  local file="$1" key="$2"
  grep -o "\"$key\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" "$file" 2>/dev/null \
    | head -n1 | sed 's/.*: *"\(.*\)"$/\1/' || true
}

while :; do
  # Find any task_id that has BOTH .qa-claude and .qa-gemini tokens.
  task_id=""
  for c in "$PENDING"/*.qa-claude; do
    [[ -e "$c" ]] || continue
    base="$(basename "$c" .qa-claude)"
    if [[ -e "$PENDING/${base}.qa-gemini" ]]; then
      task_id="$base"
      break
    fi
  done

  if [[ -z "$task_id" ]]; then
    sleep "$POLL_INTERVAL"
    continue
  fi

  c_file="$QA_C_OUT/${task_id}.json"
  g_file="$QA_G_OUT/${task_id}.json"
  c_verdict="$(extract "$c_file" verdict)"
  g_verdict="$(extract "$g_file" verdict)"
  c_summary="$(extract "$c_file" summary)"
  g_summary="$(extract "$g_file" summary)"

  agreement="agree"
  if [[ "$c_verdict" != "$g_verdict" ]]; then agreement="disagree"; fi

  consensus="fail"
  if [[ "$c_verdict" == "pass" && "$g_verdict" == "pass" ]]; then
    consensus="pass"
  elif [[ "$c_verdict" == "fail" || "$g_verdict" == "fail" ]]; then
    consensus="fail"
  else
    consensus="partial"
  fi

  report="$LOG_DIR/report-${task_id}.json"
  cat > "$report" <<EOF
{
  "task_id": "$task_id",
  "consensus": "$consensus",
  "agreement": "$agreement",
  "qa_claude": $(cat "$c_file"),
  "qa_gemini": $(cat "$g_file"),
  "summary_claude": "$c_summary",
  "summary_gemini": "$g_summary"
}
EOF

  color qa ""
  color qa "[$(ts)] ✓ reconciled $task_id  (claude=$c_verdict, gemini=$g_verdict, $agreement → $consensus)"

  send_message "cto" "qa_report" "$task_id" "$report" >/dev/null

  # Persistent-mode: push a one-line notification into the CTO pane.
  CMUX_ENV="$ORCHESTRA_ROOT/.runtime/cmux.env"
  if [[ -f "$CMUX_ENV" ]]; then
    # shellcheck disable=SC1090
    source "$CMUX_ENV"
    if [[ -n "${CTO_SURFACE:-}" && -n "${CMUX_WORKSPACE:-}" ]] && command -v cmux >/dev/null; then
      note="[REPORT $task_id] consensus=$consensus agreement=$agreement (claude=$c_verdict, gemini=$g_verdict). Full report: $report"
      cmux send-panel --panel "$CTO_SURFACE" --workspace "$CMUX_WORKSPACE" -- "$note"$'\n' 2>/dev/null || true
    fi
  fi

  mv "$PENDING/${task_id}.qa-claude" "$DONE/" 2>/dev/null || true
  mv "$PENDING/${task_id}.qa-gemini" "$DONE/" 2>/dev/null || true
done
