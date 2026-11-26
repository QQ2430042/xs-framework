#!/usr/bin/env bash
# Show status of both deployment modes.
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

for mode in monolith user tenant; do
  ARTIFACT="" PORT="" JAR="" PID_FILE="" LOG_FILE=""
  mode_config "$mode" 2>/dev/null || { printf '%-10s no boot jar\n' "$mode"; continue; }
  if is_running "$PID_FILE"; then
    printf '%-10s RUNNING  pid=%s  port=%s  endpoint=http://127.0.0.1:%s  log=%s\n' \
      "$mode" "$(cat "$PID_FILE")" "$PORT" "$PORT" "$LOG_FILE"
  else
    printf '%-10s STOPPED  port=%s  log=%s\n' "$mode" "$PORT" "$LOG_FILE"
  fi
done
