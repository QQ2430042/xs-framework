#!/usr/bin/env bash
# Gate G03: dependency baseline + no third-party pre-release dependencies.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

BASELINE="$ROOT/docs/ci/dependency-tree-baseline.txt"
WORK_DIR="$ROOT/.tools/dependency-tree"
COMBINED="$WORK_DIR/combined.txt"
UPDATE="${UPDATE_DEPENDENCY_BASELINE:-false}"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"

printf '==> G03 resolving dependency tree\n'
mvn -B -ntp -DskipTests \
  dependency:tree \
  -DoutputType=text \
  -DoutputFile=".tools/dependency-tree/tree.txt" \
  > "$WORK_DIR/maven.log" 2>&1 || {
    tail -80 "$WORK_DIR/maven.log" >&2
    fail "dependency:tree failed"
  }

mapfile -t TREE_FILES < <(find "$ROOT" -type f -path '*/.tools/dependency-tree/tree.txt' | LC_ALL=C sort)
[ "${#TREE_FILES[@]}" -gt 0 ] || fail "no dependency tree files were generated"

: > "$COMBINED"
for file in "${TREE_FILES[@]}"; do
  rel="${file#$ROOT/}"
  module="${rel%/.tools/dependency-tree/tree.txt}"
  if [ "$module" = "$rel" ]; then
    module="(root)"
  fi
  printf '## %s\n' "$module" >> "$COMBINED"
  tr -d '\r' < "$file" >> "$COMBINED"
done

printf '==> G03 checking third-party pre-release versions\n'
PRERELEASE_HITS="$(grep -E ':(jar|pom|test-jar|bundle|zip):[^:]*((^|[-.])SNAPSHOT|-RC[0-9]+|-M[0-9]+|-alpha[0-9]*|-beta[0-9]*)' "$COMBINED" | grep -v 'com\.xsframework:' || true)"
if [ -n "$PRERELEASE_HITS" ]; then
  printf '%s\n' "$PRERELEASE_HITS" >&2
  fail "third-party SNAPSHOT/RC/M/alpha/beta dependencies are forbidden"
fi

if [ "$UPDATE" = "true" ]; then
  if [ -n "${CI:-}" ]; then
    fail "refuse to update dependency baseline inside CI"
  fi
  mkdir -p "$(dirname "$BASELINE")"
  cp "$COMBINED" "$BASELINE"
  printf 'OK: dependency baseline updated at %s\n' "$BASELINE"
  exit 0
fi

[ -f "$BASELINE" ] || fail "baseline missing: $BASELINE (run UPDATE_DEPENDENCY_BASELINE=true locally)"

printf '==> G03 comparing dependency tree with baseline\n'
if ! diff -u "$BASELINE" "$COMBINED" > "$WORK_DIR/dependency-diff.txt"; then
  head -100 "$WORK_DIR/dependency-diff.txt" >&2
  fail "dependency tree drifted; update baseline only with an ADR"
fi

printf 'OK: G03 dependency tree matches baseline and has no third-party pre-release versions\n'
