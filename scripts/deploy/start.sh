#!/usr/bin/env bash
# Start one deployment mode in the background.
# Usage: start.sh <monolith|user|tenant> [--profile pg]
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

MODE="${1:-}"
PROFILE="${2:-}"
[ -n "$MODE" ] || fail "usage: start.sh <monolith|user|tenant> [--profile pg]"

mode_config "$MODE"
JAVA_BIN="$(resolve_java)"

if is_running "$PID_FILE"; then
  fail "$MODE already running with pid $(cat "$PID_FILE")"
fi

INFO_ARGS=(--server.port="$PORT")
if [ "$PROFILE" = "--profile" ] || [ "$PROFILE" = "pg" ]; then
  INFO_ARGS+=(--spring.profiles.active=pg)
fi

info "starting $MODE on port $PORT (log: $LOG_FILE)"
nohup "$JAVA_BIN" -jar "$JAR" "${INFO_ARGS[@]}" > "$LOG_FILE" 2>&1 &
echo $! > "$PID_FILE"
wait_ready "$PORT" "$LOG_FILE" "$MODE"
info "$MODE ready, pid $(cat "$PID_FILE"), endpoint http://127.0.0.1:$PORT"
