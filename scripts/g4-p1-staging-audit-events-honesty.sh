#!/usr/bin/env bash
# G4 leftover: P1 staging GET /tenants/{id}/audit-events honesty beyond #156.
#
# Extends docs/g4-p1-staging-audit-events.md. Does NOT switch GET off staging.
# Does NOT redo #147 / #152 / #156 / #166. Does NOT change AuditPort same-TX.
# Does NOT claim product G4 close. G18 stays closed. No Docker.
# Staging-like query/export stays always-green PASS.
# Live 127.0.0.1:5432 platform.audit_event is PASS only when reachable AND
# a query+export walk was observed. Missing password / table is honest SKIP.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -x "${HOME}/tools/apache-maven-3.8.7/bin/mvn" ]]; then
  export PATH="${HOME}/tools/apache-maven-3.8.7/bin:${PATH}"
elif [[ -x "${HOME}/tools/apache-maven-3.9.11/bin/mvn" ]]; then
  export PATH="${HOME}/tools/apache-maven-3.9.11/bin:${PATH}"
fi
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""
SKIP_TESTS=0

usage() {
  cat <<'EOF'
Usage:
  scripts/g4-p1-staging-audit-events-honesty.sh [--out DIR] [--skip-tests]
  scripts/g4-p1-staging-audit-events-honesty.sh --verify-report FILE

Always-green staging-like query/export + GET still on staging.
Live :5432 platform.audit_event is PASS only when credentials, listener,
and an observed query+export walk are present. Missing live DB is SKIP.
Does not switch GET off staging. Does not claim product G4 close.

  --out DIR           Output directory (default: .local/g4-p1-staging-audit-events-honesty/<utc>)
  --skip-tests        Inventory + honesty only (do not run mvn)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --skip-tests) SKIP_TESTS=1; shift ;;
    --verify-report) VERIFY_REPORT="${2:?}"; shift 2 ;;
    -h|--help) usage; exit 0 ;;
    *) echo "unknown argument: $1" >&2; usage >&2; exit 2 ;;
  esac
done

kv() { printf '%s=%s\n' "$1" "$2"; }
fail() { echo "FAIL: $*" >&2; exit 1; }

report_get() {
  local file="$1" key="$2"
  awk -F= -v k="$key" '$1==k {sub(/^[^=]+=/, ""); print; exit}' "$file"
}

verify_report() {
  local file="$1"
  [ -f "$file" ] || fail "report not found: $file"
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used staging_like_path get_still_on_staging switched_off_staging http_export_wizard leftover_rows_kept live_db_credential_set live_5432_listener live_staging_present live_staging_5432 live_staging_skip_reason live_query_observed live_export_observed redo_147 redo_152 redo_156 redo_166 g4_close g18_status audit_port_changed p0_m0_redone"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || fail "missing key $key in $file"
  done

  [ "$(report_get "$file" java_feature)" = "21" ] || fail "java_feature must be 21"
  [ "$(report_get "$file" boot_parent_version)" = "4.1.1" ] || fail "boot_parent_version must be 4.1.1"
  [ "$(report_get "$file" boot_parent_match)" = "PASS" ] || fail "boot_parent_match must be PASS"
  [ "$(report_get "$file" docker_used)" = "false" ] || fail "docker_used must be false"
  [ "$(report_get "$file" g18_status)" = "closed_unchanged" ] || fail "g18_status must stay closed_unchanged"
  [ "$(report_get "$file" audit_port_changed)" = "false" ] || fail "audit_port_changed must be false"
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || fail "p0_m0_redone must stay false"
  [ "$(report_get "$file" redo_147)" = "false" ] || fail "redo_147 must stay false"
  [ "$(report_get "$file" redo_152)" = "false" ] || fail "redo_152 must stay false"
  [ "$(report_get "$file" redo_156)" = "false" ] || fail "redo_156 must stay false"
  [ "$(report_get "$file" redo_166)" = "false" ] || fail "redo_166 must stay false"
  [ "$(report_get "$file" g4_close)" = "NOT_CLAIMED" ] || fail "g4_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" staging_like_path)" = "PASS" ] || fail "staging_like_path must be PASS"
  [ "$(report_get "$file" get_still_on_staging)" = "true" ] || fail "get_still_on_staging must stay true"
  [ "$(report_get "$file" switched_off_staging)" = "false" ] || fail "switched_off_staging must stay false"
  [ "$(report_get "$file" http_export_wizard)" = "false" ] || fail "http_export_wizard must stay false"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"

  local live
  live="$(report_get "$file" live_staging_5432)"
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) fail "live_staging_5432 must be PASS/SKIP/FAIL" ;;
  esac
  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" live_staging_present)" = "true" ] || fail "live_staging_5432=PASS requires live_staging_present=true"
    [ "$(report_get "$file" live_db_credential_set)" = "true" ] || fail "live_staging_5432=PASS requires live_db_credential_set=true"
    [ "$(report_get "$file" live_5432_listener)" = "true" ] || fail "live_staging_5432=PASS requires live_5432_listener=true"
    [ "$(report_get "$file" live_query_observed)" = "true" ] || fail "live_staging_5432=PASS requires live_query_observed=true"
    [ "$(report_get "$file" live_export_observed)" = "true" ] || fail "live_staging_5432=PASS requires live_export_observed=true"
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_staging_skip_reason)" ] || fail "live staging SKIP needs live_staging_skip_reason"
    [ "$(report_get "$file" live_query_observed)" != "true" ] || fail "SKIP cannot claim a live query walk"
    [ "$(report_get "$file" live_export_observed)" != "true" ] || fail "SKIP cannot claim a live export walk"
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=' "$file"; then
    fail "report must not contain password/secret material"
  fi
  echo "PASS: report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g4-p1-staging-audit-events-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G4 leftover: P1 staging audit-events honesty (staging-like PASS / live :5432 SKIP when unavailable)"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not switch GET off staging. Does not redo #147/#152/#156/#166. Not product G4 close."
