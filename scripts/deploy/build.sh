#!/usr/bin/env bash
# Build boot jars for one or both deployment modes.
# Usage: build.sh [monolith|user|tenant|all]   (default: all)
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/common.sh"

TARGET="${1:-all}"
cd "$ROOT"

JAVA_BIN="$(resolve_java)"
export JAVA_HOME="$(dirname "$(dirname "$JAVA_BIN")")"
export PATH="$JAVA_HOME/bin:$PATH"
info "using $(dirname "$JAVA_BIN")"

case "$TARGET" in
  monolith)
    info "building monolith boot jar"
    mvn -B -ntp -pl xs-app-monolith -am clean package
    ;;
  user)
    info "building user-service boot jar"
    mvn -B -ntp -pl xs-app-user -am clean package
    ;;
  tenant)
    info "building tenant-service boot jar"
    mvn -B -ntp -pl xs-app-tenant -am clean package
    ;;
  all)
    info "building all three boot jars"
    mvn -B -ntp -pl xs-app-monolith,xs-app-user,xs-app-tenant -am clean package
    ;;
  *)
    fail "target must be monolith, user, tenant, or all"
    ;;
esac

for mode in monolith user tenant; do
  if [ "$TARGET" = "all" ] || [ "$TARGET" = "$mode" ]; then
    mode_config "$mode"
    info "artifact: $JAR"
  fi
done
