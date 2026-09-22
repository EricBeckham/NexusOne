#!/usr/bin/env bash
# G17 leftover: 三权分立 admin-force vs dual-control honesty beyond #99/#187.
#
# Extends docs/g17-separation-of-duties.md and docs/g17-separation-honesty.md.
# After G2+G4 on main (including #204 H5/form.write, #205 MFA recovery/OTP,
# #188 ops pg_dump honesty, #209 Sentinel/Cluster remainder, #217 PublishHook/DDL,
# #210 GMTLS handshake honesty, #215 BoundSql/export honesty,
# #220 wx-dt/native-bridge remainder honesty, #218 claim-list tip-sync).
# Does NOT redo #96/#99/#187 product code. Does NOT claim product G17 / SoD /
# dual-control / 等保 / 密评 close. G18 stays closed. No P0/M0 redo.
# AuditPort same-TX untouched. No Docker.
#
# #99 catalog exclusive pairs + #187 leftover cells stay always-green PASS.
# Admin force / dual-control (maker-checker) live walks are PASS only when
# a live product JAR + live IdP + observed walk exist. Missing live path is
# honest SKIP. It never fabricates PASS.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HERE="$(cd "$(dirname "$0")" && pwd)"
if [[ -x "${HOME}/tools/apache-maven-3.8.7/bin/mvn" ]]; then
  export PATH="${HOME}/tools/apache-maven-3.8.7/bin:${PATH}"
elif [[ -x "${HOME}/tools/apache-maven-3.9.11/bin/mvn" ]]; then
  export PATH="${HOME}/tools/apache-maven-3.9.11/bin:${PATH}"
fi
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
JSON_OUT="${NEXUS_G17_SOD_LEFTOVER_JSON:-$ROOT/docs/g17-separation-leftover-honesty/walk.json}"
VERIFY_REPORT=""
VERIFY_EXPECT=pass

ARTIFACT_187="$ROOT/docs/engineering/g17-separation-honesty/agent-vm.env"
README="$ROOT/docs/README.md"

REQUIRED_LEFTOVER_ROWS="g17-separation-of-duties g17-separation-honesty g17-separation-leftover-honesty g17-gm-tls-dual-cert g17-admin-totp-enrollment g17-totp-enrollment-ux g17-admin-mfa-recovery-email g17-mfa-recovery-email-honesty g17-vue-otp-writeback g17-mfa-recovery-vue-otp-honesty g17-gm-tls-nginx-dual-stack g17-gm-tls-nginx-handshake g17-gm-tls-nginx-handshake-honesty g2-ops-pg-dump-honesty g2-ops-pg-dump g2-h5-every-page-form-write-honesty g2-h5-every-page g2-form-write-honesty g2-form-submission-write g2-submit-start-form-write g2-h5-permission g4-export-wizard-deny-honesty g4-export-wizard-deny g4-guard-deny-audit g10-leftover-honesty g5-mid-publish-failure-honesty g5-publishhook-ddl-honesty g3-outbox-consume-crash-honesty g1-queryast-boundsql-honesty g1-queryast-export-honesty g15-wx-dt-native-bridge-honesty g15-wx-dt-native-bridge-remainder s0-secret-rotate-without-restart-honesty g7-query-expense-travel-honesty linux-arm-profile-leftover-honesty g4-p1-staging-audit-events-honesty g6-recovery-port-dry-run-honesty s0s1-live-redis-minio-isolation-honesty g4-hot-purge-90d-honesty g1-fieldpolicy-mask-second-auth-honesty g10-identity-drift-honesty g15-native-bridge-honesty g4-pkce-spa-verifier-honesty s0-secret-getenv-snapshot-honesty g5-browser-failed-publish g7-withdraw-browser-honesty g9-redis-down-invalidate-honesty g9-redis-down-invalidate-remainder g9-sentinel-cluster-remainder-honesty g9-sentinel-cluster-honesty"

