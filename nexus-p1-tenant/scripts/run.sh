#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if [[ -z "${NEXUS_DATASOURCE_PASSWORD:-}" ]]; then
  echo "NEXUS_DATASOURCE_PASSWORD must be set in the environment" >&2
  exit 1
fi
export NEXUS_P1_DEMO_CONTEXT_ENABLED="${NEXUS_P1_DEMO_CONTEXT_ENABLED:-true}"
exec mvn -q spring-boot:run
