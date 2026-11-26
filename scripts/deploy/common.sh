#!/usr/bin/env bash
# Shared helpers for deployment scripts. Source only; do not execute directly.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
RUN_DIR="$ROOT/.tools/deploy"
mkdir -p "$RUN_DIR"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

info() {
  printf '==> %s\n' "$*"
}

# Resolve a JDK 25 java binary. Try bundled .tools/jdk25, then JAVA_HOME, then PATH.
# Every candidate is version-checked so a JDK 21 on JAVA_HOME never wins by accident.
resolve_java() {
  local candidates=() candidate major seen=""
  for j in "$ROOT"/.tools/jdk25/*/bin/java "$ROOT"/.tools/jdk25/*/bin/java.exe; do
    [ -x "$j" ] && candidates+=("$j")
  done
  if [ -n "${JAVA_HOME:-}" ]; then
    for name in java java.exe; do
      [ -x "$JAVA_HOME/bin/$name" ] && candidates+=("$JAVA_HOME/bin/$name")
    done
  fi
  candidate="$(command -v java 2>/dev/null || true)"
  [ -n "$candidate" ] && candidates+=("$candidate")

  for candidate in "${candidates[@]}"; do
    major="$("$candidate" -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
    seen="$seen  $candidate (major=$major)\n"
    if [ "$major" = "25" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  [ "${#candidates[@]}" -gt 0 ] || fail "no java binary found; set JAVA_HOME to JDK 25"
  printf 'JDK 25 required. Candidates:\n%b' "$seen" >&2
  fail "set JAVA_HOME to JDK 25 or keep the bundled .tools/jdk25"
}

# Populate ARTIFACT / PORT / JAR / PID_FILE / LOG_FILE for a mode.
mode_config() {
  local mode="${1:-}"
  case "$mode" in
    monolith)
      ARTIFACT="xs-app-monolith"
      PORT="${MONOLITH_PORT:-18080}"
      ;;
    user)
      ARTIFACT="xs-app-user"
      PORT="${USER_PORT:-18081}"
      ;;
    tenant)
      ARTIFACT="xs-app-tenant"
      PORT="${TENANT_PORT:-18082}"
      ;;
    *)
      fail "mode must be 'monolith', 'user' or 'tenant', got: $mode"
      ;;
  esac
  JAR="$(find "$ROOT/$ARTIFACT/target" -maxdepth 1 -name "$ARTIFACT-*-boot.jar" 2>/dev/null | LC_ALL=C sort | tail -1 || true)"
  [ -n "$JAR" ] && [ -f "$JAR" ] || fail "boot jar not found for $mode; run: bash scripts/deploy/build.sh $mode"
  PID_FILE="$RUN_DIR/$mode.pid"
  LOG_FILE="$RUN_DIR/$mode.log"
}

wait_ready() {
  local port="$1" log="$2" label="$3" i
  for i in $(seq 1 60); do
    if curl -fsS "http://127.0.0.1:${port}/v3/api-docs" -o /dev/null 2>/dev/null; then
      return 0
    fi
    if [ -f "$log" ] && grep -q "APPLICATION FAILED TO START" "$log" 2>/dev/null; then
      tail -60 "$log" >&2
      fail "$label failed to start"
    fi
    sleep 1
  done
  tail -60 "$log" >&2
  fail "$label did not become ready on port $port within 60s"
}

is_running() {
  local pid_file="$1"
  [ -f "$pid_file" ] || return 1
  local pid
  pid="$(cat "$pid_file" 2>/dev/null || true)"
  [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null
}