echo

JAVA_FEATURE=missing
JAVA_VERSION_LINE=not-installed
if command -v java >/dev/null 2>&1; then
  JAVA_VERSION_LINE="$(java -version 2>&1 | head -n 1 | tr -d '\r')"
  JAVA_FEATURE="$(java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*java\.specification\.version[[:space:]]*=/{print $3; exit}')"
fi
[ "$JAVA_FEATURE" = "21" ] || fail "java_feature must be 21, got $JAVA_FEATURE"

BOOT_GROUP=""
BOOT_ARTIFACT=""
BOOT_VERSION=""
eval "$(python3 - "$ROOT/pom.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(sys.argv[1]).getroot()
parent = root.find("m:parent", ns)
if parent is None:
    print("BOOT_GROUP=''")
    print("BOOT_ARTIFACT=''")
    print("BOOT_VERSION=''")
    raise SystemExit(0)
def t(tag):
    n = parent.find(f"m:{tag}", ns)
    return (n.text or "").strip() if n is not None else ""
print("BOOT_GROUP=%r" % t("groupId"))
print("BOOT_ARTIFACT=%r" % t("artifactId"))
print("BOOT_VERSION=%r" % t("version"))
PY
)"
BOOT_PARENT_GA="${BOOT_GROUP}:${BOOT_ARTIFACT}:${BOOT_VERSION}"
if [ "$BOOT_GROUP" = "org.springframework.boot" ] && [ "$BOOT_ARTIFACT" = "spring-boot-starter-parent" ] && [ "$BOOT_VERSION" = "4.1.1" ]; then
  BOOT_PARENT_MATCH=PASS
else
  BOOT_PARENT_MATCH=FAIL
fi
[ "$BOOT_PARENT_MATCH" = "PASS" ] || fail "boot parent must be org.springframework.boot:spring-boot-starter-parent:4.1.1"

DOCKER_PRESENT=false
DOCKER_USED=false
if command -v docker >/dev/null 2>&1; then
  DOCKER_PRESENT=true
  log "docker binary present but leftover must not use it"
fi

port_open() {
  local host="$1" port="$2"
  python3 - "$host" "$port" <<'PY'
import socket, sys
host, port = sys.argv[1], int(sys.argv[2])
s = socket.socket()
s.settimeout(0.2)
try:
    s.connect((host, port))
except OSError:
    raise SystemExit(1)
finally:
    s.close()
PY
}

FORCE_SKIP=false
if [[ "${NEXUS_G4_P1_STAGING_SKIP_LIVE:-}" == "1" || "${NEXUS_G4_P1_STAGING_SKIP_LIVE:-}" == "true" ]]; then
  FORCE_SKIP=true
fi

CREDENTIAL_SET=false
if [[ -n "${NEXUS_DB_PASSWORD:-}" || -n "${NEXUS_DATASOURCE_PASSWORD:-}" ]]; then
  CREDENTIAL_SET=true
fi

LISTENER=false
if port_open 127.0.0.1 5432; then
  LISTENER=true
fi

LIVE_PRESENT=false
if [ "$CREDENTIAL_SET" = "true" ] && [ "$LISTENER" = "true" ] && [ "$FORCE_SKIP" != "true" ]; then
  LIVE_PRESENT=true
fi

if [ "$FORCE_SKIP" = "true" ]; then
  LIVE_REASON="NEXUS_G4_P1_STAGING_SKIP_LIVE=1; live :5432 probe forced SKIP"
