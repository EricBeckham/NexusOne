#!/usr/bin/env bash
# Prove M0-01: build the JAR, start it against local PostgreSQL 15, migrate, health, Trace ID.
# Requires NEXUS_DB_URL, NEXUS_DB_USER, NEXUS_DB_PASSWORD in the environment.
# Does not use Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

: "${NEXUS_DB_URL:?set NEXUS_DB_URL (jdbc:postgresql://host:port/db)}"
: "${NEXUS_DB_USER:?set NEXUS_DB_USER}"
: "${NEXUS_DB_PASSWORD:?set NEXUS_DB_PASSWORD (may be empty for trust, but the variable must be set)}"
NEXUS_HTTP_PORT="${NEXUS_HTTP_PORT:-18080}"
export NEXUS_DB_URL NEXUS_DB_USER NEXUS_DB_PASSWORD NEXUS_HTTP_PORT

echo "==> mvn -q -DskipTests package"
mvn -q -DskipTests package

JAR="$(ls "$ROOT"/nexus-bootstrap/target/nexus-bootstrap-*.jar | grep -v original | head -n1)"
LOG="$(mktemp)"
TRACE="0199a1c0-0000-7000-8000-00000000beef"

echo "==> starting $JAR on :$NEXUS_HTTP_PORT"
java -jar "$JAR" >"$LOG" 2>&1 &
PID=$!
cleanup() {
  kill "$PID" >/dev/null 2>&1 || true
  wait "$PID" 2>/dev/null || true
}
trap cleanup EXIT

for i in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:${NEXUS_HTTP_PORT}/actuator/health" >/tmp/nexus-health.json 2>/dev/null; then
    break
  fi
  if ! kill -0 "$PID" 2>/dev/null; then
    echo "FAIL: process exited before health became ready"
    cat "$LOG"
    exit 1
  fi
  sleep 1
done

python3 - <<'PY'
import json,sys
body=json.load(open("/tmp/nexus-health.json"))
assert body.get("status")=="UP", body
print("PASS: health UP")
PY

RUNTIME="$(curl -fsS -D - -H "X-Trace-Id: ${TRACE}" "http://127.0.0.1:${NEXUS_HTTP_PORT}/api/v1/meta/runtime")"
echo "$RUNTIME" | grep -q "$TRACE"
echo "$RUNTIME" | grep -q 'flyway_static_only'
echo "PASS: runtime trace_id and schema_manager"

NOTFOUND="$(curl -sS -o /tmp/nexus-404.json -w "%{http_code}" "http://127.0.0.1:${NEXUS_HTTP_PORT}/api/v1/missing")"
test "$NOTFOUND" = "404"
python3 - <<'PY'
import json
body=json.load(open("/tmp/nexus-404.json"))
assert body["code"]=="CORE.API.0002", body
assert "trace_id" in body, body
print("PASS: error envelope", body["code"], "trace_id", body["trace_id"])
PY

if grep -E '"traceId"|"trace_id"' "$LOG" >/dev/null; then
  echo "PASS: structured log contains trace id field"
else
  echo "WARN: log file has no traceId yet (health may precede first traced request); dumping last lines"
  tail -n 20 "$LOG"
  # runtime request should have been logged
  if ! grep -F "$TRACE" "$LOG" >/dev/null; then
    echo "FAIL: trace id not found in logs"
    exit 1
  fi
  echo "PASS: inbound trace id present in logs"
fi

echo "PASS: M0-01 JAR start/migrate/health/trace"
