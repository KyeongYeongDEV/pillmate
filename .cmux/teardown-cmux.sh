#!/usr/bin/env bash
# teardown-cmux.sh — close the agent panes created by setup-cmux.sh.
# CTO pane is preserved.

set -euo pipefail
ORCHESTRA_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ENV_FILE="$ORCHESTRA_ROOT/.runtime/cmux.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "no .runtime/cmux.env — nothing to tear down" >&2
  exit 0
fi
# shellcheck disable=SC1090
source "$ENV_FILE"

for s in "$DEV_SURFACE" "$QA_CLAUDE_SURFACE" "$QA_GEMINI_SURFACE" "$RECON_SURFACE"; do
  [[ -n "$s" ]] || continue
  # Send Ctrl-C twice to interrupt the watcher loop, then close.
  cmux send-key-panel  --panel "$s" --workspace "$CMUX_WORKSPACE" C-c 2>/dev/null || true
  cmux send-key-panel  --panel "$s" --workspace "$CMUX_WORKSPACE" C-c 2>/dev/null || true
  cmux close-surface  --surface "$s" --workspace "$CMUX_WORKSPACE" 2>/dev/null || true
  echo "✓ closed $s"
done

rm -f "$ENV_FILE"
echo "✓ teardown complete (CTO pane preserved)"
