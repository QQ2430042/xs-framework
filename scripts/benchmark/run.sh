#!/usr/bin/env bash
# 用法: bash scripts/benchmark/run.sh [传给 protocol_bench.py 的参数]
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
exec python3 scripts/benchmark/protocol_bench.py "$@"
