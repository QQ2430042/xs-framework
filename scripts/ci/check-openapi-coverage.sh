#!/usr/bin/env bash
# Gate G06: the union of all authored specs and the runtime contract expose the
# same paths, HTTP methods and operationIds.
# Usage: check-openapi-coverage.sh <spec1> [spec2 ...] <runtime>
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

if [ "$#" -lt 2 ]; then
  printf 'ERROR: usage: %s <spec1> [spec2 ...] <runtime>\n' "$0" >&2
  exit 1
fi

# Last argument is the runtime contract; everything before it is an authored spec.
RUNTIME="${!#}"
SPECS=("${@:1:$#-1}")

for spec in "${SPECS[@]}"; do
  [ -f "$spec" ] || { printf 'ERROR: spec not found: %s\n' "$spec" >&2; exit 1; }
done
[ -f "$RUNTIME" ] || { printf 'ERROR: runtime contract not found: %s\n' "$RUNTIME" >&2; exit 1; }

python3 - "${SPECS[@]}" "$RUNTIME" <<'PY'
import re
import sys

METHODS = {"get", "post", "put", "patch", "delete", "head", "options", "trace"}


def scan(path):
    paths = set()
    operations = {}
    current_path = None
    current_op = None
    in_paths = False

    with open(path, "r", encoding="utf-8") as handle:
        for raw in handle:
            line = raw.rstrip("\n")
            if re.match(r"^paths:\s*$", line):
                in_paths = True
                continue
            if not in_paths:
                continue
            if line and not line[0].isspace():
                break

            match = re.match(r"^(\s*)(/[^:\s]+):\s*$", line)
            if match:
                current_path = match.group(2)
                current_op = None
                paths.add(current_path)
                continue

            match = re.match(r"^\s*([a-z]+):\s*$", line)
            if match and current_path and match.group(1) in METHODS:
                current_op = (current_path, match.group(1))
                operations[current_op] = None
                continue

            if current_op:
                match = re.match(r"^\s+operationId:\s*['\"]?([^'\"\s]+)['\"]?\s*$", line)
                if match:
                    operations[current_op] = match.group(1)

    return paths, operations


spec_files = sys.argv[1:-1]
runtime_file = sys.argv[-1]

spec_paths = set()
spec_ops = {}
for spec in spec_files:
    paths, operations = scan(spec)
    overlap = set(spec_paths) & paths
    if overlap:
        print(f"ERROR: duplicate paths across authored specs: {sorted(overlap)}", file=sys.stderr)
        sys.exit(1)
    op_overlap = set(spec_ops) & set(operations)
    if op_overlap:
        print(f"ERROR: duplicate operations across authored specs: {sorted(op_overlap)}", file=sys.stderr)
        sys.exit(1)
    spec_paths |= paths
    spec_ops.update(operations)

runtime_paths, runtime_ops = scan(runtime_file)

problems = []

for label, left, right in (
    ("paths missing from runtime", spec_paths, runtime_paths),
    ("paths missing from spec", runtime_paths, spec_paths),
):
    delta = sorted(left - right)
    if delta:
        problems.append(f"{label}: {delta}")

for label, left, right in (
    ("operations missing from runtime", spec_ops, runtime_ops),
    ("operations missing from spec", runtime_ops, spec_ops),
):
    delta = sorted(f"{method.upper()} {path}" for path, method in (left.keys() - right.keys()))
    if delta:
        problems.append(f"{label}: {delta}")

for key in sorted(set(spec_ops) & set(runtime_ops)):
    spec_id = spec_ops[key]
    runtime_id = runtime_ops[key]
    if spec_id and runtime_id and spec_id != runtime_id:
        method, route = key[1].upper(), key[0]
        problems.append(
            f"operationId drift on {method} {route}: spec={spec_id}, runtime={runtime_id}"
        )

for label, operations in (("spec", spec_ops), ("runtime", runtime_ops)):
    seen = {}
    for (path, method), op_id in operations.items():
        if not op_id:
            continue
        if op_id in seen:
            problems.append(f"duplicate operationId in {label}: {op_id}")
        seen[op_id] = (path, method)

if problems:
    print("ERROR: OpenAPI coverage gate failed", file=sys.stderr)
    for item in problems:
        print(f"  - {item}", file=sys.stderr)
    sys.exit(1)

print(f"OK: G06 {len(spec_files)} specs / {len(spec_paths)} paths / {len(spec_ops)} operations are aligned")
PY
