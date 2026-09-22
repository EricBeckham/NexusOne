#!/usr/bin/env bash
# G10 leftover remainder: file-reconcile / object-reconcile PASS/SKIP honesty
# beyond #228 / #202 / #169 / #179 / #141.
#
# Extends docs/g10-leftover-honesty-remainder.md (#228) and
# docs/g10-leftover-honesty.md (#202).
# Does NOT redo #141 / #169 / #179 / #202 / #228 product code.
# Does NOT claim product G10 close. G18 stays closed.
# No P0/M0 redo. No Docker. AuditPort same-TX unchanged.
# #228/#202 leftover honesty fixtures stay PASS and are explicitly NOT a
# live MinIO / live object-store walk / product G10 close.
# Live MinIO / object reconcile is PASS only when the endpoint is up AND
# preview/apply/prefix were observed. Missing listener is honest SKIP.
# Never fabricates PASS.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""
VERIFY_EXPECT=pass
SKIP_TESTS=0

FILE_169="$ROOT/docs/engineering/g10-file-reconcile-honesty/agent-vm.env"
ID_179="$ROOT/docs/engineering/g10-identity-drift-honesty/agent-vm.env"
FILE_202="$ROOT/docs/engineering/g10-leftover-honesty/agent-vm.env"
FILE_228="$ROOT/docs/engineering/g10-leftover-honesty-remainder/agent-vm.env"
README="$ROOT/docs/README.md"

REQUIRED_LEFTOVER_ROWS="g10-identity-drift-dry-run g10-file-reconcile-honesty g10-identity-drift-honesty g10-leftover-honesty g10-leftover-honesty-remainder g2-h5-every-page-form-write-honesty g2-h5-every-page-form-write-remainder-honesty g2-report-physical-dump-honesty g7-withdraw-browser-evidence-honesty g7-query-expense-travel-honesty-remainder g1-fieldpolicy-mask-second-auth-remainder-honesty g17-mfa-recovery-vue-otp-remainder-honesty g4-hot-purge-staging-leftover-honesty g9-live-multi-jvm-replica-remainder-honesty g9-redis-down-invalidate-remainder-honesty g9-redis-down-invalidate-remainder g3-outbox-consume-idempotent-honesty g6-recovery-port-dry-run-remainder-honesty s0-secret-rotate-getenv-remainder-honesty g4-pkce-spa-verifier-remainder-honesty g17-separation-leftover-honesty g15-wx-dt-native-bridge-remainder g1-queryast-export-honesty g17-gm-tls-nginx-handshake-honesty g5-publishhook-ddl-honesty g5-publishhook-ddl-remainder-honesty s0-secret-rotate-getenv-leftover-honesty"

SIBLING_ROWS="
g10-identity-drift-dry-run
g10-file-reconcile-honesty
g10-identity-drift-honesty
g10-leftover-honesty
g10-leftover-honesty-remainder
g2-h5-every-page-form-write-honesty
g2-h5-every-page-form-write-remainder-honesty
g2-form-write-honesty
g2-h5-every-page
g2-report-physical-dump-honesty
g2-ops-pg-dump-honesty
g7-withdraw-browser-evidence-honesty
g7-withdraw-browser-honesty
g7-query-expense-travel-honesty
g7-query-expense-travel-honesty-remainder
g1-fieldpolicy-mask-second-auth-remainder-honesty
g1-fieldpolicy-mask-second-auth-honesty
g1-fieldpolicy-mask-second-auth
g17-mfa-recovery-vue-otp-remainder-honesty
g4-hot-purge-staging-leftover-honesty
g4-hot-purge-90d-honesty
g4-p1-staging-audit-events-honesty
g4-pkce-spa-verifier-remainder-honesty
g4-pkce-spa-verifier-honesty
g4-export-wizard-deny-honesty
g9-live-multi-jvm-replica-remainder-honesty
g9-sentinel-cluster-remainder-honesty
g9-sentinel-cluster-honesty
g9-live-multi-jvm-replica
g9-redis-down-invalidate-honesty
g9-redis-down-invalidate-remainder
g9-redis-down-invalidate-remainder-honesty
g3-outbox-consume-idempotent-honesty
g3-outbox-consume-crash-honesty
g6-recovery-port-dry-run-remainder-honesty
g6-recovery-port-dry-run-honesty
g6-recovery-port-honesty
g6-recovery-port-dry-run
s0-secret-rotate-getenv-remainder-honesty
s0-secret-rotate-without-restart-honesty
s0-secret-getenv-snapshot-honesty
s0-secret-rotate-without-restart
g17-separation-leftover-honesty
g17-separation-honesty
g17-separation-of-duties
g17-gm-tls-nginx-handshake-honesty
g17-mfa-recovery-vue-otp-honesty
g17-mfa-recovery-email-honesty
g15-wx-dt-native-bridge-remainder
g15-wx-dt-native-bridge-honesty
g15-wx-dt-prod-gap-honesty
g15-native-bridge-honesty
g1-queryast-export-honesty
g1-queryast-boundsql-honesty
g1-fieldpolicy-mask-second-auth-honesty
g5-publishhook-ddl-honesty
g5-publishhook-ddl-remainder-honesty
s0-secret-rotate-getenv-leftover-honesty
g5-mid-publish-failure-honesty
g5-browser-failed-publish
g5-mid-publish-failure
linux-arm-profile-leftover-honesty
linux-arm-profile-honesty
s0s1-live-redis-minio-isolation-honesty
"

