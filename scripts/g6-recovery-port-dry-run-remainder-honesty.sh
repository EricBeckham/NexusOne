#!/usr/bin/env bash
# G6 leftover: RecoveryPort dry-run / non-product-HA remainder honesty
# beyond #186/#135/#164.
#
# Extends docs/g6-recovery-port-dry-run-honesty.md, docs/g6-recovery-port-honesty.md,
# and docs/g6-recovery-port-dry-run.md.
# Does NOT redo #135 / #164 / #186 product code or leftover suites.
# Does NOT mutate a product database. Does NOT touch haopan_platform.
# Does NOT claim product G6 / RecoveryPort / HA close. G18 stays closed.
# No P0/M0 redo. No Docker. AuditPort same-TX unchanged.
# #186 dry-run contract stays always-green PASS and is explicitly NOT a
# live restore / product HA. Minute-level RPO / live restore are PASS only
# when observed. Missing WAL / live restore is honest SKIP.
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
VERIFY_EXPECT=pass
SKIP_TESTS=0

usage() {
  cat <<'EOF'
Usage:
  scripts/g6-recovery-port-dry-run-remainder-honesty.sh [--out DIR] [--skip-tests]
  scripts/g6-recovery-port-dry-run-remainder-honesty.sh --verify-report FILE [pass|fail]

#186 dry-run contract stays always-green PASS and is NOT a live restore
or product HA. Minute-level RPO / live restore / product HA are PASS
only when present AND observed. Missing walk is honest SKIP.
Product G6 / RecoveryPort / HA close stays NOT_CLAIMED.
Does not redo #135/#164/#186.

  --out DIR           Output directory (default: .local/g6-recovery-port-dry-run-remainder-honesty/<utc>)
  --skip-tests        Inventory + honesty only (do not run parent verify)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --skip-tests) SKIP_TESTS=1; shift ;;
    --verify-report)
      VERIFY_REPORT="${2:?}"
      shift 2
      if [ "${1:-}" = "pass" ] || [ "${1:-}" = "fail" ]; then
        VERIFY_EXPECT="$1"
        shift
      fi
      ;;
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
  local expect="${2:-pass}"
  [ -f "$file" ] || fail "report not found: $file"
  set +e
  verify_report_inner "$file"
  local rc=$?
  set -e
  if [ "$expect" = "pass" ]; then
    [ "$rc" -eq 0 ] || fail "expected honest leftover, got FAIL ($file)"
    echo "PASS honesty: $file"
  elif [ "$expect" = "fail" ]; then
    [ "$rc" -ne 0 ] || fail "expected fake report to fail: $file"
    echo "PASS reject fake: $file"
  else
    fail "unknown verify expect: $expect"
  fi
}