usage() {
  cat <<'EOF'
Usage:
  scripts/g17-separation-leftover-honesty.sh [--out DIR]
  scripts/g17-separation-leftover-honesty.sh --verify-report FILE [pass|fail]

#99 catalog + #187 leftover cells stay always-green PASS.
Admin force / dual-control live walks are PASS only when live JAR + IdP
+ observed walk exist. Missing live path is honest SKIP.
Does not claim product G17 / SoD / dual-control close.

  --out DIR           Output directory (default: .local/g17-separation-leftover-honesty/<utc>)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
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

verify_report_inner() {
  local file="$1"
  [ -f "$file" ] || { echo "FAIL: report not found: $file" >&2; return 1; }
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_99_cells_honest leftover_187_cells_honest catalog_99_inventory exclusive_pairs_documented operator_not_platform_duty no_admin_force_bypass_api surfaceguard_still_required g4_deny_on_chain_unchanged same_tx_append_unchanged p1_all_demo_rewired p1_all_demo_status product_duty_ui product_duty_ui_status product_dual_control_ui product_dual_control_ui_status leftover_rows_kept leftover_rows leftover_rows_all leftover_99_kept leftover_187_kept leftover_197_kept leftover_204_kept leftover_205_kept leftover_188_kept leftover_209_kept leftover_217_kept leftover_210_kept leftover_215_kept leftover_220_kept live_jar_endpoint_set live_jar_listener live_jar_present live_idp_reachable live_idp_context_set oidc_discovery live_admin_force_walk live_admin_force_skip_reason live_admin_force_observed live_dual_control_walk live_dual_control_skip_reason live_dual_control_observed live_sod_walk dengbao_claimed mipin_claimed hsm_claimed g17_close g17_sod_close product_dual_control_close g18_status audit_port_changed p0_m0_redone redo_96 redo_99 redo_187"
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
  [ "$(report_get "$file" dengbao_claimed)" = "false" ] || { echo "FAIL: dengbao_claimed must stay false" >&2; return 1; }
  [ "$(report_get "$file" mipin_claimed)" = "false" ] || { echo "FAIL: mipin_claimed must stay false" >&2; return 1; }
  [ "$(report_get "$file" hsm_claimed)" = "false" ] || { echo "FAIL: hsm_claimed must stay false" >&2; return 1; }
  [ "$(report_get "$file" g17_close)" = "NOT_CLAIMED" ] || { echo "FAIL: g17_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" g17_sod_close)" = "NOT_CLAIMED" ] || { echo "FAIL: g17_sod_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" product_dual_control_close)" = "NOT_CLAIMED" ] || { echo "FAIL: product_dual_control_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" redo_96)" = "false" ] || { echo "FAIL: redo_96 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redo_99)" = "false" ] || { echo "FAIL: redo_99 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redo_187)" = "false" ] || { echo "FAIL: redo_187 must stay false" >&2; return 1; }
  [ "$(report_get "$file" leftover_99_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_99_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_187_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_187_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" catalog_99_inventory)" = "PASS" ] || { echo "FAIL: catalog_99_inventory must be PASS" >&2; return 1; }
  [ "$(report_get "$file" exclusive_pairs_documented)" = "true" ] || { echo "FAIL: exclusive_pairs_documented must be true" >&2; return 1; }
  [ "$(report_get "$file" operator_not_platform_duty)" = "true" ] || { echo "FAIL: operator_not_platform_duty must be true" >&2; return 1; }
  [ "$(report_get "$file" no_admin_force_bypass_api)" = "true" ] || { echo "FAIL: no_admin_force_bypass_api must be true" >&2; return 1; }
  [ "$(report_get "$file" surfaceguard_still_required)" = "PASS" ] || { echo "FAIL: surfaceguard_still_required must be PASS" >&2; return 1; }
  [ "$(report_get "$file" g4_deny_on_chain_unchanged)" = "PASS" ] || { echo "FAIL: g4_deny_on_chain_unchanged must be PASS" >&2; return 1; }
  [ "$(report_get "$file" same_tx_append_unchanged)" = "PASS" ] || { echo "FAIL: same_tx_append_unchanged must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || { echo "FAIL: leftover_rows_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_all)" = "true" ] || { echo "FAIL: leftover_rows_all must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_99_kept)" = "true" ] || { echo "FAIL: leftover_99_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_187_kept)" = "true" ] || { echo "FAIL: leftover_187_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_197_kept)" = "true" ] || { echo "FAIL: leftover_197_kept must stay true (keep g9-redis-down-invalidate-remainder)" >&2; return 1; }
  [ "$(report_get "$file" leftover_204_kept)" = "true" ] || { echo "FAIL: leftover_204_kept must stay true (keep g2-h5-every-page-form-write-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_205_kept)" = "true" ] || { echo "FAIL: leftover_205_kept must stay true (keep g17-mfa-recovery-vue-otp-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_188_kept)" = "true" ] || { echo "FAIL: leftover_188_kept must stay true (keep g2-ops-pg-dump-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_209_kept)" = "true" ] || { echo "FAIL: leftover_209_kept must stay true (keep g9-sentinel-cluster-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_217_kept)" = "true" ] || { echo "FAIL: leftover_217_kept must stay true (keep g5-publishhook-ddl-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_210_kept)" = "true" ] || { echo "FAIL: leftover_210_kept must stay true (keep g17-gm-tls-nginx-handshake-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_215_kept)" = "true" ] || { echo "FAIL: leftover_215_kept must stay true (keep g1-queryast-export-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_220_kept)" = "true" ] || { echo "FAIL: leftover_220_kept must stay true (keep g15-wx-dt-native-bridge-remainder)" >&2; return 1; }
  [ "$(report_get "$file" p1_all_demo_rewired)" = "false" ] || { echo "FAIL: p1_all_demo_rewired must stay false" >&2; return 1; }
  [ "$(report_get "$file" p1_all_demo_status)" = "OPEN" ] || { echo "FAIL: p1_all_demo_status must stay OPEN" >&2; return 1; }
  [ "$(report_get "$file" product_duty_ui)" = "false" ] || { echo "FAIL: product_duty_ui must stay false" >&2; return 1; }
  [ "$(report_get "$file" product_duty_ui_status)" = "OPEN" ] || { echo "FAIL: product_duty_ui_status must stay OPEN" >&2; return 1; }
  [ "$(report_get "$file" product_dual_control_ui)" = "false" ] || { echo "FAIL: product_dual_control_ui must stay false" >&2; return 1; }
  [ "$(report_get "$file" product_dual_control_ui_status)" = "OPEN" ] || { echo "FAIL: product_dual_control_ui_status must stay OPEN" >&2; return 1; }

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $REQUIRED_LEFTOVER_ROWS; do
    echo "$rows" | grep -Fq "$required_row" || { echo "FAIL: leftover_rows must keep $required_row" >&2; return 1; }
  done

  local force dual
  force="$(report_get "$file" live_admin_force_walk)"
  dual="$(report_get "$file" live_dual_control_walk)"
  case "$force" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_admin_force_walk must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  case "$dual" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_dual_control_walk must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  if [ "$force" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_admin_force_walk=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_idp_reachable)" = "true" ] || { echo "FAIL: live_admin_force_walk=PASS requires live_idp_reachable=true" >&2; return 1; }
    [ "$(report_get "$file" live_admin_force_observed)" = "true" ] || { echo "FAIL: live_admin_force_walk=PASS requires live_admin_force_observed=true" >&2; return 1; }
  fi
  if [ "$force" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_admin_force_skip_reason)" ] || { echo "FAIL: live admin force SKIP needs live_admin_force_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_admin_force_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim live_admin_force_observed=true" >&2; return 1; }
  fi
  if [ "$dual" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || { echo "FAIL: live_dual_control_walk=PASS requires live_jar_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_idp_reachable)" = "true" ] || { echo "FAIL: live_dual_control_walk=PASS requires live_idp_reachable=true" >&2; return 1; }
    [ "$(report_get "$file" live_dual_control_observed)" = "true" ] || { echo "FAIL: live_dual_control_walk=PASS requires live_dual_control_observed=true" >&2; return 1; }
  fi
  if [ "$dual" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_dual_control_skip_reason)" ] || { echo "FAIL: live dual-control SKIP needs live_dual_control_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_dual_control_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim live_dual_control_observed=true" >&2; return 1; }
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|totpSecret=|ACCESS_KEY=|TOKEN=' "$file"; then
    echo "FAIL: report must not contain password/secret/token material" >&2
    return 1
  fi
  return 0
}