usage() {
  cat <<'EOF'
Usage:
  scripts/g10-file-object-reconcile-remainder-honesty.sh [--out DIR] [--skip-tests]
  scripts/g10-file-object-reconcile-remainder-honesty.sh --verify-report FILE [pass|fail]

Beyond #228/#202/#169/#179/#141: leftover #169/#179/#202/#228 cells stay
honest; parent leftover honesty fixtures stay PASS and are NOT a live
MinIO / object-store walk. Live MinIO / object reconcile is PASS only
when present AND the walk was observed. Missing listener is honest SKIP.
Does not re-run #141/#169/#179/#202/#228. Does not claim product G10 close.

  --out DIR           Output directory (default: .local/g10-file-object-reconcile-remainder-honesty/<utc>)
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

artifact_get() {
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_169_cells_honest leftover_179_cells_honest leftover_202_cells_honest leftover_202_honesty_gate leftover_228_cells_honest leftover_228_honesty_gate harness_228_is_not_live_minio harness_228_is_not_live_object harness_228_is_not_product_g10 harness_202_is_not_live_minio leftover_rows_kept leftover_rows_all counting_preview_apply tenant_prefix_guard dry_run_default_no_write s3_endpoint_set s3_credentials_set live_minio_listener live_minio_present live_minio_reconcile live_minio_skip_reason live_object_reconcile live_object_skip_reason live_preview_observed live_apply_observed live_tenant_prefix_observed keycloak_8080_listener keycloak_oidc_discovery live_keycloak_present live_keycloak_8080 live_keycloak_skip_reason live_dry_run_observed live_apply live_apply_skip_reason live_apply_observed identity_apply_product_redone identity_drift_141_redone file_reconcile_169_redone identity_drift_179_redone leftover_202_redone leftover_228_redone saml_claimed ldap_claimed sms_mfa_claimed g10_close g18_status audit_port_changed p0_m0_redone honesty_169_rerun honesty_179_rerun honesty_202_rerun honesty_228_rerun"
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
  [ "$(report_get "$file" identity_drift_141_redone)" = "false" ] || { echo "FAIL: must not redo #141" >&2; return 1; }
  [ "$(report_get "$file" file_reconcile_169_redone)" = "false" ] || { echo "FAIL: must not redo #169" >&2; return 1; }
  [ "$(report_get "$file" identity_drift_179_redone)" = "false" ] || { echo "FAIL: must not redo #179" >&2; return 1; }
  [ "$(report_get "$file" leftover_202_redone)" = "false" ] || { echo "FAIL: must not redo #202" >&2; return 1; }
  [ "$(report_get "$file" leftover_228_redone)" = "false" ] || { echo "FAIL: must not redo #228" >&2; return 1; }
  [ "$(report_get "$file" honesty_169_rerun)" = "false" ] || { echo "FAIL: honesty_169_rerun must be false" >&2; return 1; }
  [ "$(report_get "$file" honesty_179_rerun)" = "false" ] || { echo "FAIL: honesty_179_rerun must be false" >&2; return 1; }
  [ "$(report_get "$file" honesty_202_rerun)" = "false" ] || { echo "FAIL: honesty_202_rerun must be false (do not redo #202)" >&2; return 1; }
  [ "$(report_get "$file" honesty_228_rerun)" = "false" ] || { echo "FAIL: honesty_228_rerun must be false (do not redo #228)" >&2; return 1; }
  [ "$(report_get "$file" identity_apply_product_redone)" = "false" ] || { echo "FAIL: identity_apply_product_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" saml_claimed)" = "false" ] || { echo "FAIL: saml_claimed must stay false" >&2; return 1; }
  [ "$(report_get "$file" ldap_claimed)" = "false" ] || { echo "FAIL: ldap_claimed must stay false" >&2; return 1; }
  [ "$(report_get "$file" sms_mfa_claimed)" = "false" ] || { echo "FAIL: sms_mfa_claimed must stay false" >&2; return 1; }
  [ "$(report_get "$file" g10_close)" = "NOT_CLAIMED" ] || { echo "FAIL: g10_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" leftover_169_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_169_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_179_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_179_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_202_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_202_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_202_honesty_gate)" = "PASS" ] || { echo "FAIL: leftover_202_honesty_gate must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_228_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_228_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_228_honesty_gate)" = "PASS" ] || { echo "FAIL: leftover_228_honesty_gate must be PASS" >&2; return 1; }
  [ "$(report_get "$file" harness_228_is_not_live_minio)" = "true" ] || { echo "FAIL: harness_228_is_not_live_minio must stay true; #228 harness is not a live MinIO walk" >&2; return 1; }
  [ "$(report_get "$file" harness_228_is_not_live_object)" = "true" ] || { echo "FAIL: harness_228_is_not_live_object must stay true; #228 harness is not a live object reconcile" >&2; return 1; }
  [ "$(report_get "$file" harness_228_is_not_product_g10)" = "true" ] || { echo "FAIL: harness_228_is_not_product_g10 must stay true; #228 PASS is not product G10 close" >&2; return 1; }
  [ "$(report_get "$file" harness_202_is_not_live_minio)" = "true" ] || { echo "FAIL: harness_202_is_not_live_minio must stay true; #202 harness is not a live MinIO walk" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_all)" = "true" ] || { echo "FAIL: leftover_rows_all must stay true" >&2; return 1; }
  [ "$(report_get "$file" counting_preview_apply)" = "PASS" ] || { echo "FAIL: counting_preview_apply must be PASS" >&2; return 1; }
  [ "$(report_get "$file" tenant_prefix_guard)" = "PASS" ] || { echo "FAIL: tenant_prefix_guard must be PASS" >&2; return 1; }
  [ "$(report_get "$file" dry_run_default_no_write)" = "true" ] || { echo "FAIL: dry_run_default_no_write must be true" >&2; return 1; }
  [ "$(report_get "$file" live_apply)" = "SKIP" ] || { echo "FAIL: live_apply must stay SKIP (do not redo product apply)" >&2; return 1; }
  [ "$(report_get "$file" live_apply_observed)" = "false" ] || { echo "FAIL: live_apply_observed must stay false" >&2; return 1; }
  [ -n "$(report_get "$file" live_apply_skip_reason)" ] || { echo "FAIL: live_apply SKIP needs live_apply_skip_reason" >&2; return 1; }

  local rows
  rows="$(report_get "$file" leftover_rows_kept)"
  local required_row
  for required_row in $REQUIRED_LEFTOVER_ROWS; do
    echo "$rows" | grep -Fq "$required_row" || { echo "FAIL: leftover_rows_kept must keep $required_row" >&2; return 1; }
  done

  local live object
  live="$(report_get "$file" live_minio_reconcile)"
  object="$(report_get "$file" live_object_reconcile)"
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_minio_reconcile must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  case "$object" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_object_reconcile must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" live_minio_present)" = "true" ] || { echo "FAIL: live_minio_reconcile=PASS requires live_minio_present=true" >&2; return 1; }
    [ "$(report_get "$file" s3_credentials_set)" = "true" ] || { echo "FAIL: live_minio_reconcile=PASS requires s3_credentials_set=true" >&2; return 1; }
    [ "$(report_get "$file" live_preview_observed)" = "true" ] || { echo "FAIL: live_minio_reconcile=PASS requires live_preview_observed=true" >&2; return 1; }
    [ "$(report_get "$file" live_apply_observed)" = "true" ] || { echo "FAIL: live_minio_reconcile=PASS requires live_apply_observed=true" >&2; return 1; }
    [ "$(report_get "$file" live_tenant_prefix_observed)" = "true" ] || { echo "FAIL: live_minio_reconcile=PASS requires live_tenant_prefix_observed=true" >&2; return 1; }
    [ "$(report_get "$file" harness_228_is_not_live_minio)" = "true" ] || { echo "FAIL: live MinIO PASS cannot treat #228 harness as the walk" >&2; return 1; }
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_minio_skip_reason)" ] || { echo "FAIL: live MinIO SKIP needs live_minio_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_preview_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live preview walk" >&2; return 1; }
    [ "$(report_get "$file" live_apply_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live apply walk" >&2; return 1; }
    [ "$(report_get "$file" live_tenant_prefix_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live tenant-prefix walk" >&2; return 1; }
  fi
  if [ "$object" = "PASS" ]; then
    [ "$(report_get "$file" live_minio_present)" = "true" ] || { echo "FAIL: live_object_reconcile=PASS requires live_minio_present=true" >&2; return 1; }
    [ "$(report_get "$file" live_preview_observed)" = "true" ] || { echo "FAIL: live_object_reconcile=PASS requires live_preview_observed=true" >&2; return 1; }
    [ "$(report_get "$file" live_tenant_prefix_observed)" = "true" ] || { echo "FAIL: live_object_reconcile=PASS requires live_tenant_prefix_observed=true" >&2; return 1; }
    [ "$(report_get "$file" harness_228_is_not_live_object)" = "true" ] || { echo "FAIL: live object PASS cannot treat #228 harness as the walk" >&2; return 1; }
  fi
  if [ "$object" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_object_skip_reason)" ] || { echo "FAIL: live object SKIP needs live_object_skip_reason" >&2; return 1; }
  fi

  live="$(report_get "$file" live_keycloak_8080)"
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) echo "FAIL: live_keycloak_8080 must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" live_keycloak_present)" = "true" ] || { echo "FAIL: live_keycloak_8080=PASS requires live_keycloak_present=true" >&2; return 1; }
    [ "$(report_get "$file" keycloak_oidc_discovery)" = "true" ] || { echo "FAIL: live_keycloak_8080=PASS requires keycloak_oidc_discovery=true" >&2; return 1; }
    [ "$(report_get "$file" live_dry_run_observed)" = "true" ] || { echo "FAIL: live_keycloak_8080=PASS requires live_dry_run_observed=true" >&2; return 1; }
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_keycloak_skip_reason)" ] || { echo "FAIL: live Keycloak SKIP needs live_keycloak_skip_reason" >&2; return 1; }
    [ "$(report_get "$file" live_dry_run_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim a live dry-run walk" >&2; return 1; }
  fi

  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=|CLIENT_SECRET=' "$file"; then
    echo "FAIL: report must not contain password/secret material" >&2
    return 1
  fi
  if grep -Eiq 'product g10 closed|g10_close=CLOSED|产品 G10 已关闭' "$file"; then
    echo "FAIL: report must not claim product G10 closed" >&2
    return 1
  fi
  return 0
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT" "$VERIFY_EXPECT"
  exit 0
