#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"
PASSWORD="${NEXUS_DB_PASSWORD:-${NEXUS_DATASOURCE_PASSWORD:-}}"
if [[ -z "${PASSWORD}" ]]; then
  echo "NEXUS_DB_PASSWORD (or NEXUS_DATASOURCE_PASSWORD) must be set in the environment" >&2
  exit 1
fi
export NEXUS_DB_PASSWORD="${NEXUS_DB_PASSWORD:-$PASSWORD}"
export NEXUS_DATASOURCE_PASSWORD="${NEXUS_DATASOURCE_PASSWORD:-$PASSWORD}"
export NEXUS_P1_DEMO_CONTEXT_ENABLED="${NEXUS_P1_DEMO_CONTEXT_ENABLED:-true}"
export NEXUS_HTTP_PORT="${NEXUS_HTTP_PORT:-${NEXUS_P1_PORT:-18081}}"
exec mvn -q -pl nexus-bootstrap -am spring-boot:run
