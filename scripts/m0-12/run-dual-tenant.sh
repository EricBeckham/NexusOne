#!/usr/bin/env bash
# Start (or reuse) the M0-01 bootstrap JAR and run M0-12 dual-tenant HTTP acceptance.
# Requires local PostgreSQL 15.x. Does not use Docker.
# Credentials only from the environment.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

: "${NEXUS_DB_URL:=jdbc:postgresql://127.0.0.1:5432/nexusone}"
: "${NEXUS_DB_USER:=nexus}"
: "${NEXUS_DB_PASSWORD:?set NEXUS_DB_PASSWORD in the environment}"
: "${NEXUS_HTTP_PORT:=18082}"
: "${NEXUS_PLATFORM_BOOTSTRAP_TOKEN:=m0-12-bootstrap}"

export NEXUS_DB_URL NEXUS_DB_USER NEXUS_DB_PASSWORD NEXUS_HTTP_PORT
export NEXUS_P1_DEMO_CONTEXT_ENABLED=true
export NEXUS_IAM_IDP="${NEXUS_IAM_IDP:-stub}"
export NEXUS_IAM_PERSISTENCE="${NEXUS_IAM_PERSISTENCE:-memory}"
export NEXUS_PLATFORM_BOOTSTRAP_TOKEN
export NEXUS_BASE_URL="${NEXUS_BASE_URL:-http://127.0.0.1:${NEXUS_HTTP_PORT}}"

echo "== runtime"
java -version
python3 --version

if command -v psql >/dev/null 2>&1; then
  export PGPASSWORD="${NEXUS_DB_PASSWORD}"
  PGVER="$(psql -h 127.0.0.1 -U "${NEXUS_DB_USER}" -d "${NEXUS_DB_URL##*/}" -Atc "SHOW server_version;")"
  echo "PostgreSQL ${PGVER}"
  case "${PGVER}" in
    15.*) ;;
    *)
      echo "FAIL: M0 dual-tenant script requires PostgreSQL 15.x, got ${PGVER}" >&2
      exit 1
      ;;
  esac
fi

STARTED=0
if curl -sf "${NEXUS_BASE_URL}/actuator/health" >/dev/null 2>&1; then
  echo "== reusing ${NEXUS_BASE_URL}"
else
  echo "== mvn -q -DskipTests package"
  mvn -q -DskipTests package
  JAR="$(ls "$ROOT"/nexus-bootstrap/target/nexus-bootstrap-*.jar | grep -v original | head -n1)"
  LOG="${NEXUS_M012_LOG:-/tmp/nexus-m0-12.log}"
  echo "== starting $JAR on :$NEXUS_HTTP_PORT (log $LOG)"
  java -jar "$JAR" >"$LOG" 2>&1 &
  PID=$!
  STARTED=1
  cleanup() {
    if [[ "${STARTED}" = "1" ]]; then
      kill "$PID" >/dev/null 2>&1 || true
      wait "$PID" 2>/dev/null || true
    fi
  }
  trap cleanup EXIT
  for _ in $(seq 1 90); do
    if curl -sf "${NEXUS_BASE_URL}/actuator/health" >/dev/null; then
      break
    fi
    if ! kill -0 "$PID" 2>/dev/null; then
      echo "FAIL: JAR exited before health; last log lines:" >&2
      tail -n 80 "$LOG" >&2
      exit 1
    fi
    sleep 1
  done
  curl -sf "${NEXUS_BASE_URL}/actuator/health" >/dev/null
fi

python3 "$ROOT/scripts/m0-12/dual-tenant-isolation.py" \
  --base "${NEXUS_BASE_URL}" \
  --bootstrap-token "${NEXUS_PLATFORM_BOOTSTRAP_TOKEN}" \
  --json-out "${NEXUS_M012_JSON:-/tmp/nexus-m0-12-dual-tenant.json}"

if [[ -f "$ROOT/web/package.json" ]]; then
  if command -v node >/dev/null 2>&1; then
    echo "== Vue client isolation assert"
    (cd "$ROOT/web" && node scripts/assert-client-isolation.mjs)
  fi
fi

echo "PASS: M0-12 dual-tenant HTTP acceptance (G18 still open; see checklist SKIP rows)"