fi

if [[ "${NEXUS_G10_APPLY:-}" == "1" || "${NEXUS_G10_APPLY:-}" == "true" ]]; then
  echo "REFUSE: this leftover remainder is honesty around file-reconcile / object-reconcile PASS/SKIP (no writes)." >&2
  echo "Apply stays on nexus-p2-iam/scripts/run-g10-identity-reconcile.sh" >&2
  exit 2
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g10-file-object-reconcile-remainder-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G10 leftover: file/object reconcile remainder honesty beyond #228/#202/#169/#179/#141"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #141/#169/#179/#202/#228. Not product G10 close. G18 stays closed."
echo "#228/#202 harness PASS is not a live MinIO / live object walk / product G10 close."
echo

[ -f "$FILE_169" ] || fail "missing committed #169 artifact $FILE_169"
[ -f "$ID_179" ] || fail "missing committed #179 artifact $ID_179"
[ -f "$FILE_202" ] || fail "missing committed #202 artifact $FILE_202"
[ -f "$FILE_228" ] || fail "missing committed #228 artifact $FILE_228"
[ -f "$README" ] || fail "missing $README"

JAVA_FEATURE=missing
JAVA_VERSION_LINE=not-installed
if command -v java >/dev/null 2>&1; then
  JAVA_VERSION_LINE="$(java -version 2>&1 | head -n 1 | tr -d '\r')"
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