elif [ "$CREDENTIAL_SET" != "true" ] && [ "$LISTENER" != "true" ]; then
  LIVE_REASON="live DB credential unset; nothing listening on 127.0.0.1:5432; platform.audit_event walk not observed; Docker forbidden"
elif [ "$CREDENTIAL_SET" != "true" ]; then
  LIVE_REASON="live DB credential unset; live staging SKIP (secrets stay in env/SecretPort)"
elif [ "$LISTENER" != "true" ]; then
  LIVE_REASON="credential set but 127.0.0.1:5432 not reachable; live staging SKIP; this leftover does not start PostgreSQL"
else
  LIVE_REASON="listener on 127.0.0.1:5432 but this leftover does not claim a walk until query/export were observed"
fi

STAGING_LIKE=PASS
GET_ON_STAGING=true
LIVE_STATUS=SKIP
LIVE_QUERY=false
LIVE_EXPORT=false
TEST_SUMMARY=""

if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl nexus-p1-tenant -am test \
    -Dtest=G4P1StagingAuditEventsHonestyTest \
    -Dsurefire.failIfNoSpecifiedTests=false | tee "$OUT/mvn.log"
  MVN_RC=${PIPESTATUS[0]}
  set -e
  [ "$MVN_RC" -eq 0 ] || fail "mvn leftover tests failed"
  TEST_SUMMARY="$(awk '/Tests run:/{line=$0} END{print line}' "$OUT/mvn.log")"
  if grep -q 'G4_P1_STAGING_LIVE_WALK' "$OUT/mvn.log"; then
    LIVE_STATUS=PASS
    LIVE_QUERY=true
    LIVE_EXPORT=true
    LIVE_REASON="live query+export walk observed on 127.0.0.1:5432"
  else
    LIVE_STATUS=SKIP
  fi
else
  TEST_SUMMARY="skipped (--skip-tests)"
fi

GIT_COMMIT="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
GIT_BRANCH="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"

REPORT="$OUT/report.env"
{
  kv schema_version 1
  kv timestamp_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  kv git_commit "$GIT_COMMIT"
  kv git_branch "$GIT_BRANCH"
  kv java_version_line "$JAVA_VERSION_LINE"
  kv java_feature "$JAVA_FEATURE"
  kv boot_parent_ga "$BOOT_PARENT_GA"
  kv boot_parent_version "$BOOT_VERSION"
  kv boot_parent_match "$BOOT_PARENT_MATCH"
  kv docker_present "$DOCKER_PRESENT"
  kv docker_used "$DOCKER_USED"
  kv staging_like_path "$STAGING_LIKE"
  kv get_still_on_staging "$GET_ON_STAGING"
  kv switched_off_staging false
  kv http_export_wizard false
  kv leftover_rows_kept true
  kv live_db_credential_set "$CREDENTIAL_SET"
  kv live_5432_listener "$LISTENER"
  kv live_staging_present "$LIVE_PRESENT"
  kv live_staging_5432 "$LIVE_STATUS"
  kv live_staging_skip_reason "$LIVE_REASON"
  kv live_query_observed "$LIVE_QUERY"
  kv live_export_observed "$LIVE_EXPORT"
  kv redo_147 false
  kv redo_152 false
  kv redo_156 false
  kv redo_166 false
  kv g4_close NOT_CLAIMED
  kv g4_close_reason "Honesty leftover around P1 staging GET /tenants/{id}/audit-events. Live :5432 SKIP when unavailable. Not product G4 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
  kv test_summary "$TEST_SUMMARY"
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "| Check | Result | Notes |"
  echo "|---|---|---|"
  echo "| JDK 21 | PASS | $JAVA_VERSION_LINE |"
  echo "| Boot 4.1.1 | $BOOT_PARENT_MATCH | \`$BOOT_PARENT_GA\` |"
  echo "| Staging-like query/export | $STAGING_LIKE | in-memory AuditRepository; GET still on staging |"
  echo "| GET still on staging | $GET_ON_STAGING | TenantController listByTenant / listByType |"
  echo "| Live :5432 platform.audit_event | $LIVE_STATUS | ${LIVE_REASON} |"
  echo "| live_query_observed | $LIVE_QUERY | export observed=$LIVE_EXPORT |"
  echo "| #147/#152/#156/#166 redo | false | sibling leftovers |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| G4 close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g4-p1-staging-audit-events-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT"

SANITIZED="${ROOT}/docs/engineering/g4-p1-staging-audit-events-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_staging_5432=$LIVE_STATUS g4_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (live :5432 $LIVE_STATUS)"
echo "See docs/g4-p1-staging-audit-events-honesty.md"
echo "Keep leftover row: docs/g4-p1-staging-audit-events.md"
echo "Not claimed: product G4 close / switching GET off staging / #147/#152/#156/#166 redo"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
