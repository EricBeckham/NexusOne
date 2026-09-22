#!/usr/bin/env bash
# G4 leftover: P1 staging GET /tenants/{id}/audit-events query + export honesty.
# Staging-like path is Zonky / in-memory. Live 127.0.0.1:5432 is honest SKIP when
# password, connection, or platform.audit_event is missing.
# Does not switch GET off staging. Does not change AuditPort same-TX.
# Does not redo HTTP export wizard (#152). Does not claim product G4 closed.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "${ROOT}"

if ! command -v mvn >/dev/null 2>&1; then
  if [[ -x "${HOME}/tools/apache-maven-3.8.7/bin/mvn" ]]; then
    export PATH="${HOME}/tools/apache-maven-3.8.7/bin:${PATH}"
  fi
fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "ERROR: mvn not on PATH" >&2
  exit 1
fi

WORK="${G4_P1_STAGING_WORK:-${ROOT}/.local/g4-p1-staging-audit-events}"
mkdir -p "${WORK}"
chmod 700 "${WORK}" || true
REPORT="${WORK}/report.env"
STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

kv() {
  printf '%s=%s\n' "$1" "$2" | tee -a "${REPORT}"
}

: >"${REPORT}"
kv leftover g4-p1-staging-audit-events
kv recorded_at "${STAMP}"
kv java_version "$(java -version 2>&1 | head -n1 | tr -d '"')"
kv mvn_version "$(mvn -version | head -n1)"
kv boot "4.1.1"
kv switched_off_staging false
kv product_g4_closed false
kv http_export_wizard_redone false
kv auditport_same_tx_changed false
kv g18_reopened false

echo "== G4 leftover: P1 staging audit-events query/export (in-memory + Zonky) =="
mvn -pl nexus-p1-tenant,nexus-audit-core,nexus-bootstrap -am test \
  -Dtest=P1StagingAuditEventsHonestyTest,P1StagingAuditEventsLiveSkipTest,G4P1StagingAuditEventsIT,JdbcAuditChainTransactionTest \
  -Dsurefire.failIfNoSpecifiedTests=false
kv staging_like_path PASS

LIVE_STATUS=SKIP
LIVE_REASON="NEXUS_DB_PASSWORD unset or live 127.0.0.1:5432 / platform.audit_event unavailable"
PASSWORD="${NEXUS_DB_PASSWORD:-${NEXUS_DATASOURCE_PASSWORD:-}}"
if [[ "${NEXUS_G4_P1_STAGING_SKIP_LIVE:-}" == "1" ]]; then
  LIVE_REASON="NEXUS_G4_P1_STAGING_SKIP_LIVE=1"
elif [[ -n "${PASSWORD}" ]] && command -v psql >/dev/null 2>&1; then
  export PGPASSWORD="${PASSWORD}"
  DB_URL="${NEXUS_DB_URL:-${NEXUS_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:5432/nexusone}}"
  PGHOST="127.0.0.1"
  PGPORT="5432"
  PGUSER="${NEXUS_DB_USER:-${NEXUS_DATASOURCE_USERNAME:-postgres}}"
  PGDATABASE="nexusone"
  if [[ "${DB_URL}" =~ jdbc:postgresql://([^:/]+):([0-9]+)/([^?]+) ]]; then
    PGHOST="${BASH_REMATCH[1]}"
    PGPORT="${BASH_REMATCH[2]}"
    PGDATABASE="${BASH_REMATCH[3]}"
  fi
  if psql -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d "${PGDATABASE}" -Atc \
      "SELECT to_regclass('platform.audit_event')" >/tmp/g4-p1-staging-regclass.txt 2>/dev/null; then
    REGCLASS="$(tr -d '[:space:]' </tmp/g4-p1-staging-regclass.txt)"
    if [[ "${REGCLASS}" == "platform.audit_event" ]]; then
      COUNT="$(psql -h "${PGHOST}" -p "${PGPORT}" -U "${PGUSER}" -d "${PGDATABASE}" -Atc \
        "SELECT count(*) FROM platform.audit_event")"
      python3 - <<PY
import json
from pathlib import Path
Path("${WORK}/live-staging-audit-events.json").write_text(json.dumps({
    "leftover": "g4-p1-staging-audit-events",
    "source_table": "platform.audit_event",
    "query_path": "GET /api/v1/p1/tenants/{id}/audit-events",
    "switched_off_staging": False,
    "product_g4_closed": False,
    "http_export_wizard": False,
    "live": True,
    "count": int("${COUNT}".strip() or "0"),
}, indent=2) + "\n")
PY
      LIVE_STATUS=PASS
      LIVE_REASON="queried platform.audit_event count=${COUNT}"
    else
      LIVE_REASON="platform.audit_event missing on ${PGHOST}:${PGPORT}/${PGDATABASE}"
    fi
  else
    LIVE_REASON="psql could not reach ${PGHOST}:${PGPORT}/${PGDATABASE}"
  fi
elif [[ -z "${PASSWORD}" ]]; then
  LIVE_REASON="NEXUS_DB_PASSWORD / NEXUS_DATASOURCE_PASSWORD not set"
else
  LIVE_REASON="psql not on PATH; live export left to P1StagingAuditEventsLiveSkipTest"
fi

kv live_staging_5432 "${LIVE_STATUS}"
kv live_skip_reason "${LIVE_REASON}"
echo "${LIVE_STATUS}: live P1 staging — ${LIVE_REASON}"
echo "See docs/g4-p1-staging-audit-events.md"
echo "Not claimed: product G4 close / switching GET off staging / HTTP export wizard redo"
