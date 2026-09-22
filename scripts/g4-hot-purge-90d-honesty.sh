#!/usr/bin/env bash
# G4 leftover: 90-day hot-row purge window honesty.
# Does not redo #123 / #136 / #147 / #152 / #156 / #166.
# Does not DROP platform.audit_record. Does not change AuditPort same-TX.
# Live :5432 / JAR / Quartz / MinIO remaining window is PASS only when
# those are present AND a live purge walk was observed. Missing live
# DB/JAR is honest SKIP. Does not claim product G4 close. G18 stays closed.
# No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
if [[ -x "${HOME}/tools/apache-maven-3.8.7/bin/mvn" ]]; then
  export PATH="${HOME}/tools/apache-maven-3.8.7/bin:${PATH}"
fi
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""
SKIP_TESTS=0

usage() {
  cat <<'EOF'
Usage:
  scripts/g4-hot-purge-90d-honesty.sh [--out DIR] [--skip-tests]
  scripts/g4-hot-purge-90d-honesty.sh --verify-report FILE

In-memory 90-day / 365-day window policy may PASS. Live :5432 / JAR /
Quartz / MinIO remaining purge window is PASS only when present AND a
live purge walk was observed. Missing live DB/JAR is honest SKIP.
Product G4 close stays NOT_CLAIMED. Does not DROP the hot table.

  --out DIR           Output directory (default: .local/g4-hot-purge-90d-honesty/<utc>)
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
raise SystemExit(0)
PY
}

verify_report() {
  local file="$1"
  [ -f "$file" ] || fail "report not found: $file"
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used window_policy hot_retention_days archive_retention_days hot_table_dropped live_db_present live_jar_present live_minio_present live_purge_observed live_purge_window live_purge_window_skip_reason live_quartz_observed live_quartz live_quartz_skip_reason live_minio_cold_observed live_minio_cold live_minio_cold_skip_reason g4_close g18_status audit_port_changed p0_m0_redone g4_123_136_147_152_156_166_redone"
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
  [ "$(report_get "$file" hot_table_dropped)" = "false" ] || fail "hot_table_dropped must be false"
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || fail "p0_m0_redone must be false"
  [ "$(report_get "$file" g4_123_136_147_152_156_166_redone)" = "false" ] || fail "must not redo #123/#136/#147/#152/#156/#166"
  [ "$(report_get "$file" g4_close)" = "NOT_CLAIMED" ] || fail "g4_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" window_policy)" = "PASS" ] || fail "window_policy must be PASS"
  [ "$(report_get "$file" hot_retention_days)" = "90" ] || fail "hot_retention_days must be 90"
  [ "$(report_get "$file" archive_retention_days)" = "365" ] || fail "archive_retention_days must be 365"

  local verdict
  for verdict in live_purge_window live_quartz live_minio_cold; do
    local value
    value="$(report_get "$file" "$verdict")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) fail "$verdict must be PASS/SKIP/FAIL" ;;
    esac
  done

  if [ "$(report_get "$file" live_purge_window)" = "PASS" ]; then
    [ "$(report_get "$file" live_db_present)" = "true" ] || fail "live_purge_window=PASS requires live_db_present=true"
    [ "$(report_get "$file" live_jar_present)" = "true" ] || fail "live_purge_window=PASS requires live_jar_present=true"
    [ "$(report_get "$file" live_purge_observed)" = "true" ] || fail "live_purge_window=PASS requires live_purge_observed=true"
  fi
  if [ "$(report_get "$file" live_quartz)" = "PASS" ]; then
    [ "$(report_get "$file" live_quartz_observed)" = "true" ] || fail "live_quartz=PASS requires live_quartz_observed=true"
  fi
  if [ "$(report_get "$file" live_minio_cold)" = "PASS" ]; then
    [ "$(report_get "$file" live_minio_present)" = "true" ] || fail "live_minio_cold=PASS requires live_minio_present=true"
    [ "$(report_get "$file" live_minio_cold_observed)" = "true" ] || fail "live_minio_cold=PASS requires live_minio_cold_observed=true"
  fi
  if [ "$(report_get "$file" live_purge_window)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_purge_window_skip_reason)" ] || fail "live purge SKIP needs live_purge_window_skip_reason"
    [ "$(report_get "$file" live_purge_observed)" != "true" ] || fail "SKIP cannot claim a live purge walk"
  fi
  if [ "$(report_get "$file" live_quartz)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_quartz_skip_reason)" ] || fail "live Quartz SKIP needs live_quartz_skip_reason"
    [ "$(report_get "$file" live_quartz_observed)" != "true" ] || fail "SKIP cannot claim a live Quartz walk"
  fi
  if [ "$(report_get "$file" live_minio_cold)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_minio_cold_skip_reason)" ] || fail "live MinIO SKIP needs live_minio_cold_skip_reason"
    [ "$(report_get "$file" live_minio_cold_observed)" != "true" ] || fail "SKIP cannot claim a live MinIO cold walk"
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=' "$file"; then
    fail "report must not contain password/secret material"
  fi
  echo "PASS: report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g4-hot-purge-90d-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G4 90-day hot-purge window honesty leftover"
echo "cwd=$ROOT"
echo "Does not redo #123/#136/#147/#152/#156/#166."
echo "Does not DROP platform.audit_record."
echo "Not claimed: product G4 close / live :5432 prefix DELETE / live Quartz / live MinIO"
echo

