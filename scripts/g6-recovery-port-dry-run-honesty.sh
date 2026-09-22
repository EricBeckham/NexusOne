#!/usr/bin/env bash
# G6 leftover: RecoveryPort still dry-run / not product HA honesty beyond #135/#164.
# Does not redo #135 dry-run contract or #164 honesty leftover.
# Does not mutate a product database. Does not touch haopan_platform.
# Does not claim product G6 / RecoveryPort / minute-level RPO / product HA.
# G18 stays closed. P0/M0 not redone. No Docker.
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
  scripts/g6-recovery-port-dry-run-honesty.sh [--out DIR] [--skip-tests]
  scripts/g6-recovery-port-dry-run-honesty.sh --verify-report FILE

Dry-run contract stays always-green PASS. Minute-level RPO / product HA /
executing restore are PASS only when actually observed. Missing WAL /
standby / restore walk is honest SKIP. Product G6 / HA close stays
NOT_CLAIMED. Both leftover rows stay in docs/README.md.

  --out DIR           Output directory (default: .local/g6-recovery-port-dry-run-honesty/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used dry_run_contract recovery_port_executed profile_accepted dry_run_is_not_product_ha dry_run_is_not_minute_rpo leftover_rows_kept haopan_platform_touched product_db_mutated product_5432_targeted minute_rpo_observed minute_level_rpo minute_level_rpo_skip_reason product_ha_observed product_ha product_ha_skip_reason executing_restore_observed executing_restore executing_restore_skip_reason g6_close recovery_port_close g18_status audit_port_changed p0_m0_redone redone_135 redone_164"
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
  [ "$(report_get "$file" redone_135)" = "false" ] || fail "redone_135 must stay false"
  [ "$(report_get "$file" redone_164)" = "false" ] || fail "redone_164 must stay false"
  [ "$(report_get "$file" g6_close)" = "NOT_CLAIMED" ] || fail "g6_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" recovery_port_close)" = "NOT_CLAIMED" ] || fail "recovery_port_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" dry_run_contract)" = "PASS" ] || fail "dry_run_contract must be PASS"
  [ "$(report_get "$file" recovery_port_executed)" = "false" ] || fail "recovery_port_executed must stay false"
  [ "$(report_get "$file" profile_accepted)" = "false" ] || fail "profile_accepted must stay false"
  [ "$(report_get "$file" dry_run_is_not_product_ha)" = "true" ] || fail "dry_run_is_not_product_ha must be true"
  [ "$(report_get "$file" dry_run_is_not_minute_rpo)" = "true" ] || fail "dry_run_is_not_minute_rpo must be true"
  [ "$(report_get "$file" haopan_platform_touched)" = "false" ] || fail "haopan_platform_touched must stay false"
  [ "$(report_get "$file" product_db_mutated)" = "false" ] || fail "product_db_mutated must stay false"
  [ "$(report_get "$file" product_5432_targeted)" = "false" ] || fail "product_5432_targeted must stay false"
  local rows
  rows="$(report_get "$file" leftover_rows_kept)"
  echo "$rows" | grep -q 'g6-recovery-port-dry-run' || fail "leftover_rows_kept must keep g6-recovery-port-dry-run"
  echo "$rows" | grep -q 'g6-recovery-port-honesty' || fail "leftover_rows_kept must keep g6-recovery-port-honesty"

  local verdict
  for verdict in minute_level_rpo product_ha executing_restore; do
    local value
    value="$(report_get "$file" "$verdict")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) fail "$verdict must be PASS/SKIP/FAIL" ;;
    esac
  done

  if [ "$(report_get "$file" minute_level_rpo)" = "PASS" ]; then
    [ "$(report_get "$file" minute_rpo_observed)" = "true" ] || fail "minute_level_rpo=PASS requires minute_rpo_observed=true"
  fi
  if [ "$(report_get "$file" product_ha)" = "PASS" ]; then
    [ "$(report_get "$file" product_ha_observed)" = "true" ] || fail "product_ha=PASS requires product_ha_observed=true"
  fi
  if [ "$(report_get "$file" executing_restore)" = "PASS" ]; then
    [ "$(report_get "$file" executing_restore_observed)" = "true" ] || fail "executing_restore=PASS requires executing_restore_observed=true"
  fi
  if [ "$(report_get "$file" minute_level_rpo)" = "SKIP" ]; then
    [ -n "$(report_get "$file" minute_level_rpo_skip_reason)" ] || fail "minute-level RPO SKIP needs minute_level_rpo_skip_reason"
    [ "$(report_get "$file" minute_rpo_observed)" != "true" ] || fail "SKIP cannot claim a minute-level RPO walk"
  fi
  if [ "$(report_get "$file" product_ha)" = "SKIP" ]; then
    [ -n "$(report_get "$file" product_ha_skip_reason)" ] || fail "product HA SKIP needs product_ha_skip_reason"
    [ "$(report_get "$file" product_ha_observed)" != "true" ] || fail "SKIP cannot claim a product HA walk"
  fi
  if [ "$(report_get "$file" executing_restore)" = "SKIP" ]; then
    [ -n "$(report_get "$file" executing_restore_skip_reason)" ] || fail "executing restore SKIP needs executing_restore_skip_reason"
    [ "$(report_get "$file" executing_restore_observed)" != "true" ] || fail "SKIP cannot claim an executing restore walk"
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|TOKEN=' "$file"; then
    fail "report must not contain password/secret/token material"
  fi
  echo "PASS: report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g6-recovery-port-dry-run-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G6 RecoveryPort dry-run / not product HA leftover honesty"
