#!/usr/bin/env bash
# Gate W02: lightweight secret pattern scan.
# Observation mode by default (always exits 0). Set STRICT_SECRETS=true to block.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

WORK_DIR="$ROOT/.tools/secret-scan"
PATTERN_FILE="$WORK_DIR/patterns.txt"
HITS="$WORK_DIR/hits.txt"
STRICT="${STRICT_SECRETS:-false}"

rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR"

cat > "$PATTERN_FILE" <<'PATTERNS'
AKIA[0-9A-Z]{16}
-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----
gh[pousr]_[A-Za-z0-9]{36,}
xox[baprs]-[A-Za-z0-9-]{10,}
eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}
(password|passwd|secret|api[._-]?key|access[._-]?token|client[._-]?secret)[[:space:]]*[:=][[:space:]]*["'][^"']{8,}["']
PATTERNS

printf '==> W02 scanning tracked source and config files\n'
grep -RInEf "$PATTERN_FILE" \
  --binary-files=without-match \
  --exclude-dir=.tools \
  --exclude-dir=target \
  --exclude-dir=.git \
  --exclude-dir=node_modules \
  --exclude-dir=.idea \
  --exclude='check-secrets.sh' \
  . > "$HITS" 2>/dev/null || true

if [ -s "$HITS" ]; then
  COUNT="$(wc -l < "$HITS" | tr -d ' ')"
  printf 'WARN: W02 found %s potential secret hit(s)\n' "$COUNT"
  head -50 "$HITS"
  printf 'Evidence: %s\n' "$HITS"
  if [ "$STRICT" = "true" ]; then
    printf 'ERROR: STRICT_SECRETS=true, blocking on secret hits\n' >&2
    exit 1
  fi
  printf 'Observation mode: not blocking. Review hits before promoting W02 to P0.\n'
  exit 0
fi

printf 'OK: W02 no potential secrets found\n'
