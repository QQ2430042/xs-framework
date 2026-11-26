#!/usr/bin/env bash
# Stop one or both deployment modes.
# Usage: stop.sh <monolith|user|tenant|all>
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

TARGET="${1:-all}"

stop_one() {
  local mode="$1"
  mode_config "$mode"
  if ! is_running "$PID_FILE"; then
    info "$mode is not running"
    return 0
  fi
  local pid
  pid="$(cat "$PID_FILE")"
  info "stopping $mode (pid $pid)"
  kill "$pid" 2>/dev/null || true
  local i
  for i in $(seq 1 30); do
    kill -0 "$pid" 2>/dev/null || break
    sleep 1
  done
  if kill -0 "$pid" 2>/dev/null; then
    kill -9 "$pid" 2>/dev/null || true
  fi
  rm -f "$PID_FILE"
  info "$mode stopped"
}

case "$TARGET" in
  monolith|user|tenant) stop_one "$TARGET" ;;
  all) stop_one monolith; stop_one user; stop_one tenant ;;
  *) fail "target must be monolith, user, tenant, or all" ;;
esac