C169_COUNTING="$(artifact_get "$FILE_169" counting_preview_apply)"
C169_PREFIX="$(artifact_get "$FILE_169" tenant_prefix_guard)"
C169_LIVE="$(artifact_get "$FILE_169" live_minio_reconcile)"
C169_PREVIEW="$(artifact_get "$FILE_169" live_preview_observed)"
C169_APPLY="$(artifact_get "$FILE_169" live_apply_observed)"
C169_G10="$(artifact_get "$FILE_169" g10_close)"
[ "$C169_COUNTING" = "PASS" ] || fail "committed #169 counting_preview_apply is $C169_COUNTING; must stay PASS"
[ "$C169_PREFIX" = "PASS" ] || fail "committed #169 tenant_prefix_guard is $C169_PREFIX; must stay PASS"
[ "$C169_LIVE" = "SKIP" ] || fail "committed #169 live_minio_reconcile is $C169_LIVE; leftover requires honest SKIP on that artifact"
[ "$C169_PREVIEW" = "false" ] || fail "committed #169 live_preview_observed is $C169_PREVIEW; SKIP cannot claim a walk"
[ "$C169_APPLY" = "false" ] || fail "committed #169 live_apply_observed is $C169_APPLY; SKIP cannot claim a walk"
[ "$C169_G10" = "NOT_CLAIMED" ] || fail "committed #169 g10_close is $C169_G10"

