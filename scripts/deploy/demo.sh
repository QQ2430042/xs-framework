#!/usr/bin/env bash
# One-command example: build both modes, start, smoke-test, stop.
# Usage: demo.sh [--keep-running]
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

KEEP="${1:-}"
cleanup() {
  if [ "$KEEP" != "--keep-running" ]; then
    bash "$ROOT/scripts/deploy/stop.sh" all || true
  fi
}
trap cleanup EXIT INT TERM

bash "$ROOT/scripts/deploy/build.sh" all
bash "$ROOT/scripts/deploy/start.sh" monolith
bash "$ROOT/scripts/deploy/start.sh" user
bash "$ROOT/scripts/deploy/start.sh" tenant
bash "$ROOT/scripts/deploy/status.sh"
bash "$ROOT/scripts/deploy/smoke.sh" all

if [ "$KEEP" = "--keep-running" ]; then
  info "demo finished; all services left running. Stop with: bash scripts/deploy/stop.sh all"
else
  info "demo finished; all services stopped"
fi
