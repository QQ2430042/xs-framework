#!/usr/bin/env bash
# Gate G07: start the packaged monolith and verify committed OpenAPI equals runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

COMMITTED="$ROOT/docs/api/openapi.yaml"
WORK_DIR="$ROOT/.tools/openapi-drift"
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

command -v curl >/dev/null 2>&1 || fail "curl is required"
command -v python3 >/dev/null 2>&1 || fail "python3 is required"
[ -f "$COMMITTED" ] || fail "committed OpenAPI not found: $COMMITTED"

JAVA_BIN="java"
if [ -n "${JAVA_HOME:-}" ]; then
  if [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
  elif [ -x "$JAVA_HOME/bin/java.exe" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java.exe"
  fi
fi
command -v "$JAVA_BIN" >/dev/null 2>&1 || [ -x "$JAVA_BIN" ] || fail "java is required"

JAR="$(find "$ROOT/xs-app-monolith/target" -maxdepth 1 -name 'xs-app-monolith-*-boot.jar' 2>/dev/null | LC_ALL=C sort | tail -1 || true)"
[ -n "$JAR" ] || fail "boot jar not found; run mvn clean verify first"

PORT="${BOOT_PORT:-$(python3 -c 'import socket; s=socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()')}"

printf '==> G07 starting %s on port %s\n' "$(basename "$JAR")" "$PORT"
"$JAVA_BIN" -jar "$JAR" --server.port="$PORT" > "$WORK_DIR/app.log" 2>&1 &
APP_PID=$!

cleanup() {
  if kill -0 "$APP_PID" 2>/dev/null; then
    kill "$APP_PID" 2>/dev/null || true
    wait "$APP_PID" 2>/dev/null || true
  fi
}
trap cleanup EXIT INT TERM

RUNTIME="$WORK_DIR/runtime.yaml"
if ! curl -fsS --retry 40 --retry-delay 1 --retry-connrefused \
  "http://127.0.0.1:${PORT}/v3/api-docs.yaml" -o "$RUNTIME" 2>/dev/null; then
  tail -100 "$WORK_DIR/app.log" >&2
  fail "monolith did not expose /v3/api-docs.yaml"
fi

normalize() {
  sed -E 's#(url: http://(127\.0\.0\.1|localhost):)[0-9]+#\1PORT#' "$1"
}

printf '==> G07 comparing committed contract with runtime contract\n'
if ! diff -u <(normalize "$COMMITTED") <(normalize "$RUNTIME") > "$WORK_DIR/diff.txt"; then
  head -120 "$WORK_DIR/diff.txt" >&2
  fail "runtime OpenAPI drifted; regenerate docs/api/openapi.{yaml,json}"
fi

printf 'OK: G07 committed OpenAPI matches runtime OpenAPI\n'