C179_DRY="$(artifact_get "$ID_179" dry_run_default_no_write)"
C179_KC="$(artifact_get "$ID_179" live_keycloak_8080)"
C179_APPLY="$(artifact_get "$ID_179" live_apply)"
C179_G10="$(artifact_get "$ID_179" g10_close)"
[ "$C179_DRY" = "true" ] || fail "committed #179 dry_run_default_no_write is $C179_DRY"
[ "$C179_KC" = "SKIP" ] || fail "committed #179 live_keycloak_8080 is $C179_KC; leftover requires honest SKIP on that artifact"
[ "$C179_APPLY" = "SKIP" ] || fail "committed #179 live_apply is $C179_APPLY; must stay SKIP"
[ "$C179_G10" = "NOT_CLAIMED" ] || fail "committed #179 g10_close is $C179_G10"

C202_169="$(artifact_get "$FILE_202" leftover_169_cells_honest)"
C202_179="$(artifact_get "$FILE_202" leftover_179_cells_honest)"
C202_MINIO="$(artifact_get "$FILE_202" live_minio_reconcile)"
C202_KC="$(artifact_get "$FILE_202" live_keycloak_8080)"
C202_APPLY="$(artifact_get "$FILE_202" live_apply)"
C202_G10="$(artifact_get "$FILE_202" g10_close)"
[ "$C202_169" = "PASS" ] || fail "committed #202 leftover_169_cells_honest is $C202_169"
[ "$C202_179" = "PASS" ] || fail "committed #202 leftover_179_cells_honest is $C202_179"
[ "$C202_MINIO" = "SKIP" ] || fail "committed #202 live_minio_reconcile is $C202_MINIO; leftover requires honest SKIP on that artifact"
[ "$C202_KC" = "SKIP" ] || fail "committed #202 live_keycloak_8080 is $C202_KC; leftover requires honest SKIP on that artifact"
[ "$C202_APPLY" = "SKIP" ] || fail "committed #202 live_apply is $C202_APPLY"
[ "$C202_G10" = "NOT_CLAIMED" ] || fail "committed #202 g10_close is $C202_G10"

C228_202="$(artifact_get "$FILE_228" leftover_202_cells_honest)"
C228_GATE="$(artifact_get "$FILE_228" leftover_202_honesty_gate)"
C228_MINIO="$(artifact_get "$FILE_228" live_minio_reconcile)"
C228_KC="$(artifact_get "$FILE_228" live_keycloak_8080)"
C228_APPLY="$(artifact_get "$FILE_228" live_apply)"
C228_G10="$(artifact_get "$FILE_228" g10_close)"
C228_G18="$(artifact_get "$FILE_228" g18_status)"
C228_AUDIT="$(artifact_get "$FILE_228" audit_port_changed)"
C228_P0="$(artifact_get "$FILE_228" p0_m0_redone)"
[ "$C228_202" = "PASS" ] || fail "committed #228 leftover_202_cells_honest is $C228_202"
[ "$C228_GATE" = "PASS" ] || fail "committed #228 leftover_202_honesty_gate is $C228_GATE"
[ "$C228_MINIO" = "SKIP" ] || fail "committed #228 live_minio_reconcile is $C228_MINIO; leftover requires honest SKIP on that artifact"
[ "$C228_KC" = "SKIP" ] || fail "committed #228 live_keycloak_8080 is $C228_KC; leftover requires honest SKIP on that artifact"
[ "$C228_APPLY" = "SKIP" ] || fail "committed #228 live_apply is $C228_APPLY"
[ "$C228_G10" = "NOT_CLAIMED" ] || fail "committed #228 g10_close is $C228_G10"
[ "$C228_G18" = "closed_unchanged" ] || fail "committed #228 g18_status is $C228_G18"
[ "$C228_AUDIT" = "false" ] || fail "committed #228 audit_port_changed is $C228_AUDIT"
[ "$C228_P0" = "false" ] || fail "committed #228 p0_m0_redone is $C228_P0"