verify_report() {
  local file="$1"
  local expect="${2:-pass}"
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

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT" "$VERIFY_EXPECT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g17-separation-leftover-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G17 leftover: 三权分立 admin-force vs dual-control honesty beyond #99/#187"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #96/#99/#187. Not product G17 / SoD / dual-control close. G18 stays closed."
echo

[ -f "$ARTIFACT_187" ] || fail "missing committed #187 artifact $ARTIFACT_187"
[ -f "$README" ] || fail "missing $README"

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

C187_CATALOG="$(report_get "$ARTIFACT_187" catalog_99_inventory)"
C187_LIVE="$(report_get "$ARTIFACT_187" live_sod_walk)"
C187_SOD="$(report_get "$ARTIFACT_187" g17_sod_close)"
C187_G17="$(report_get "$ARTIFACT_187" g17_close)"
C187_ADMIN="$(report_get "$ARTIFACT_187" live_admin_observed)"
[ "$C187_CATALOG" = "PASS" ] || fail "committed #187 catalog_99_inventory is $C187_CATALOG; must stay PASS"
[ "$C187_LIVE" = "SKIP" ] || fail "committed #187 live_sod_walk is $C187_LIVE; leftover requires honest SKIP on that artifact"
[ "$C187_SOD" = "NOT_CLAIMED" ] || fail "committed #187 g17_sod_close is $C187_SOD"
[ "$C187_G17" = "NOT_CLAIMED" ] || fail "committed #187 g17_close is $C187_G17"
[ "$C187_ADMIN" = "false" ] || fail "committed #187 live_admin_observed is $C187_ADMIN; SKIP cannot claim a walk"

MISSING_ROWS=""
LEFTOVER_ROWS_KEPT=true
LEFTOVER_99_KEPT=false
LEFTOVER_187_KEPT=false
LEFTOVER_197_KEPT=false
LEFTOVER_204_KEPT=false
LEFTOVER_205_KEPT=false
LEFTOVER_188_KEPT=false
LEFTOVER_209_KEPT=false
LEFTOVER_217_KEPT=false
LEFTOVER_210_KEPT=false
LEFTOVER_215_KEPT=false
LEFTOVER_220_KEPT=false
for required_row in $REQUIRED_LEFTOVER_ROWS; do
  if grep -Fq "[${required_row}](./${required_row}.md)" "$README"; then
    if [ "$required_row" = "g17-separation-of-duties" ]; then
      LEFTOVER_99_KEPT=true
    fi
    if [ "$required_row" = "g17-separation-honesty" ]; then
      LEFTOVER_187_KEPT=true
    fi
    if [ "$required_row" = "g9-redis-down-invalidate-remainder" ]; then
      LEFTOVER_197_KEPT=true
    fi
    if [ "$required_row" = "g2-h5-every-page-form-write-honesty" ]; then
      LEFTOVER_204_KEPT=true
    fi
    if [ "$required_row" = "g17-mfa-recovery-vue-otp-honesty" ]; then
      LEFTOVER_205_KEPT=true
    fi
    if [ "$required_row" = "g2-ops-pg-dump-honesty" ]; then
      LEFTOVER_188_KEPT=true
    fi
    if [ "$required_row" = "g9-sentinel-cluster-remainder-honesty" ]; then
      LEFTOVER_209_KEPT=true
    fi
    if [ "$required_row" = "g5-publishhook-ddl-honesty" ]; then
      LEFTOVER_217_KEPT=true
    fi
    if [ "$required_row" = "g17-gm-tls-nginx-handshake-honesty" ]; then
      LEFTOVER_210_KEPT=true
    fi
    if [ "$required_row" = "g1-queryast-export-honesty" ]; then
      LEFTOVER_215_KEPT=true
    fi
    if [ "$required_row" = "g15-wx-dt-native-bridge-remainder" ]; then
      LEFTOVER_220_KEPT=true
    fi
  else
    LEFTOVER_ROWS_KEPT=false
    MISSING_ROWS="${MISSING_ROWS} ${required_row}"
  fi
done
[ "$LEFTOVER_ROWS_KEPT" = "true" ] || fail "docs/README.md dropped leftover row(s):$MISSING_ROWS"
[ "$LEFTOVER_99_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-separation-of-duties (#99)"
[ "$LEFTOVER_187_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-separation-honesty (#187)"
[ "$LEFTOVER_197_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-redis-down-invalidate-remainder (#197)"
[ "$LEFTOVER_204_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-h5-every-page-form-write-honesty (#204)"
[ "$LEFTOVER_205_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-mfa-recovery-vue-otp-honesty (#205)"
[ "$LEFTOVER_188_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-ops-pg-dump-honesty (#188)"
[ "$LEFTOVER_209_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-sentinel-cluster-remainder-honesty (#209)"
[ "$LEFTOVER_217_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g5-publishhook-ddl-honesty (#217)"
[ "$LEFTOVER_210_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-gm-tls-nginx-handshake-honesty (#210)"
[ "$LEFTOVER_215_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g1-queryast-export-honesty (#215)"
[ "$LEFTOVER_220_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g15-wx-dt-native-bridge-remainder (#220)"

eval "$(python3 - "$ROOT" <<'PY'
import pathlib, sys
root = pathlib.Path(sys.argv[1])
catalog = (root / "nexus-permission-core/src/main/java/one/nexus/permission/admin/PlatformDutyCatalog.java").read_text(encoding="utf-8")
admin = (root / "nexus-permission-core/src/main/java/one/nexus/permission/admin/PolicyAdminService.java").read_text(encoding="utf-8")
engine = (root / "nexus-permission-core/src/main/java/one/nexus/permission/engine/PermissionEngine.java").read_text(encoding="utf-8")
guard = (root / "nexus-permission-core/src/main/java/one/nexus/permission/surface/SurfaceGuard.java").read_text(encoding="utf-8")
actor = (root / "nexus-p1-tenant/src/main/java/one/nexus/p1/domain/Actor.java").read_text(encoding="utf-8")
demo = (root / "nexus-p1-tenant/src/main/java/one/nexus/p1/infra/isolation/DemoPlatformContextFilter.java").read_text(encoding="utf-8")
needed = (
    'SYSTEM_ADMIN = "platform_system_admin"' in catalog
    and 'SECURITY_ADMIN = "platform_security_admin"' in catalog
    and 'AUDIT_ADMIN = "platform_audit_admin"' in catalog
    and "exclusivePairs()" in catalog
    and "duty_conflict" in admin
    and "duty_conflict" in engine
)
print("CATALOG_OK=%s" % ("true" if needed else "false"))
print("OPERATOR_NOT_DUTY=%s" % ("true" if "tenant_member remains demo bootstrap and is not a platform duty" in catalog else "false"))
print("SURFACEGUARD_OK=%s" % ("true" if "appendNewTransaction" in guard and "PermissionEngine" in guard else "false"))
print("G4_DENY_OK=%s" % ("true" if "appendNewTransaction(AuditCaptures.permission(" in guard and "appendNewTransaction(AuditCaptures.guardReject(" in guard else "false"))
print("SAME_TX_OK=%s" % ("true" if "same-TX" in guard or "appendNewTransaction" in guard else "false"))
all_demo = "duties.contains(PlatformDuty.ALL_DEMO)" in actor
demo_stamp = "Set.of(PlatformDuty.ALL_DEMO)" in demo
print("P1_ALL_DEMO_STILL=%s" % ("true" if all_demo and demo_stamp else "false"))
needles = (
    "forceApprove", "force_approve", "forceOverride", "force_override",
    "dualControl", "dual_control", "makerChecker", "maker_checker",
)
skip_parts = ("honesty", "leftover", "EVIDENCE", "fixtures")
hits = []
for base in (
    root / "nexus-permission-core/src/main",
    root / "nexus-bootstrap/src/main",
    root / "web/src",
):
    if not base.exists():
        continue
    for path in base.rglob("*"):
        if path.suffix.lower() not in {".java", ".vue", ".js", ".ts"}:
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        if any(n in text for n in needles):
            rel = str(path.relative_to(root))
            if not any(p in rel for p in skip_parts):
                hits.append(rel)
print("ADMIN_FORCE_BYPASS=%s" % ("true" if hits else "false"))
ui_hits = []
web = root / "web/src"
if web.is_dir():
    for path in web.rglob("*"):
        if path.suffix.lower() not in {".vue", ".js", ".ts"}:
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        if "platform_system_admin" in text and ("assign" in text.lower() or "duties/example" in text):
            ui_hits.append(str(path))
        lowered = text.lower()
        if ("dual-control" in lowered or "maker-checker" in lowered or "双人复核" in text) and "honesty" not in str(path):
            ui_hits.append(str(path))
print("PRODUCT_DUTY_UI=%s" % ("true" if ui_hits else "false"))
print("PRODUCT_DUAL_CONTROL_UI=%s" % ("true" if ui_hits else "false"))
PY
)"
[ "$CATALOG_OK" = "true" ] || fail "#99 PlatformDutyCatalog / assign / evaluate inventory missing"
[ "$OPERATOR_NOT_DUTY" = "true" ] || fail "tenant_member must stay documented as not a platform duty"
[ "$SURFACEGUARD_OK" = "true" ] || fail "G2 SurfaceGuard must still be required (admin does not bypass)"
[ "$G4_DENY_OK" = "true" ] || fail "G4 deny-on-chain appendNewTransaction pair must stay"
[ "$SAME_TX_OK" = "true" ] || fail "AuditPort same-TX appendNewTransaction must stay untouched"
[ "$P1_ALL_DEMO_STILL" = "true" ] || fail "P1 ALL_DEMO bypass must still be present (remaining SoD path)"
[ "$ADMIN_FORCE_BYPASS" = "false" ] || fail "product admin-force / dual-control bypass API appeared; leftover must stay honest"
[ "$PRODUCT_DUTY_UI" = "false" ] || fail "product duty-assign / dual-control UI appeared; leftover must stay honest"
[ "$PRODUCT_DUAL_CONTROL_UI" = "false" ] || fail "product dual-control UI appeared; leftover must stay honest"

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

LIVE_ENDPOINT="${NEXUS_G17_SOD_LIVE_BASE:-${NEXUS_BASE_URL:-}}"
LIVE_ENDPOINT_SET=false
LIVE_CONTEXT_SET=false
if [ -n "$LIVE_ENDPOINT" ]; then
  LIVE_ENDPOINT_SET=true
fi
if [ -n "${NEXUS_G17_SOD_LIVE_CONTEXT_ID:-}" ]; then
  LIVE_CONTEXT_SET=true
fi

LIVE_LISTENER=false
if port_open 127.0.0.1 8080 || port_open 127.0.0.1 8088; then
  LIVE_LISTENER=true
fi

KEYCLOAK_ISSUER="${NEXUS_KEYCLOAK_ISSUER:-}"
KC_REACHABLE=false
OIDC_DISCOVERY=SKIP
if [[ -z "$KEYCLOAK_ISSUER" ]]; then
  for candidate in http://127.0.0.1:8080/realms/nexus http://127.0.0.1:8180/realms/nexus; do
    if curl -sf --max-time 2 "${candidate}/.well-known/openid-configuration" >/dev/null 2>&1; then
      KEYCLOAK_ISSUER="$candidate"
      KC_REACHABLE=true
      OIDC_DISCOVERY=PASS
      break
    fi
  done
elif curl -sf --max-time 2 "${KEYCLOAK_ISSUER}/.well-known/openid-configuration" >/dev/null 2>&1; then
  KC_REACHABLE=true
  OIDC_DISCOVERY=PASS
fi

LIVE_JAR_PRESENT=false
if [ "$LIVE_ENDPOINT_SET" = "true" ] && [ "$LIVE_CONTEXT_SET" = "true" ] && [ "$LIVE_LISTENER" = "true" ]; then
  LIVE_JAR_PRESENT=true
fi

if [ "$LIVE_ENDPOINT_SET" != "true" ] && [ "$LIVE_LISTENER" != "true" ] && [ "$KC_REACHABLE" != "true" ]; then
  FORCE_REASON="live JAR unset (NEXUS_BASE_URL / NEXUS_G17_SOD_LIVE_BASE); nothing on 127.0.0.1:8080 / :8088; live IdP unreachable; Docker forbidden; this leftover does not start the JAR and does not re-run #99/#187"
  DUAL_REASON="live JAR unset; live IdP unreachable; product dual-control (maker-checker) walk not observed; Docker forbidden"
elif [ "$KC_REACHABLE" != "true" ]; then
  FORCE_REASON="live IdP unreachable (NEXUS_KEYCLOAK_ISSUER / loopback :8080/:8180); admin force HTTP SKIP"
  DUAL_REASON="live IdP unreachable; dual-control (maker + checker) walk SKIP"
elif [ "$LIVE_CONTEXT_SET" != "true" ]; then
  FORCE_REASON="issued SoD context unset (NEXUS_G17_SOD_LIVE_CONTEXT_ID); live admin force walk SKIP"
  DUAL_REASON="issued SoD context unset; live dual-control walk SKIP"
elif [ "$LIVE_LISTENER" != "true" ]; then
  FORCE_REASON="live IdP reachable but product JAR not listening; live admin force walk SKIP"
  DUAL_REASON="live IdP reachable but product JAR not listening; live dual-control walk SKIP"
else
  FORCE_REASON="live listener/IdP present but admin force HTTP walk was not observed; this leftover does not fabricate PASS"
  DUAL_REASON="live listener/IdP present but maker+checker dual-control walk was not observed; this leftover does not fabricate PASS"
fi

LEFTOVER_ROWS_CSV="$(echo "$REQUIRED_LEFTOVER_ROWS" | tr ' ' ',')"
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
  kv leftover_99_cells_honest PASS
  kv leftover_187_cells_honest PASS
  kv catalog_99_inventory PASS
  kv exclusive_pairs_documented true
  kv operator_not_platform_duty "$OPERATOR_NOT_DUTY"
  kv no_admin_force_bypass_api true
  kv surfaceguard_still_required PASS
  kv g4_deny_on_chain_unchanged PASS
  kv same_tx_append_unchanged PASS
  kv p1_all_demo_rewired false
  kv p1_all_demo_status OPEN
  kv product_duty_ui false
  kv product_duty_ui_status OPEN
  kv product_dual_control_ui false
  kv product_dual_control_ui_status OPEN
  kv leftover_rows_kept "$LEFTOVER_ROWS_KEPT"
  kv leftover_rows "$LEFTOVER_ROWS_CSV"
  kv leftover_rows_all true
  kv leftover_99_kept "$LEFTOVER_99_KEPT"
  kv leftover_187_kept "$LEFTOVER_187_KEPT"
  kv leftover_197_kept "$LEFTOVER_197_KEPT"
  kv leftover_204_kept "$LEFTOVER_204_KEPT"
  kv leftover_205_kept "$LEFTOVER_205_KEPT"
  kv leftover_188_kept "$LEFTOVER_188_KEPT"
  kv leftover_209_kept "$LEFTOVER_209_KEPT"
  kv leftover_217_kept "$LEFTOVER_217_KEPT"
  kv leftover_210_kept "$LEFTOVER_210_KEPT"
  kv leftover_215_kept "$LEFTOVER_215_KEPT"
  kv leftover_220_kept "$LEFTOVER_220_KEPT"
  kv live_jar_endpoint_set "$LIVE_ENDPOINT_SET"
  kv live_jar_listener "$LIVE_LISTENER"
  kv live_jar_present "$LIVE_JAR_PRESENT"
  kv live_idp_reachable "$KC_REACHABLE"
  kv live_idp_context_set "$LIVE_CONTEXT_SET"
  kv oidc_discovery "$OIDC_DISCOVERY"
  kv probed_issuer "${KEYCLOAK_ISSUER:-}"
  kv live_admin_force_walk SKIP
  kv live_admin_force_skip_reason "$FORCE_REASON"
  kv live_admin_force_observed false
  kv live_dual_control_walk SKIP
  kv live_dual_control_skip_reason "$DUAL_REASON"
  kv live_dual_control_observed false
  kv live_sod_walk SKIP
  kv dengbao_claimed false
  kv mipin_claimed false
  kv hsm_claimed false
  kv g17_close NOT_CLAIMED
  kv g17_sod_close NOT_CLAIMED
  kv product_dual_control_close NOT_CLAIMED
  kv g17_close_reason "Honesty leftover beyond #99/#187 after G2+G4. Catalog exclusive pairs PASS. No product admin-force bypass API. Live admin-force / dual-control SKIP when JAR/IdP unavailable. Not product G17 / SoD / dual-control / 等保 / 密评."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
  kv redo_96 false
  kv redo_99 false
  kv redo_187 false
} >"$REPORT"

mkdir -p "$(dirname "$JSON_OUT")"
python3 - "$JSON_OUT" "$REPORT" <<'PY'
import json, sys
out, report = sys.argv[1:3]
vals = {}
for line in open(report, encoding="utf-8"):
    if "=" in line and not line.startswith("#"):
        k, v = line.rstrip("\n").split("=", 1)
        vals[k] = v
walk = {
    "leftover": "g17-separation-leftover-honesty",
    "siblings": ["g17-separation-of-duties", "g17-separation-honesty"],
    "catalog_99_inventory": vals.get("catalog_99_inventory"),
    "no_admin_force_bypass_api": vals.get("no_admin_force_bypass_api"),
    "live_admin_force_walk": vals.get("live_admin_force_walk"),
    "live_dual_control_walk": vals.get("live_dual_control_walk"),
    "product_dual_control_ui_status": vals.get("product_dual_control_ui_status"),
    "g17Close": "NOT_CLAIMED",
    "g17SodClose": "NOT_CLAIMED",
    "productDualControlClose": "NOT_CLAIMED",
    "g18": "closed_unchanged",
    "timestampUtc": vals.get("timestamp_utc"),
    "evidence": "scripts/g17-separation-leftover-honesty.sh",
}
open(out, "w", encoding="utf-8").write(json.dumps(walk, indent=2) + "\n")
print("wrote", out)
PY

verify_report "$REPORT"

echo "== Honesty fixtures"
verify_report "$HERE/g17-separation-leftover-honesty/fixtures/honest-skip.env"
verify_report "$HERE/g17-separation-leftover-honesty/fixtures/fake-live-admin-force-pass.env" fail
verify_report "$HERE/g17-separation-leftover-honesty/fixtures/fake-live-dual-control-pass.env" fail
verify_report "$HERE/g17-separation-leftover-honesty/fixtures/fake-g17-close.env" fail
verify_report "$HERE/g17-separation-leftover-honesty/fixtures/fake-dual-control-close.env" fail
verify_report "$HERE/g17-separation-leftover-honesty/fixtures/fake-redo.env" fail

SANITIZED="${ROOT}/docs/engineering/g17-separation-leftover-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_admin_force=SKIP live_dual_control=SKIP g17_close=NOT_CLAIMED"
echo
echo "PASS: leftover honesty recorded (#99/#187 cells honest; admin force SKIP; dual-control SKIP)"
echo "See docs/g17-separation-leftover-honesty.md"
echo "Not claimed: product G17 / SoD / dual-control close / 等保 / 密评"
echo "Not redone: #96/#99/#187"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
echo "walk=$JSON_OUT"
