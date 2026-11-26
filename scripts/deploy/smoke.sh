#!/usr/bin/env bash
# Smoke-test one or all running modes: OpenAPI + domain endpoints + guards.
# Usage: smoke.sh <monolith|user|tenant|all>
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

TARGET="${1:-all}"
command -v curl >/dev/null 2>&1 || fail "curl is required"
command -v python3 >/dev/null 2>&1 || fail "python3 is required"

json_code() {
  python3 -c 'import json,sys; print(json.load(sys.stdin).get("code"))' 2>/dev/null || true
}

smoke_openapi() {
  local base="$1" mode="$2" status
  status="$(curl -sS -o /dev/null -w '%{http_code}' "${base}/v3/api-docs")"
  [ "$status" = "200" ] || fail "[$mode] /v3/api-docs returned $status"
  info "[$mode] OpenAPI OK"
}

smoke_user() {
  local base="$1" mode="$2" suffix body code user_id fetched fetched_id missing_status
  info "[$mode] checking user endpoints"
  suffix="$(date +%s)$(date +%N | cut -c1-3)"
  body="$(curl -sS -X POST "${base}/api/v1/users" \
    -H 'Content-Type: application/json' -H 'X-Tenant-Id: tenant-1' \
    -d "{\"username\":\"smoke-${suffix}\",\"displayName\":\"Smoke ${suffix}\"}")"
  code="$(printf '%s' "$body" | json_code)"
  [ "$code" = "0" ] || fail "[$mode] create user failed: $body"
  user_id="$(printf '%s' "$body" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["userId"])')"
  [ -n "$user_id" ] || fail "[$mode] no userId in response: $body"
  fetched="$(curl -sS "${base}/api/v1/users/${user_id}" -H 'X-Tenant-Id: tenant-1')"
  fetched_id="$(printf '%s' "$fetched" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["userId"])' 2>/dev/null || true)"
  [ "$fetched_id" = "$user_id" ] || fail "[$mode] get user mismatch: $fetched"
  missing_status="$(curl -sS -o /dev/null -w '%{http_code}' -X POST "${base}/api/v1/users" \
    -H 'Content-Type: application/json' -d '{"username":"no-tenant"}')"
  [ "$missing_status" = "400" ] || fail "[$mode] missing tenant returned $missing_status, expected 400"
  info "[$mode] user OK (userId=$user_id)"
}

smoke_tenant() {
  local base="$1" mode="$2" suffix body code tenant_id fetched fetched_id list status_value
  info "[$mode] checking tenant endpoints"
  suffix="$(date +%s)$(date +%N | cut -c1-3)"
  body="$(curl -sS -X POST "${base}/api/v1/tenants" \
    -H 'Content-Type: application/json' \
    -d "{\"tenantName\":\"smoke-${suffix}\",\"description\":\"Smoke ${suffix}\"}")"
  code="$(printf '%s' "$body" | json_code)"
  [ "$code" = "0" ] || fail "[$mode] create tenant failed: $body"
  tenant_id="$(printf '%s' "$body" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["tenantId"])')"
  [ -n "$tenant_id" ] || fail "[$mode] no tenantId in response: $body"
  fetched="$(curl -sS "${base}/api/v1/tenants/${tenant_id}")"
  fetched_id="$(printf '%s' "$fetched" | python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["tenantId"])' 2>/dev/null || true)"
  [ "$fetched_id" = "$tenant_id" ] || fail "[$mode] get tenant mismatch: $fetched"
  list="$(curl -sS "${base}/api/v1/tenants")"
  printf '%s' "$list" | python3 -c 'import json,sys; assert isinstance(json.load(sys.stdin)["data"], list)' || fail "[$mode] list tenants failed: $list"
  status_value="$(curl -sS -X PATCH "${base}/api/v1/tenants/${tenant_id}/status" \
    -H 'Content-Type: application/json' -d '{"status":"SUSPENDED"}' | \
    python3 -c 'import json,sys; print(json.load(sys.stdin)["data"]["status"])' 2>/dev/null || true)"
  [ "$status_value" = "SUSPENDED" ] || fail "[$mode] change status failed: $status_value"
  info "[$mode] tenant OK (tenantId=$tenant_id)"
}

smoke_one() {
  local mode="$1"
  mode_config "$mode"
  local base="http://127.0.0.1:${PORT}"
  is_running "$PID_FILE" || fail "$mode is not running; run: bash scripts/deploy/start.sh $mode"
  smoke_openapi "$base" "$mode"
  case "$mode" in
    monolith) smoke_user "$base" "$mode"; smoke_tenant "$base" "$mode" ;;
    user) smoke_user "$base" "$mode" ;;
    tenant) smoke_tenant "$base" "$mode" ;;
  esac
  info "[$mode] smoke OK"
}

case "$TARGET" in
  monolith|user|tenant) smoke_one "$TARGET" ;;
  all) smoke_one monolith; smoke_one user; smoke_one tenant ;;
  *) fail "target must be monolith, user, tenant, or all" ;;
esac