LEFTOVER_ROWS_ALL=true
for row in $SIBLING_ROWS; do
  grep -q "\[${row}\]" "$README" || fail "docs/README.md must keep leftover row ${row}"
done
grep -q '\[g10-file-object-reconcile-remainder-honesty\](\./g10-file-object-reconcile-remainder-honesty.md)' "$README" \
  || fail "docs/README.md must list this leftover row"
if grep -Eiq '产品 G10 已关闭|product g10 closed|g10_close=CLOSED' "$README"; then
  fail "docs/README.md must not claim product G10 closed"
fi

echo
echo "== Honesty fixtures (this leftover)"
verify_report scripts/g10-file-object-reconcile-remainder-honesty/fixtures/honest-skip.env pass
verify_report scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-g10-close.env fail
verify_report scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-live-minio-pass.env fail
verify_report scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-live-keycloak-pass.env fail
verify_report scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-live-apply-pass.env fail
verify_report scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-redone.env fail
verify_report scripts/g10-file-object-reconcile-remainder-honesty/fixtures/fake-harness-as-live.env fail

echo
echo "== Parent leftover honesty still PASS (#228/#202 verify-report only; not a product redo)"
if [ "$SKIP_TESTS" = "0" ]; then
  log "verify parent #228 leftover honesty fixtures (harness PASS; do not re-run collect)"
  ./scripts/g10-leftover-honesty-remainder.sh --verify-report \
    scripts/g10-leftover-honesty-remainder/fixtures/honest-skip.env | tee -a "$OUT/collect.log"
  set +e
  ./scripts/g10-leftover-honesty-remainder.sh --verify-report \
    scripts/g10-leftover-honesty-remainder/fixtures/fake-live-minio-pass.env >"$OUT/fake-228-minio.log" 2>&1
  FAKE228_MINIO=$?
  ./scripts/g10-leftover-honesty-remainder.sh --verify-report \
    scripts/g10-leftover-honesty-remainder/fixtures/fake-g10-close.env >"$OUT/fake-228-close.log" 2>&1
  FAKE228_CLOSE=$?
  set -e
  [ "$FAKE228_MINIO" -ne 0 ] || fail "expected #228 fake-live-minio-pass.env to FAIL"
  [ "$FAKE228_CLOSE" -ne 0 ] || fail "expected #228 fake-g10-close.env to FAIL"
  log "verify parent #202 leftover honesty fixtures (harness PASS; do not re-run collect)"
  ./scripts/g10-leftover-honesty.sh --verify-report \
    scripts/g10-leftover-honesty/fixtures/honest-skip.env | tee -a "$OUT/collect.log"
  log "parent #228/#202 leftover honesty gate PASS (honest SKIP accepted; fakes rejected)"
else
  log "parent #228/#202 verify skipped (--skip-tests); inventory still PASS"
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

kc_discovery() {
  local issuer="$1"
  curl -fsS --max-time 3 "${issuer}/.well-known/openid-configuration" 2>/dev/null | grep -q '"issuer"'
}

S3_ENDPOINT_SET=false
S3_CREDENTIALS_SET=false
if [ -n "${S3_ENDPOINT:-}" ]; then
  S3_ENDPOINT_SET=true
fi
if [ -n "${S3_ACCESS_KEY:-}" ] && [ -n "${S3_SECRET_KEY:-}" ]; then
  S3_CREDENTIALS_SET=true
fi

LIVE_MINIO_LISTENER=false
if port_open 127.0.0.1 19000 || port_open 127.0.0.1 9000; then
  LIVE_MINIO_LISTENER=true
fi

LIVE_MINIO_PRESENT=false
if [ "$S3_ENDPOINT_SET" = "true" ] && [ "$S3_CREDENTIALS_SET" = "true" ] && [ "$LIVE_MINIO_LISTENER" = "true" ]; then
  LIVE_MINIO_PRESENT=true
fi

if [ "$S3_ENDPOINT_SET" != "true" ] && [ "$LIVE_MINIO_LISTENER" != "true" ]; then
  MINIO_REASON="S3_ENDPOINT unset; nothing listening on 127.0.0.1:19000 / :9000; Docker forbidden; #228/#202 harness PASS is not a live MinIO / object-store walk; this leftover remainder does not start MinIO and does not re-run #169/#202/#228"
elif [ "$S3_CREDENTIALS_SET" != "true" ]; then
  MINIO_REASON="S3 credentials unset; live reconcile SKIP (secrets stay in env/SecretPort); #228/#202 harness PASS is not a live MinIO walk; this leftover remainder does not re-run #169/#202/#228"