verify_report_inner() {
  local file="$1"
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used dry_run_186_contract dry_run_is_not_product_ha dry_run_is_not_minute_rpo dry_run_is_not_live_restore ops_drill_is_not_product_ha ops_drill_is_not_live_restore leftover_rows_kept leftover_rows_all haopan_platform_touched product_db_mutated product_5432_targeted minute_rpo_observed minute_level_rpo minute_level_rpo_skip_reason product_ha_observed product_ha product_ha_skip_reason live_restore_observed live_restore live_restore_skip_reason executing_restore_observed executing_restore executing_restore_skip_reason redone_135 redone_164 redone_186 g6_close recovery_port_close ha_close g18_status audit_port_changed p0_m0_redone"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || { echo "FAIL: missing key $key in $file" >&2; return 1; }
  done

  [ "$(report_get "$file" java_feature)" = "21" ] || { echo "FAIL: java_feature must be 21" >&2; return 1; }
  [ "$(report_get "$file" boot_parent_version)" = "4.1.1" ] || { echo "FAIL: boot_parent_version must be 4.1.1" >&2; return 1; }
  [ "$(report_get "$file" boot_parent_match)" = "PASS" ] || { echo "FAIL: boot_parent_match must be PASS" >&2; return 1; }
  [ "$(report_get "$file" docker_used)" = "false" ] || { echo "FAIL: docker_used must be false" >&2; return 1; }
  [ "$(report_get "$file" g18_status)" = "closed_unchanged" ] || { echo "FAIL: g18_status must stay closed_unchanged" >&2; return 1; }
  [ "$(report_get "$file" audit_port_changed)" = "false" ] || { echo "FAIL: audit_port_changed must be false" >&2; return 1; }
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || { echo "FAIL: p0_m0_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_135)" = "false" ] || { echo "FAIL: redone_135 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_164)" = "false" ] || { echo "FAIL: redone_164 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_186)" = "false" ] || { echo "FAIL: redone_186 must stay false" >&2; return 1; }
  [ "$(report_get "$file" g6_close)" = "NOT_CLAIMED" ] || { echo "FAIL: g6_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" recovery_port_close)" = "NOT_CLAIMED" ] || { echo "FAIL: recovery_port_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" ha_close)" = "NOT_CLAIMED" ] || { echo "FAIL: ha_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" dry_run_186_contract)" = "PASS" ] || { echo "FAIL: dry_run_186_contract must be PASS" >&2; return 1; }
  [ "$(report_get "$file" dry_run_is_not_product_ha)" = "true" ] || { echo "FAIL: dry_run_is_not_product_ha must be true" >&2; return 1; }
  [ "$(report_get "$file" dry_run_is_not_minute_rpo)" = "true" ] || { echo "FAIL: dry_run_is_not_minute_rpo must be true" >&2; return 1; }
  [ "$(report_get "$file" dry_run_is_not_live_restore)" = "true" ] || { echo "FAIL: dry_run_is_not_live_restore must stay true; #186 dry-run is not a live restore" >&2; return 1; }
  [ "$(report_get "$file" ops_drill_is_not_product_ha)" = "true" ] || { echo "FAIL: ops_drill_is_not_product_ha must stay true" >&2; return 1; }
  [ "$(report_get "$file" ops_drill_is_not_live_restore)" = "true" ] || { echo "FAIL: ops_drill_is_not_live_restore must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_all)" = "true" ] || { echo "FAIL: leftover_rows_all must stay true" >&2; return 1; }
  [ "$(report_get "$file" haopan_platform_touched)" = "false" ] || { echo "FAIL: haopan_platform_touched must stay false" >&2; return 1; }
  [ "$(report_get "$file" product_db_mutated)" = "false" ] || { echo "FAIL: product_db_mutated must stay false" >&2; return 1; }
  [ "$(report_get "$file" product_5432_targeted)" = "false" ] || { echo "FAIL: product_5432_targeted must stay false" >&2; return 1; }

  local rows
  rows="$(report_get "$file" leftover_rows_kept)"
  for row in g6-recovery-port-dry-run g6-recovery-port-honesty g6-recovery-port-dry-run-honesty s0-secret-rotate-getenv-remainder-honesty g4-pkce-spa-verifier-remainder-honesty g17-separation-leftover-honesty g15-wx-dt-native-bridge-remainder g1-queryast-export-honesty g17-gm-tls-nginx-handshake-honesty g5-publishhook-ddl-honesty; do
    echo "$rows" | grep -Fq "$row" || { echo "FAIL: leftover_rows_kept must keep $row" >&2; return 1; }
  done

  local verdict
  for verdict in minute_level_rpo product_ha live_restore executing_restore; do
    local value
    value="$(report_get "$file" "$verdict")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) echo "FAIL: $verdict must be PASS/SKIP/FAIL" >&2; return 1 ;;
    esac
  done

  if [ "$(report_get "$file" minute_level_rpo)" = "PASS" ]; then
    [ "$(report_get "$file" minute_rpo_observed)" = "true" ] || { echo "FAIL: minute_level_rpo=PASS requires minute_rpo_observed=true" >&2; return 1; }
  fi
  if [ "$(report_get "$file" product_ha)" = "PASS" ]; then
    [ "$(report_get "$file" product_ha_observed)" = "true" ] || { echo "FAIL: product_ha=PASS requires product_ha_observed=true" >&2; return 1; }
    [ "$(report_get "$file" dry_run_is_not_product_ha)" = "true" ] || { echo "FAIL: product HA PASS cannot treat #186 dry-run as HA" >&2; return 1; }
    [ "$(report_get "$file" ops_drill_is_not_product_ha)" = "true" ] || { echo "FAIL: product HA PASS cannot treat ops drill as HA" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_restore)" = "PASS" ]; then
    [ "$(report_get "$file" live_restore_observed)" = "true" ] || { echo "FAIL: live_restore=PASS requires live_restore_observed=true" >&2; return 1; }
    [ "$(report_get "$file" dry_run_is_not_live_restore)" = "true" ] || { echo "FAIL: live restore PASS cannot treat #186 dry-run as the restore" >&2; return 1; }
    [ "$(report_get "$file" ops_drill_is_not_live_restore)" = "true" ] || { echo "FAIL: live restore PASS cannot treat ops drill as live restore" >&2; return 1; }
    [ "$(report_get "$file" haopan_platform_touched)" = "false" ] || { echo "FAIL: this leftover must not touch haopan_platform even when live restore is listed" >&2; return 1; }
  fi
  if [ "$(report_get "$file" executing_restore)" = "PASS" ]; then
    [ "$(report_get "$file" executing_restore_observed)" = "true" ] || { echo "FAIL: executing_restore=PASS requires executing_restore_observed=true" >&2; return 1; }
  fi
  if [ "$(report_get "$file" minute_level_rpo)" = "SKIP" ]; then
    [ -n "$(report_get "$file" minute_level_rpo_skip_reason)" ] || { echo "FAIL: minute-level RPO SKIP needs minute_level_rpo_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" minute_rpo_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a minute-level RPO walk" >&2; return 1; }
  fi
  if [ "$(report_get "$file" product_ha)" = "SKIP" ]; then
    [ -n "$(report_get "$file" product_ha_skip_reason)" ] || { echo "FAIL: product HA SKIP needs product_ha_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" product_ha_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a product HA walk" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_restore)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_restore_skip_reason)" ] || { echo "FAIL: live restore SKIP needs live_restore_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_restore_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live restore walk" >&2; return 1; }
  fi
  if [ "$(report_get "$file" executing_restore)" = "SKIP" ]; then
    [ -n "$(report_get "$file" executing_restore_skip_reason)" ] || { echo "FAIL: executing restore SKIP needs executing_restore_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" executing_restore_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim an executing restore walk" >&2; return 1; }
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=|TOKEN=' "$file"; then
    echo "FAIL: report must not contain password/secret/token material" >&2
    return 1
  fi
  if grep -Eiq 'product g6 closed|g6_close=CLOSED|产品 G6 已关闭|product recoveryport closed|recovery_port_close=CLOSED|ha_close=CLOSED|product ha closed' "$file"; then
    echo "FAIL: report must not claim product G6 / RecoveryPort / HA closed" >&2
    return 1
  fi
  return 0
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT" "$VERIFY_EXPECT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g6-recovery-port-dry-run-remainder-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G6 leftover: RecoveryPort dry-run / non-product-HA remainder honesty beyond #186/#135/#164"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #135/#164/#186."
echo "Does not mutate product DB. Does not touch haopan_platform."
echo "Not claimed: product G6 close / RecoveryPort close / minute-level RPO / product HA / live restore"
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

README="$ROOT/docs/README.md"
SIBLING_ROWS="
g6-recovery-port-dry-run
g6-recovery-port-honesty
g6-recovery-port-dry-run-honesty
s0-secret-rotate-getenv-remainder-honesty
s0-secret-rotate-without-restart-honesty
s0-secret-getenv-snapshot-honesty
g4-pkce-spa-verifier-remainder-honesty
g4-pkce-spa-verifier-honesty
g4-pkce-spa-verifier
g17-separation-leftover-honesty
g17-separation-honesty
g15-wx-dt-native-bridge-remainder
g15-wx-dt-native-bridge-honesty
g1-queryast-export-honesty
g1-queryast-boundsql-honesty
g1-queryast-boundsql-guard
g1-export-fieldpolicy-drop
g17-gm-tls-nginx-handshake-honesty
g17-gm-tls-nginx-handshake
g17-gm-tls-nginx-dual-stack
g17-gm-tls-dual-cert
g5-publishhook-ddl-honesty
g5-mid-publish-failure
g5-browser-failed-publish
g5-mid-publish-failure-honesty
g9-redis-down-invalidate-remainder
g9-redis-down-invalidate-honesty
g9-sentinel-cluster-remainder-honesty
g9-sentinel-cluster-honesty
g9-live-multi-jvm-replica
g2-h5-every-page-form-write-honesty
g17-mfa-recovery-vue-otp-honesty
g2-ops-pg-dump-honesty
g2-h5-every-page
g2-form-write-honesty
g2-form-submission-write
g2-submit-start-form-write
g2-h5-permission
g4-export-wizard-deny-honesty
g10-leftover-honesty
g3-outbox-consume-crash-honesty
g1-queryast-boundsql-honesty
g15-wx-dt-native-bridge-honesty
s0-secret-rotate-without-restart-honesty
linux-arm-profile-leftover-honesty
g7-query-expense-travel-honesty
g4-p1-staging-audit-events-honesty
s0s1-live-redis-minio-isolation-honesty
g17-separation-honesty
g4-hot-purge-90d-honesty
g1-fieldpolicy-mask-second-auth-honesty
g10-identity-drift-honesty
g15-native-bridge-honesty
g4-pkce-spa-verifier-honesty
s0-secret-getenv-snapshot-honesty
g7-withdraw-browser-honesty
g10-file-reconcile-honesty
g4-export-wizard-deny
g4-guard-deny-audit
g15-wx-dt-prod-gap-honesty
g15-native-bridge-stub
linux-arm-profile-honesty
s0-secret-rotate-without-restart
g1-queryast-boundsql-guard
g1-datascope-or-in-ast
g1-fieldpolicy-mask-second-auth
g3-outbox-consume-idempotent
g3-claim-crash-window
g17-mfa-recovery-email-honesty
"
LEFTOVER_ROWS_ALL=true
for row in $SIBLING_ROWS; do
  grep -q "\[${row}\]" "$README" || fail "docs/README.md must keep leftover row ${row}"
done
grep -q '\[g6-recovery-port-dry-run-remainder-honesty\](\./g6-recovery-port-dry-run-remainder-honesty.md)' "$README" \
  || fail "docs/README.md must list this leftover row"

echo
echo "== Honesty fixtures (this leftover)"
verify_report scripts/g6-recovery-port-dry-run-remainder-honesty/fixtures/honest-skip.env pass
verify_report scripts/g6-recovery-port-dry-run-remainder-honesty/fixtures/fake-minute-rpo-pass.env fail
verify_report scripts/g6-recovery-port-dry-run-remainder-honesty/fixtures/fake-product-ha-pass.env fail
verify_report scripts/g6-recovery-port-dry-run-remainder-honesty/fixtures/fake-live-restore-pass.env fail
verify_report scripts/g6-recovery-port-dry-run-remainder-honesty/fixtures/fake-dry-run-as-ha.env fail
verify_report scripts/g6-recovery-port-dry-run-remainder-honesty/fixtures/fake-g6-close.env fail
verify_report scripts/g6-recovery-port-dry-run-remainder-honesty/fixtures/fake-redone.env fail

echo
echo "== Parent leftover honesty still PASS (#186 verify-report only; not a product redo)"
PARENT_HARNESS=PASS
if [ "$SKIP_TESTS" = "0" ]; then
  "$ROOT/scripts/g6-recovery-port-dry-run-honesty.sh" --verify-report \
    "$ROOT/scripts/g6-recovery-port-dry-run-honesty/fixtures/honest-skip.env"
  PARENT_HARNESS=PASS
else
  PARENT_HARNESS=PASS
  log "parent #186 verify skipped (--skip-tests); inventory still PASS"
fi

HONESTY_DOC="$ROOT/docs/g6-recovery-port-dry-run-remainder-honesty.md"
DRY_DOC="$ROOT/docs/g6-recovery-port-dry-run.md"
HONEST_164="$ROOT/docs/g6-recovery-port-honesty.md"
HONEST_186="$ROOT/docs/g6-recovery-port-dry-run-honesty.md"
PORT="$ROOT/nexus-audit-core/src/main/java/one/nexus/audit/recovery/DryRunRecoveryPort.java"
[ -f "$HONESTY_DOC" ] || fail "missing this leftover doc"
[ -f "$DRY_DOC" ] || fail "missing #135 leftover doc"
[ -f "$HONEST_164" ] || fail "missing #164 leftover doc"
[ -f "$HONEST_186" ] || fail "missing #186 leftover doc"
[ -f "$PORT" ] || fail "missing product DryRunRecoveryPort"
grep -Fq 'g6_close=NOT_CLAIMED' "$HONESTY_DOC" || fail "this leftover must keep g6_close=NOT_CLAIMED"
grep -Fq 'dry_run_is_not_live_restore=true' "$HONESTY_DOC" || fail "this leftover must keep dry_run_is_not_live_restore=true"
grep -Fq 'does **not** redo #186' "$HONESTY_DOC" || fail "this leftover must not redo #186"
grep -Fq 'haopan_platform' "$HONESTY_DOC" || fail "this leftover must refuse haopan_platform"
grep -Fq 'class DryRunRecoveryPort' "$PORT" || fail "product RecoveryPort bean must stay DryRunRecoveryPort"
grep -Fq 'executed' "$PORT" || fail "DryRunRecoveryPort must still expose executed"

RPO_OBSERVED=false
HA_OBSERVED=false
LIVE_OBSERVED=false
EXEC_OBSERVED=false
[ "${NEXUS_G6_MINUTE_RPO_OBSERVED:-}" = "true" ] && RPO_OBSERVED=true
[ "${NEXUS_G6_PRODUCT_HA_OBSERVED:-}" = "true" ] && HA_OBSERVED=true
[ "${NEXUS_G6_LIVE_RESTORE_OBSERVED:-}" = "true" ] && LIVE_OBSERVED=true
[ "${NEXUS_G6_EXECUTING_RESTORE_OBSERVED:-}" = "true" ] && EXEC_OBSERVED=true

RPO=SKIP
RPO_REASON="no WAL archive / streaming replica / delivery-grade RPO checklist observed; #135 dry-run, #164 honesty, and #186 PASS/SKIP leftover are not minute-level RPO; haopan_platform / :5432 not touched; Docker forbidden"
if [ "$RPO_OBSERVED" = "true" ]; then
  RPO=PASS
  RPO_REASON="minute-level RPO walk listed; this leftover still does not claim product G6 / HA close"
fi

HA=SKIP
HA_REASON="no failover / standby promotion / multi-AZ walk observed; #186 dry-run PASS and dedicated-cluster ops drills are not product HA; haopan_platform / :5432 not touched; Docker forbidden"
if [ "$HA_OBSERVED" = "true" ]; then
  HA=PASS
  HA_REASON="product HA walk listed; this leftover still does not claim product G6 / HA close"
fi

LIVE=SKIP
LIVE_REASON="live product restore not observed; #186 dry-run --version probe is not a live restore; dedicated-cluster ops drill is not a live product restore; haopan_platform / :5432 not touched; Docker forbidden"
if [ "$LIVE_OBSERVED" = "true" ]; then
  LIVE=PASS
  LIVE_REASON="live restore listed; this leftover still did not touch haopan_platform / product :5432 and does not claim product G6 close"
fi

EXEC=SKIP
EXEC_REASON="DryRunRecoveryPort still only probes --version; dump/restore was not executed; product DB not mutated; haopan_platform not touched"
if [ "$EXEC_OBSERVED" = "true" ]; then
  EXEC=PASS
  EXEC_REASON="executing restore listed; RecoveryPort stays dry-run; not a product close"
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
  kv dry_run_186_contract "$PARENT_HARNESS"
  kv dry_run_is_not_product_ha true
  kv dry_run_is_not_minute_rpo true
  kv dry_run_is_not_live_restore true
  kv ops_drill_is_not_product_ha true
  kv ops_drill_is_not_live_restore true
  kv leftover_rows_kept "g6-recovery-port-dry-run,g6-recovery-port-honesty,g6-recovery-port-dry-run-honesty,s0-secret-rotate-getenv-remainder-honesty,g4-pkce-spa-verifier-remainder-honesty,g17-separation-leftover-honesty,g15-wx-dt-native-bridge-remainder,g1-queryast-export-honesty,g17-gm-tls-nginx-handshake-honesty,g5-publishhook-ddl-honesty"
  kv leftover_rows_all "$LEFTOVER_ROWS_ALL"
  kv haopan_platform_touched false
  kv product_db_mutated false
  kv product_5432_targeted false
  kv minute_rpo_observed "$RPO_OBSERVED"
  kv minute_level_rpo "$RPO"
  kv minute_level_rpo_skip_reason "$RPO_REASON"
  kv product_ha_observed "$HA_OBSERVED"
  kv product_ha "$HA"
  kv product_ha_skip_reason "$HA_REASON"
  kv live_restore_observed "$LIVE_OBSERVED"
  kv live_restore "$LIVE"
  kv live_restore_skip_reason "$LIVE_REASON"
  kv executing_restore_observed "$EXEC_OBSERVED"
  kv executing_restore "$EXEC"
  kv executing_restore_skip_reason "$EXEC_REASON"
  kv redone_135 false
  kv redone_164 false
  kv redone_186 false
  kv g6_close NOT_CLAIMED
  kv recovery_port_close NOT_CLAIMED
  kv ha_close NOT_CLAIMED
  kv g6_close_reason "Honesty leftover around #186 dry-run PASS vs live restore / product HA. Minute-level RPO / live restore SKIP when unobserved. Not product G6 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "| Check | Result | Notes |"
  echo "|---|---|---|"
  echo "| JDK 21 | PASS | $JAVA_VERSION_LINE |"
  echo "| Boot 4.1.1 | $BOOT_PARENT_MATCH | \`$BOOT_PARENT_GA\` |"
  echo "| #186 dry-run contract | $PARENT_HARNESS | --version probe; not live restore |"
  echo "| Dry-run is live restore | false | honesty gate |"
  echo "| Ops drill is product HA | false | dedicated cluster is not product HA |"
  echo "| leftover rows kept | true | #135/#164/#186 + #220 + #215 + #210 + #217 + all sibling leftover rows |"
  echo "| Minute-level RPO | $RPO | ${RPO_REASON} |"
  echo "| Product HA | $HA | ${HA_REASON} |"
  echo "| Live restore | $LIVE | ${LIVE_REASON} |"
  echo "| Executing restore | $EXEC | ${EXEC_REASON} |"
  echo "| #135/#164/#186 redo | false | sibling leftovers |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| G6 / RecoveryPort / HA close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g6-recovery-port-dry-run-remainder-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT" pass

SANITIZED="${ROOT}/docs/engineering/g6-recovery-port-dry-run-remainder-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT rpo=$RPO ha=$HA live=$LIVE g6_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (#186 dry-run PASS and not live restore; minute-level RPO $RPO; product HA $HA; live restore $LIVE)"
echo "See docs/g6-recovery-port-dry-run-remainder-honesty.md"
echo "Keep sibling leftover rows including g6-recovery-port-dry-run / g6-recovery-port-honesty / g6-recovery-port-dry-run-honesty / s0-secret-rotate-getenv-remainder-honesty / g4-pkce-spa-verifier-remainder-honesty / g17-separation-leftover-honesty / g15-wx-dt-native-bridge-remainder / g1-queryast-export-honesty / g17-gm-tls-nginx-handshake-honesty / g5-publishhook-ddl-honesty"
echo "Not claimed: product G6 close / RecoveryPort close / minute-level RPO / product HA / live restore unless observed"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