JAVA_FEATURE=missing
if command -v java >/dev/null 2>&1; then
  JAVA_FEATURE="$(java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*java\.specification\.version[[:space:]]*=/{print $3; exit}')"
  java -version 2>&1 | head -n 1 | tee -a "$OUT/collect.log"
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
if [ "$BOOT_GROUP" = "org.springframework.boot" ] && [ "$BOOT_ARTIFACT" = "spring-boot-starter-parent" ] && [ "$BOOT_VERSION" = "4.1.1" ]; then
  BOOT_PARENT_MATCH=PASS
else
  BOOT_PARENT_MATCH=FAIL
fi
[ "$BOOT_PARENT_MATCH" = "PASS" ] || fail "boot parent must be org.springframework.boot:spring-boot-starter-parent:4.1.1"

DOCKER_USED=false
if command -v docker >/dev/null 2>&1; then
  log "docker binary present but leftover must not use it"
fi

LIVE_DB=false
LIVE_JAR=false
LIVE_MINIO=false
if port_open 127.0.0.1 5432; then LIVE_DB=true; fi
if port_open 127.0.0.1 8080; then LIVE_JAR=true; fi
if port_open 127.0.0.1 9000; then LIVE_MINIO=true; fi

PURGE_REASON="no live PostgreSQL on 127.0.0.1:5432 and no product JAR on 127.0.0.1:8080; Zonky #123 is not a live purge PASS; Docker forbidden"
if [ "${NEXUS_G4_HOT_PURGE_SKIP_LIVE:-}" = "1" ]; then
  PURGE_REASON="NEXUS_G4_HOT_PURGE_SKIP_LIVE=1; live :5432 / JAR purge window not walked; Zonky #123 is not a live purge PASS; Docker forbidden"
elif [ "$LIVE_DB" = "true" ] && [ "$LIVE_JAR" = "true" ]; then
  PURGE_REASON="live :5432 and JAR listeners present but a live 90-day prefix DELETE was not observed; Zonky #123 is not a live purge PASS; Docker forbidden"
elif [ "$LIVE_DB" = "true" ]; then
  PURGE_REASON="PostgreSQL listener present on 127.0.0.1:5432 but no product JAR on 127.0.0.1:8080; live purge Job not observed; Docker forbidden"
elif [ "$LIVE_JAR" = "true" ]; then
  PURGE_REASON="product JAR may be present but no live PostgreSQL on 127.0.0.1:5432; live 90-day prefix DELETE not observed; Docker forbidden"
fi

QUARTZ_REASON="nexus.audit.hot-purge.schedule-enabled defaults false; live Quartz 03:00 UTC walk not observed; IsolatedQuartzJob is tenant-only"
if [ "$LIVE_MINIO" = "true" ]; then
  MINIO_REASON="MinIO listener present on 127.0.0.1:9000 but a live cold-copy drill was not observed"
else
  MINIO_REASON="no live MinIO on 127.0.0.1:9000; fake / counting storage is not a live cold drill"
fi

TEST_SUMMARY=""
if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl nexus-audit-core -am test \
    -Dtest=G4HotPurge90dHonestyTest \
    -Dsurefire.failIfNoSpecifiedTests=false | tee "$OUT/mvn.log"
  MVN_RC=${PIPESTATUS[0]}
  set -e
  [ "$MVN_RC" -eq 0 ] || fail "mvn leftover tests failed"
  TEST_SUMMARY="$(awk '/Tests run:/{line=$0} END{print line}' "$OUT/mvn.log")"
else
  TEST_SUMMARY="skipped (--skip-tests)"
fi

{
  kv schema_version 1
  kv timestamp_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  kv java_feature "$JAVA_FEATURE"
  kv boot_parent_version "$BOOT_VERSION"
  kv boot_parent_match "$BOOT_PARENT_MATCH"
  kv docker_used "$DOCKER_USED"
  kv window_policy PASS
  kv hot_retention_days 90
  kv archive_retention_days 365
  kv hot_table_dropped false
  kv live_db_present "$LIVE_DB"
  kv live_jar_present "$LIVE_JAR"
  kv live_minio_present "$LIVE_MINIO"
  kv live_purge_observed false
  kv live_purge_window SKIP
  kv live_purge_window_skip_reason "$PURGE_REASON"
  kv live_quartz_observed false
  kv live_quartz SKIP
  kv live_quartz_skip_reason "$QUARTZ_REASON"
  kv live_minio_cold_observed false
  kv live_minio_cold SKIP
  kv live_minio_cold_skip_reason "$MINIO_REASON"
  kv g4_close NOT_CLAIMED
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
  kv g4_123_136_147_152_156_166_redone false
  kv test_summary "$TEST_SUMMARY"
} > "$OUT/report.env"

{
  echo "# G4 90-day hot-purge window honesty"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| window policy (90d / 365d, no DROP) | PASS |"
  echo "| live :5432 / JAR purge window | SKIP |"
  echo "| live Quartz | SKIP |"
  echo "| live MinIO cold | SKIP |"
  echo "| product G4 close | NOT_CLAIMED |"
  echo
  echo "Live purge reason: $PURGE_REASON"
  echo "Quartz reason: $QUARTZ_REASON"
  echo "MinIO reason: $MINIO_REASON"
} > "$OUT/matrix.md"

verify_report "$OUT/report.env"
log "wrote $OUT/report.env"
echo
echo "PASS: honesty leftover recorded (window policy PASS; live purge / Quartz / MinIO SKIP)"
echo "See docs/g4-hot-purge-90d-honesty.md"
echo "Keep both leftover rows: docs/g4-hot-purge-90d.md and docs/g4-hot-purge-90d-honesty.md"
echo "Not claimed: product G4 close / live :5432 prefix DELETE / live Quartz / live MinIO"
echo "out=$OUT"