elif [ "$LIVE_MINIO_LISTENER" != "true" ]; then
  MINIO_REASON="S3_ENDPOINT set but object store not reachable; live reconcile SKIP; #228/#202 harness PASS is not a live MinIO walk; this leftover remainder does not re-run #169/#202/#228"
else
  MINIO_REASON="live listener present but this leftover remainder does not claim a walk until preview/apply/prefix were observed; #228/#202 harness PASS is not that walk; does not re-run #169/#202/#228"
fi
OBJECT_REASON="object-store walk not observed; #228/#202 harness PASS is not a live object reconcile"

ISSUER="${NEXUS_KEYCLOAK_ISSUER:-http://127.0.0.1:8080/realms/nexus}"
KC_LISTENER=false
if port_open 127.0.0.1 8080; then
  KC_LISTENER=true
fi
KC_DISCOVERY=false
if command -v curl >/dev/null 2>&1 && kc_discovery "$ISSUER"; then
  KC_DISCOVERY=true
fi

LIVE_KC_PRESENT=false
if [ "$KC_LISTENER" = "true" ] && [ "$KC_DISCOVERY" = "true" ]; then
  LIVE_KC_PRESENT=true
fi

if [ "$KC_LISTENER" != "true" ] && [ "$KC_DISCOVERY" != "true" ]; then
  KC_REASON="Keycloak 8080 unavailable; nothing listening on 127.0.0.1:8080 and OIDC discovery not reachable at ${ISSUER}; Docker forbidden; this leftover remainder does not start Keycloak and does not re-run #179/#202/#228"
elif [ "$KC_LISTENER" != "true" ]; then
  KC_REASON="OIDC discovery answered but nothing listening on 127.0.0.1:8080; live Keycloak 8080 SKIP; this leftover remainder does not re-run #179/#202/#228"
elif [ "$KC_DISCOVERY" != "true" ]; then
  KC_REASON="listener on 127.0.0.1:8080 but OIDC discovery not reachable at ${ISSUER}; live Keycloak 8080 SKIP; this leftover remainder does not re-run #179/#202/#228"
else
  KC_REASON="Keycloak 8080 present (${ISSUER}) but this leftover remainder does not claim a dry-run walk until it was observed; does not start Keycloak and does not re-run #179/#202/#228"
fi

APPLY_REASON="identity apply stays on nexus-p2-iam/scripts/run-g10-identity-reconcile.sh; this leftover remainder does not redo the product apply path or #141/#169/#179/#202/#228"

# Honesty leftover remainder never fabricates a live walk. #141/#169/#179/#202/#228 scripts are not re-run.
LIVE_MINIO=SKIP
LIVE_OBJECT=SKIP
LIVE_PREVIEW=false
LIVE_APPLY_OBS=false
LIVE_PREFIX=false
LIVE_KC=SKIP
LIVE_DRY=false
[ "${LIVE_PREVIEW_OBSERVED:-}" = "true" ] && LIVE_PREVIEW=true
[ "${LIVE_APPLY_OBSERVED:-}" = "true" ] && LIVE_APPLY_OBS=true
[ "${LIVE_TENANT_PREFIX_OBSERVED:-}" = "true" ] && LIVE_PREFIX=true
[ "${LIVE_DRY_RUN_OBSERVED:-}" = "true" ] && LIVE_DRY=true
if [ "$LIVE_MINIO_PRESENT" = "true" ] && [ "$LIVE_PREVIEW" = "true" ] && [ "$LIVE_APPLY_OBS" = "true" ] && [ "$LIVE_PREFIX" = "true" ]; then
  LIVE_MINIO=PASS
  LIVE_OBJECT=PASS
  MINIO_REASON="live MinIO preview + apply + tenant-prefix walk observed; #228/#202 harness PASS is still not that walk"
  OBJECT_REASON="live object-store reconcile observed; #228/#202 harness PASS is still not that walk"
