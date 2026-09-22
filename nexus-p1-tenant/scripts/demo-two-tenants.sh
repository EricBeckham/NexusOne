#!/usr/bin/env bash
# Two-tenant create/stop + platform protection + quota reject on local PostgreSQL 15.x.
# Requires NEXUS_DB_PASSWORD (or legacy NEXUS_DATASOURCE_PASSWORD) in the environment.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

PASSWORD="${NEXUS_DB_PASSWORD:-${NEXUS_DATASOURCE_PASSWORD:-}}"
if [[ -z "${PASSWORD}" ]]; then
  echo "NEXUS_DB_PASSWORD (or NEXUS_DATASOURCE_PASSWORD) must be set in the environment" >&2
  exit 1
fi

export NEXUS_DB_URL="${NEXUS_DB_URL:-${NEXUS_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:5432/nexus_p1}}"
export NEXUS_DB_USER="${NEXUS_DB_USER:-${NEXUS_DATASOURCE_USERNAME:-nexus_p1}}"
export NEXUS_DB_PASSWORD="${NEXUS_DB_PASSWORD:-$PASSWORD}"
export NEXUS_DATASOURCE_URL="${NEXUS_DATASOURCE_URL:-$NEXUS_DB_URL}"
export NEXUS_DATASOURCE_USERNAME="${NEXUS_DATASOURCE_USERNAME:-$NEXUS_DB_USER}"
export NEXUS_DATASOURCE_PASSWORD="${NEXUS_DATASOURCE_PASSWORD:-$PASSWORD}"
export NEXUS_HTTP_PORT="${NEXUS_HTTP_PORT:-${NEXUS_P1_PORT:-18081}}"
export NEXUS_P1_PORT="${NEXUS_P1_PORT:-$NEXUS_HTTP_PORT}"
export NEXUS_P1_DEMO_CONTEXT_ENABLED=true
export NEXUS_P1_QUOTA_APP_LIMIT="${NEXUS_P1_QUOTA_APP_LIMIT:-2}"
export NEXUS_P1_QUOTA_CUSTOM_LEDGER_LIMIT="${NEXUS_P1_QUOTA_CUSTOM_LEDGER_LIMIT:-2}"

echo "== runtime"
echo "Spring Boot parent: 4.1.1 (one.nexus:nexusone)"
java -version
if command -v psql >/dev/null 2>&1; then
  export PGPASSWORD="${NEXUS_DB_PASSWORD}"
  psql "${NEXUS_DB_URL/#jdbc:postgresql:\/\//postgres://}" -c "SELECT current_setting('server_version') AS pg_server_version, version();" \
    || PGPASSWORD="${NEXUS_DB_PASSWORD}" psql -h 127.0.0.1 -p 5432 -U "${NEXUS_DB_USER}" -d nexus_p1 \
      -c "SELECT current_setting('server_version') AS pg_server_version, version();"
fi

BASE="http://127.0.0.1:${NEXUS_P1_PORT}"
ACTOR="${NEXUS_P1_DEMO_ACTOR_ID:-01900000-0000-7000-8000-0000000000ac}"
TRACE="demo-$(date -u +%Y%m%d%H%M%S)"

mvn -q -DskipTests package
JAR="nexus-bootstrap/target/nexus-bootstrap-0.1.0-SNAPSHOT.jar"
if [[ ! -f "$JAR" ]]; then
  echo "missing $JAR" >&2
  exit 1
fi

java -jar "$JAR" > /tmp/nexus-p1-tenant.log 2>&1 &
PID=$!
cleanup() {
  kill "$PID" 2>/dev/null || true
  wait "$PID" 2>/dev/null || true
}
trap cleanup EXIT

for _ in $(seq 1 60); do
  if curl -sf "$BASE/actuator/health" >/dev/null; then
    break
  fi
  sleep 0.5
done
curl -sf "$BASE/actuator/health" >/dev/null

auth() {
  curl -sS -H "Content-Type: application/json" \
    -H "X-Nexus-Demo-Actor-Id: ${ACTOR}" \
    -H "X-Nexus-Demo-Reason: m0-03-demo" \
    -H "X-Nexus-Trace-Id: ${TRACE}" \
    "$@"
}

echo "== platform tenant"
auth "$BASE/api/v1/p1/platform-tenant"
echo

SUFFIX="$(date -u +%H%M%S)"
ALPHA="alpha_${SUFFIX}"
BETA="beta_${SUFFIX}"

create() {
  local code="$1" name="$2"
  auth -X POST "$BASE/api/v1/p1/tenants" -d "$(cat <<JSON
{"name":"${name}","code":"${code}","legal_name":"${name} Ltd",
 "admin":{"display_name":"Admin ${code}","email":"${code}@example.com"},
 "config":{"locale":"zh-CN","timezone":"Asia/Shanghai","currency":"CNY"}}
JSON
)"
}

echo "== create ${ALPHA}"
ALPHA_BODY="$(create "$ALPHA" "Alpha ${SUFFIX}")"
echo "$ALPHA_BODY"
ALPHA_ID="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["tenant"]["id"])' <<<"$ALPHA_BODY")"

echo "== create ${BETA}"
BETA_BODY="$(create "$BETA" "Beta ${SUFFIX}")"
echo "$BETA_BODY"
BETA_ID="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["tenant"]["id"])' <<<"$BETA_BODY")"

echo "== stop ${ALPHA}"
auth -X POST "$BASE/api/v1/p1/tenants/${ALPHA_ID}/disable" -d '{"confirm":true,"reason":"demo stop"}'
echo

echo "== enable ${ALPHA}"
auth -X POST "$BASE/api/v1/p1/tenants/${ALPHA_ID}/enable" -d '{"confirm":true}'
echo

PLATFORM_ID="$(auth "$BASE/api/v1/p1/platform-tenant" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
echo "== stop platform (expect P1.TENANT.0004)"
auth -X POST "$BASE/api/v1/p1/tenants/${PLATFORM_ID}/disable" -d '{"confirm":true,"reason":"should fail"}'
echo
echo "== platform disable rejected audit"
auth "$BASE/api/v1/p1/tenants/${PLATFORM_ID}/audit-events?event_type=p1.tenant.disable.rejected"
echo

echo "== consume CUSTOM_LEDGER to limit then overflow"
auth -X POST "$BASE/api/v1/p1/tenants/${BETA_ID}/quotas/consume" \
  -d '{"metric":"CUSTOM_LEDGER","amount":2,"idempotency_key":"demo-ledgers"}'
echo
echo "== over-limit (expect P1.QUOTA.0001)"
auth -X POST "$BASE/api/v1/p1/tenants/${BETA_ID}/quotas/consume" \
  -d '{"metric":"CUSTOM_LEDGER","amount":1,"idempotency_key":"demo-overflow"}'
echo
echo "== quota rejected audit"
auth "$BASE/api/v1/p1/tenants/${BETA_ID}/audit-events?event_type=p1.quota.rejected"
echo

echo "demo complete. log: /tmp/nexus-p1-tenant.log"
