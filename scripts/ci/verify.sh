#!/usr/bin/env bash
# Single entry point for every P0 CI gate plus P1 observations. Safe to run locally.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

SPECTRAL_VERSION="${SPECTRAL_VERSION:-6.15.0}"
SPEC="docs/spec/openapi/user-api.yaml"
TENANT_SPEC="docs/spec/openapi/tenant-api.yaml"
RULES="docs/ci/spectral.yaml"

fail() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

step() {
  printf '\n==> %s\n' "$*"
}

step "0/8 preflight"
require_cmd java
require_cmd mvn
require_cmd node
require_cmd npx
require_cmd curl
require_cmd python3
[ -f "$SPEC" ] || fail "missing spec: $SPEC"
[ -f "$TENANT_SPEC" ] || fail "missing spec: $TENANT_SPEC"
[ -f "$RULES" ] || fail "missing ruleset: $RULES"

step "1/8 G01+G02 enforcer: JDK 25 / Maven 3.9+ / dependency convergence"
mvn -B -ntp -pl '!xs-dependencies' enforcer:enforce@ci-enforcer

step "2/8 G03 dependency baseline and pre-release policy"
bash scripts/ci/check-dependencies.sh

step "3/8 G04 clean verify: compile + unit + ArchUnit + H2 integration"
mvn -B -ntp clean verify

step "4/8 G05 OpenAPI lint (authored + runtime)"
RUNTIME_SPEC="docs/api/openapi.yaml"
[ -f "$RUNTIME_SPEC" ] || fail "missing runtime contract: $RUNTIME_SPEC"
npx --yes "@stoplight/spectral-cli@${SPECTRAL_VERSION}" lint "$SPEC" "$TENANT_SPEC" "$RUNTIME_SPEC" \
  --ruleset "$RULES" --fail-severity=error

step "5/8 G06 spec/runtime path-method-operationId coverage"
bash scripts/ci/check-openapi-coverage.sh "$SPEC" "$TENANT_SPEC" docs/api/openapi.yaml

step "6/8 G07 runtime OpenAPI drift"
bash scripts/ci/check-openapi-drift.sh

step "7/8 P1 W02 secret scan (observation)"
bash scripts/ci/check-secrets.sh

step "8/8 gate summary"
printf 'OK: all P0 CI gates passed\n'