echo "cwd=$ROOT"
echo "Does not redo #135/#164."
echo "Does not mutate product DB. Does not touch haopan_platform."
echo "Not claimed: product G6 close / RecoveryPort close / minute-level RPO / product HA"
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

README="$ROOT/docs/README.md"
grep -q '\[g6-recovery-port-dry-run\](\./g6-recovery-port-dry-run.md)' "$README" \
  || fail "docs/README.md must keep g6-recovery-port-dry-run leftover row"
grep -q '\[g6-recovery-port-honesty\](\./g6-recovery-port-honesty.md)' "$README" \
  || fail "docs/README.md must keep g6-recovery-port-honesty leftover row"

RPO_REASON="no WAL archive / streaming replica / delivery-grade RPO checklist observed; #135 dry-run and #164 honesty leftover are not minute-level RPO; haopan_platform / :5432 not touched; Docker forbidden"
HA_REASON="no failover / standby promotion / multi-AZ walk observed; #135 dry-run and #164 honesty leftover are not product HA; haopan_platform / :5432 not touched; Docker forbidden"
RESTORE_REASON="DryRunRecoveryPort still only probes --version; dump/restore was not executed; product DB not mutated; haopan_platform not touched"

TEST_SUMMARY=""
if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl nexus-audit-core -am test \
    -Dtest=G6RecoveryPortDryRunHonestyTest \
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
  kv dry_run_contract PASS
  kv recovery_port_executed false
  kv profile_accepted false
  kv dry_run_is_not_product_ha true
  kv dry_run_is_not_minute_rpo true
  kv leftover_rows_kept "g6-recovery-port-dry-run,g6-recovery-port-honesty"
  kv haopan_platform_touched false
  kv product_db_mutated false
  kv product_5432_targeted false
  kv minute_rpo_observed false
  kv minute_level_rpo SKIP
  kv minute_level_rpo_skip_reason "$RPO_REASON"
  kv product_ha_observed false
  kv product_ha SKIP
  kv product_ha_skip_reason "$HA_REASON"
  kv executing_restore_observed false
  kv executing_restore SKIP
  kv executing_restore_skip_reason "$RESTORE_REASON"
  kv g6_close NOT_CLAIMED
  kv recovery_port_close NOT_CLAIMED
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
  kv redone_135 false
  kv redone_164 false
  kv test_summary "$TEST_SUMMARY"
} > "$OUT/report.env"

{
  echo "# G6 RecoveryPort dry-run / not product HA honesty"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| dry-run contract | PASS |"
  echo "| minute-level RPO | SKIP |"
  echo "| product HA | SKIP |"
  echo "| executing restore | SKIP |"
  echo "| product G6 / RecoveryPort close | NOT_CLAIMED |"
  echo
  echo "RPO reason: $RPO_REASON"
  echo "HA reason: $HA_REASON"
  echo "Restore reason: $RESTORE_REASON"
} > "$OUT/matrix.md"

verify_report "$OUT/report.env"
log "wrote $OUT/report.env"
echo
echo "PASS: honesty leftover recorded (minute-level RPO / product HA / executing restore SKIP)"
echo "See docs/g6-recovery-port-dry-run-honesty.md"
echo "Keep both leftover rows: docs/g6-recovery-port-dry-run.md and docs/g6-recovery-port-honesty.md"
echo "Not claimed: product G6 close / RecoveryPort close / minute-level RPO / product HA"
echo "Not redone: #135 dry-run contract / #164 honesty leftover / P0/M0"
echo "Not touched: haopan_platform / product DB / :5432"
echo "out=$OUT"