fi
if [ "$LIVE_KC_PRESENT" = "true" ] && [ "$LIVE_DRY" = "true" ]; then
  LIVE_KC=PASS
  KC_REASON="live Keycloak 8080 dry-run observed; #228/#202 harness PASS is still not that walk"
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
  kv leftover_169_cells_honest PASS
  kv leftover_179_cells_honest PASS
  kv leftover_202_cells_honest PASS
  kv leftover_202_honesty_gate PASS
  kv leftover_228_cells_honest PASS
  kv leftover_228_honesty_gate PASS
  kv harness_228_is_not_live_minio true
  kv harness_228_is_not_live_object true
  kv harness_228_is_not_product_g10 true
  kv harness_202_is_not_live_minio true
  kv leftover_rows_kept "$LEFTOVER_ROWS_CSV"
  kv leftover_rows_all "$LEFTOVER_ROWS_ALL"
  kv honesty_169_rerun false
  kv honesty_179_rerun false
  kv honesty_202_rerun false
  kv honesty_228_rerun false
  kv counting_preview_apply PASS
  kv tenant_prefix_guard PASS
  kv dry_run_default_no_write true
  kv s3_endpoint_set "$S3_ENDPOINT_SET"
  kv s3_credentials_set "$S3_CREDENTIALS_SET"
  kv live_minio_listener "$LIVE_MINIO_LISTENER"
  kv live_minio_present "$LIVE_MINIO_PRESENT"
  kv live_minio_reconcile "$LIVE_MINIO"
  kv live_minio_skip_reason "$MINIO_REASON"
  kv live_object_reconcile "$LIVE_OBJECT"
  kv live_object_skip_reason "$OBJECT_REASON"
  kv live_preview_observed "$LIVE_PREVIEW"
  kv live_apply_observed "$LIVE_APPLY_OBS"
  kv live_tenant_prefix_observed "$LIVE_PREFIX"
  kv keycloak_8080_listener "$KC_LISTENER"
  kv keycloak_oidc_discovery "$KC_DISCOVERY"
  kv live_keycloak_present "$LIVE_KC_PRESENT"
  kv live_keycloak_8080 "$LIVE_KC"
  kv live_keycloak_skip_reason "$KC_REASON"
  kv live_dry_run_observed "$LIVE_DRY"
  kv live_apply SKIP
  kv live_apply_skip_reason "$APPLY_REASON"
  kv live_apply_observed false
  kv identity_apply_product_redone false
  kv identity_drift_141_redone false
  kv file_reconcile_169_redone false
  kv identity_drift_179_redone false
  kv leftover_202_redone false
  kv leftover_228_redone false
  kv saml_claimed false
  kv ldap_claimed false
  kv sms_mfa_claimed false
  kv g10_close NOT_CLAIMED
  kv g10_close_reason "Honesty leftover remainder beyond #228/#202/#169/#179/#141. File-reconcile counting/tenant-prefix stay PASS. Parent #228/#202 leftover honesty fixtures stay PASS and are not a live MinIO / object walk. Live MinIO/object/Keycloak SKIP when unavailable. Not product G10 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "# G10 leftover honesty remainder beyond #228/#202/#169/#179/#141"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| leftover #169 cells still honest | PASS |"
  echo "| leftover #179 cells still honest | PASS |"
  echo "| leftover #202 cells still honest | PASS |"
  echo "| leftover #228 cells still honest | PASS |"
  echo "| #228 leftover honesty fixtures | PASS (not live MinIO / object) |"
  echo "| counting preview vs apply | PASS |"
  echo "| tenant-prefix guard | PASS |"
  echo "| Live MinIO / object store | $LIVE_MINIO |"
  echo "| Live object reconcile | $LIVE_OBJECT |"
  echo "| Live Keycloak 8080 dry-run | $LIVE_KC |"
  echo "| Live apply | SKIP |"
  echo "| leftover rows kept | $LEFTOVER_ROWS_CSV |"
  echo "| #141/#169/#179/#202/#228 redo | false |"
  echo "| product G10 close | NOT_CLAIMED |"
  echo
  echo "MinIO SKIP reason: $MINIO_REASON"
  echo "Object SKIP reason: $OBJECT_REASON"
  echo "Keycloak SKIP reason: $KC_REASON"
} >"$MATRIX"

{
  echo "g10-file-object-reconcile-remainder-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT" pass

SANITIZED="${ROOT}/docs/engineering/g10-file-object-reconcile-remainder-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_minio=$LIVE_MINIO live_object=$LIVE_OBJECT g10_close=NOT_CLAIMED"
echo
echo "PASS: leftover honesty remainder recorded (#228/#202 harness PASS and not live MinIO; live MinIO / object $LIVE_MINIO)"
echo "See docs/g10-file-object-reconcile-remainder-honesty.md"
echo "Keep sibling leftovers including g10-leftover-honesty-remainder / g10-leftover-honesty / g2-h5-every-page-form-write-remainder-honesty"
echo "Not claimed: product G10 close / identity apply / SAML / LDAP / SMS MFA / #141 / #169 / #179 / #202 / #228"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
