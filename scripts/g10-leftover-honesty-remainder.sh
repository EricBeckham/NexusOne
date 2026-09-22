#!/usr/bin/env bash
# G10 leftover remainder: file-reconcile / identity-drift PASS/SKIP honesty
# beyond #202 / #169 / #179 / #141.
# Does not redo #141/#169/#179/#202 product code.
# Does not claim product G10 close. No SAML/LDAP/SMS MFA.
# G18 stays closed. P0/M0 not redone. AuditPort same-TX untouched. No Docker.
# Live MinIO / Keycloak is PASS only when the endpoint is up AND the walk
# was observed. Missing listener is honest SKIP. Never fabricates PASS.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""

FILE_169="$ROOT/docs/engineering/g10-file-reconcile-honesty/agent-vm.env"
ID_179="$ROOT/docs/engineering/g10-identity-drift-honesty/agent-vm.env"
FILE_202="$ROOT/docs/engineering/g10-leftover-honesty/agent-vm.env"
README="$ROOT/docs/README.md"

REQUIRED_LEFTOVER_ROWS="g10-identity-drift-dry-run g10-file-reconcile-honesty g10-identity-drift-honesty g10-leftover-honesty g10-leftover-honesty-remainder s0-secret-rotate-getenv-remainder-honesty g4-pkce-spa-verifier-remainder-honesty g17-separation-leftover-honesty g15-wx-dt-native-bridge-remainder g1-queryast-export-honesty g17-gm-tls-nginx-handshake-honesty g4-export-wizard-deny-honesty g2-h5-every-page-form-write-honesty g9-redis-down-invalidate-remainder g17-mfa-recovery-vue-otp-honesty g2-ops-pg-dump-honesty g2-report-physical-dump-honesty g9-sentinel-cluster-remainder-honesty g5-publishhook-ddl-honesty linux-arm-profile-honesty linux-arm-profile-leftover-honesty g7-query-expense-travel-honesty g7-withdraw-browser-honesty g4-hot-purge-90d g4-hot-purge-90d-honesty g4-p1-staging-audit-events g4-p1-staging-audit-events-honesty g6-recovery-port-dry-run g6-recovery-port-honesty g6-recovery-port-dry-run-honesty g6-recovery-port-dry-run-remainder-honesty s0s1-live-redis-minio-isolation-honesty g17-separation-of-duties g17-separation-honesty g1-fieldpolicy-mask-second-auth g1-fieldpolicy-mask-second-auth-honesty g1-queryast-boundsql-honesty g3-outbox-consume-crash-honesty g5-mid-publish-failure-honesty g15-native-bridge-honesty g15-wx-dt-native-bridge-honesty g4-pkce-spa-verifier-honesty s0-secret-getenv-snapshot-honesty s0-secret-rotate-without-restart-honesty g5-browser-failed-publish"

usage() {
  cat <<'EOF'
Usage:
  scripts/g10-leftover-honesty-remainder.sh [--out DIR]
  scripts/g10-leftover-honesty-remainder.sh --verify-report FILE

Beyond #202/#169/#179/#141: leftover #169/#179/#202 cells stay honest;
parent leftover honesty fixtures stay PASS. Live MinIO / Keycloak is
PASS only when present AND the walk was observed. Missing listener is
honest SKIP. Does not re-run #141/#169/#179/#202. Does not claim
product G10 close.

  --out DIR           Output directory (default: .local/g10-leftover-honesty-remainder/<utc>)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
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

artifact_get() {
  local file="$1" key="$2"
  awk -F= -v k="$key" '$1==k {sub(/^[^=]+=/, ""); print; exit}' "$file"
}

verify_report() {
  local file="$1"
  [ -f "$file" ] || fail "report not found: $file"
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_169_cells_honest leftover_179_cells_honest leftover_202_cells_honest leftover_202_honesty_gate leftover_rows_kept leftover_rows leftover_141_kept leftover_169_kept leftover_179_kept leftover_202_kept leftover_203_kept leftover_204_kept leftover_197_kept leftover_205_kept leftover_188_kept leftover_209_kept leftover_217_kept leftover_210_kept leftover_215_kept leftover_220_kept leftover_216_kept leftover_222_kept leftover_223_kept leftover_225_kept leftover_221_kept leftover_191_kept leftover_192_kept leftover_193_kept leftover_195_kept leftover_196_kept leftover_198_kept leftover_200_kept counting_preview_apply tenant_prefix_guard dry_run_default_no_write committed_169_live_minio committed_169_g10_close committed_179_live_keycloak committed_179_live_apply committed_179_g10_close committed_202_live_minio committed_202_live_keycloak committed_202_g10_close s3_endpoint_set s3_credentials_set live_minio_listener live_minio_present live_minio_reconcile live_minio_skip_reason live_preview_observed live_apply_observed live_tenant_prefix_observed keycloak_8080_listener keycloak_oidc_discovery live_keycloak_present live_keycloak_8080 live_keycloak_skip_reason live_dry_run_observed live_apply live_apply_skip_reason live_apply_observed identity_apply_product_redone identity_drift_141_redone file_reconcile_169_redone identity_drift_179_redone leftover_202_redone saml_claimed ldap_claimed sms_mfa_claimed g10_close g18_status audit_port_changed p0_m0_redone honesty_169_rerun honesty_179_rerun honesty_202_rerun"
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
  [ "$(report_get "$file" identity_drift_141_redone)" = "false" ] || fail "must not redo #141"
  [ "$(report_get "$file" file_reconcile_169_redone)" = "false" ] || fail "must not redo #169"
  [ "$(report_get "$file" identity_drift_179_redone)" = "false" ] || fail "must not redo #179"
  [ "$(report_get "$file" leftover_202_redone)" = "false" ] || fail "must not redo #202"
  [ "$(report_get "$file" honesty_169_rerun)" = "false" ] || fail "honesty_169_rerun must be false"
  [ "$(report_get "$file" honesty_179_rerun)" = "false" ] || fail "honesty_179_rerun must be false"
  [ "$(report_get "$file" honesty_202_rerun)" = "false" ] || fail "honesty_202_rerun must be false (do not redo #202)"
  [ "$(report_get "$file" identity_apply_product_redone)" = "false" ] || fail "identity_apply_product_redone must stay false"
  [ "$(report_get "$file" saml_claimed)" = "false" ] || fail "saml_claimed must stay false"
  [ "$(report_get "$file" ldap_claimed)" = "false" ] || fail "ldap_claimed must stay false"
  [ "$(report_get "$file" sms_mfa_claimed)" = "false" ] || fail "sms_mfa_claimed must stay false"
  [ "$(report_get "$file" g10_close)" = "NOT_CLAIMED" ] || fail "g10_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" leftover_169_cells_honest)" = "PASS" ] || fail "leftover_169_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_179_cells_honest)" = "PASS" ] || fail "leftover_179_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_202_cells_honest)" = "PASS" ] || fail "leftover_202_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_202_honesty_gate)" = "PASS" ] || fail "leftover_202_honesty_gate must be PASS"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" leftover_141_kept)" = "true" ] || fail "leftover_141_kept must stay true"
  [ "$(report_get "$file" leftover_169_kept)" = "true" ] || fail "leftover_169_kept must stay true"
  [ "$(report_get "$file" leftover_179_kept)" = "true" ] || fail "leftover_179_kept must stay true"
  [ "$(report_get "$file" leftover_202_kept)" = "true" ] || fail "leftover_202_kept must stay true (keep g10-leftover-honesty)"
  [ "$(report_get "$file" leftover_203_kept)" = "true" ] || fail "leftover_203_kept must stay true"
  [ "$(report_get "$file" leftover_204_kept)" = "true" ] || fail "leftover_204_kept must stay true"
  [ "$(report_get "$file" leftover_197_kept)" = "true" ] || fail "leftover_197_kept must stay true"
  [ "$(report_get "$file" leftover_205_kept)" = "true" ] || fail "leftover_205_kept must stay true"
  [ "$(report_get "$file" leftover_188_kept)" = "true" ] || fail "leftover_188_kept must stay true"
  [ "$(report_get "$file" leftover_209_kept)" = "true" ] || fail "leftover_209_kept must stay true"
  [ "$(report_get "$file" leftover_217_kept)" = "true" ] || fail "leftover_217_kept must stay true"
  [ "$(report_get "$file" leftover_210_kept)" = "true" ] || fail "leftover_210_kept must stay true"
  [ "$(report_get "$file" leftover_215_kept)" = "true" ] || fail "leftover_215_kept must stay true (keep g1-queryast-export-honesty)"
  [ "$(report_get "$file" leftover_220_kept)" = "true" ] || fail "leftover_220_kept must stay true (keep g15-wx-dt-native-bridge-remainder)"
  [ "$(report_get "$file" leftover_216_kept)" = "true" ] || fail "leftover_216_kept must stay true (keep g17-separation-leftover-honesty)"
  [ "$(report_get "$file" leftover_222_kept)" = "true" ] || fail "leftover_222_kept must stay true (keep g4-pkce-spa-verifier-remainder-honesty)"
  [ "$(report_get "$file" leftover_223_kept)" = "true" ] || fail "leftover_223_kept must stay true (keep s0-secret-rotate-getenv-remainder-honesty)"
  [ "$(report_get "$file" leftover_225_kept)" = "true" ] || fail "leftover_225_kept must stay true (keep g6-recovery-port-dry-run-remainder-honesty)"
  [ "$(report_get "$file" leftover_221_kept)" = "true" ] || fail "leftover_221_kept must stay true (keep g2-report-physical-dump-honesty)"
  [ "$(report_get "$file" leftover_191_kept)" = "true" ] || fail "leftover_191_kept must stay true"
  [ "$(report_get "$file" leftover_192_kept)" = "true" ] || fail "leftover_192_kept must stay true"
  [ "$(report_get "$file" leftover_193_kept)" = "true" ] || fail "leftover_193_kept must stay true"
  [ "$(report_get "$file" leftover_195_kept)" = "true" ] || fail "leftover_195_kept must stay true"
  [ "$(report_get "$file" leftover_196_kept)" = "true" ] || fail "leftover_196_kept must stay true"
  [ "$(report_get "$file" leftover_198_kept)" = "true" ] || fail "leftover_198_kept must stay true"
  [ "$(report_get "$file" leftover_200_kept)" = "true" ] || fail "leftover_200_kept must stay true"
  [ "$(report_get "$file" counting_preview_apply)" = "PASS" ] || fail "counting_preview_apply must be PASS"
  [ "$(report_get "$file" tenant_prefix_guard)" = "PASS" ] || fail "tenant_prefix_guard must be PASS"
  [ "$(report_get "$file" dry_run_default_no_write)" = "true" ] || fail "dry_run_default_no_write must be true"
  [ "$(report_get "$file" committed_169_g10_close)" = "NOT_CLAIMED" ] || fail "committed #169 g10_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" committed_179_g10_close)" = "NOT_CLAIMED" ] || fail "committed #179 g10_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" committed_202_g10_close)" = "NOT_CLAIMED" ] || fail "committed #202 g10_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" committed_179_live_apply)" = "SKIP" ] || fail "committed #179 live_apply must stay SKIP"
  [ "$(report_get "$file" live_apply)" = "SKIP" ] || fail "live_apply must stay SKIP (do not redo product apply)"
  [ "$(report_get "$file" live_apply_observed)" = "false" ] || fail "live_apply_observed must stay false"
  [ -n "$(report_get "$file" live_apply_skip_reason)" ] || fail "live_apply SKIP needs live_apply_skip_reason"

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $REQUIRED_LEFTOVER_ROWS; do
    echo "$rows" | grep -Fq "$required_row" || fail "leftover_rows must keep $required_row"
  done

  local live
  live="$(report_get "$file" live_minio_reconcile)"
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) fail "live_minio_reconcile must be PASS/SKIP/FAIL" ;;
  esac
  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" live_minio_present)" = "true" ] || fail "live_minio_reconcile=PASS requires live_minio_present=true"
    [ "$(report_get "$file" s3_credentials_set)" = "true" ] || fail "live_minio_reconcile=PASS requires s3_credentials_set=true"
    [ "$(report_get "$file" live_preview_observed)" = "true" ] || fail "live_minio_reconcile=PASS requires live_preview_observed=true"
    [ "$(report_get "$file" live_apply_observed)" = "true" ] || fail "live_minio_reconcile=PASS requires live_apply_observed=true"
    [ "$(report_get "$file" live_tenant_prefix_observed)" = "true" ] || fail "live_minio_reconcile=PASS requires live_tenant_prefix_observed=true"
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_minio_skip_reason)" ] || fail "live MinIO SKIP needs live_minio_skip_reason"
    [ "$(report_get "$file" live_preview_observed)" != "true" ] || fail "SKIP cannot claim a live preview walk"
    [ "$(report_get "$file" live_apply_observed)" != "true" ] || fail "SKIP cannot claim a live apply walk"
    [ "$(report_get "$file" live_tenant_prefix_observed)" != "true" ] || fail "SKIP cannot claim a live tenant-prefix walk"
  fi

  live="$(report_get "$file" live_keycloak_8080)"
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) fail "live_keycloak_8080 must be PASS/SKIP/FAIL" ;;
  esac
  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" live_keycloak_present)" = "true" ] || fail "live_keycloak_8080=PASS requires live_keycloak_present=true"
    [ "$(report_get "$file" keycloak_oidc_discovery)" = "true" ] || fail "live_keycloak_8080=PASS requires keycloak_oidc_discovery=true"
    [ "$(report_get "$file" live_dry_run_observed)" = "true" ] || fail "live_keycloak_8080=PASS requires live_dry_run_observed=true"
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_keycloak_skip_reason)" ] || fail "live Keycloak SKIP needs live_keycloak_skip_reason"
    [ "$(report_get "$file" live_dry_run_observed)" != "true" ] || fail "SKIP cannot claim a live dry-run walk"
  fi

  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=|CLIENT_SECRET=' "$file"; then
    fail "report must not contain password/secret material"
  fi
  echo "PASS: report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [[ "${NEXUS_G10_APPLY:-}" == "1" || "${NEXUS_G10_APPLY:-}" == "true" ]]; then
  echo "REFUSE: this leftover remainder is honesty around file-reconcile / identity-drift PASS/SKIP (no writes)." >&2
  echo "Apply stays on nexus-p2-iam/scripts/run-g10-identity-reconcile.sh" >&2
  exit 2
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g10-leftover-honesty-remainder/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G10 leftover honesty remainder beyond #202/#169/#179/#141"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #141/#169/#179/#202. Not product G10 close. G18 stays closed."
echo

[ -f "$FILE_169" ] || fail "missing committed #169 artifact $FILE_169"
[ -f "$ID_179" ] || fail "missing committed #179 artifact $ID_179"
[ -f "$FILE_202" ] || fail "missing committed #202 artifact $FILE_202"
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
C169_141="$(artifact_get "$FILE_169" identity_drift_141_redone)"
C169_G6="$(artifact_get "$FILE_169" g6_object_restore_redone)"

[ "$C169_COUNTING" = "PASS" ] || fail "committed #169 counting_preview_apply is $C169_COUNTING; must stay PASS"
[ "$C169_PREFIX" = "PASS" ] || fail "committed #169 tenant_prefix_guard is $C169_PREFIX; must stay PASS"
[ "$C169_LIVE" = "SKIP" ] || fail "committed #169 live_minio_reconcile is $C169_LIVE; leftover requires honest SKIP on that artifact"
[ "$C169_PREVIEW" = "false" ] || fail "committed #169 live_preview_observed is $C169_PREVIEW; SKIP cannot claim a walk"
[ "$C169_APPLY" = "false" ] || fail "committed #169 live_apply_observed is $C169_APPLY; SKIP cannot claim a walk"
[ "$C169_G10" = "NOT_CLAIMED" ] || fail "committed #169 g10_close is $C169_G10"
[ "$C169_141" = "false" ] || fail "committed #169 identity_drift_141_redone is $C169_141"
[ "$C169_G6" = "false" ] || fail "committed #169 g6_object_restore_redone is $C169_G6"

C179_DRY="$(artifact_get "$ID_179" dry_run_default_no_write)"
C179_KC="$(artifact_get "$ID_179" live_keycloak_8080)"
C179_DRY_OBS="$(artifact_get "$ID_179" live_dry_run_observed)"
C179_APPLY="$(artifact_get "$ID_179" live_apply)"
C179_APPLY_OBS="$(artifact_get "$ID_179" live_apply_observed)"
C179_G10="$(artifact_get "$ID_179" g10_close)"
C179_141="$(artifact_get "$ID_179" identity_drift_141_redone)"
C179_169="$(artifact_get "$ID_179" file_reconcile_169_redone)"
C179_SAML="$(artifact_get "$ID_179" saml_claimed)"
C179_LDAP="$(artifact_get "$ID_179" ldap_claimed)"
C179_SMS="$(artifact_get "$ID_179" sms_mfa_claimed)"

[ "$C179_DRY" = "true" ] || fail "committed #179 dry_run_default_no_write is $C179_DRY"
[ "$C179_KC" = "SKIP" ] || fail "committed #179 live_keycloak_8080 is $C179_KC; leftover requires honest SKIP on that artifact"
[ "$C179_DRY_OBS" = "false" ] || fail "committed #179 live_dry_run_observed is $C179_DRY_OBS"
[ "$C179_APPLY" = "SKIP" ] || fail "committed #179 live_apply is $C179_APPLY; must stay SKIP"
[ "$C179_APPLY_OBS" = "false" ] || fail "committed #179 live_apply_observed is $C179_APPLY_OBS"
[ "$C179_G10" = "NOT_CLAIMED" ] || fail "committed #179 g10_close is $C179_G10"
[ "$C179_141" = "false" ] || fail "committed #179 identity_drift_141_redone is $C179_141"
[ "$C179_169" = "false" ] || fail "committed #179 file_reconcile_169_redone is $C179_169"
[ "$C179_SAML" = "false" ] || fail "committed #179 saml_claimed is $C179_SAML"
[ "$C179_LDAP" = "false" ] || fail "committed #179 ldap_claimed is $C179_LDAP"
[ "$C179_SMS" = "false" ] || fail "committed #179 sms_mfa_claimed is $C179_SMS"

C202_169="$(artifact_get "$FILE_202" leftover_169_cells_honest)"
C202_179="$(artifact_get "$FILE_202" leftover_179_cells_honest)"
C202_MINIO="$(artifact_get "$FILE_202" live_minio_reconcile)"
C202_KC="$(artifact_get "$FILE_202" live_keycloak_8080)"
C202_APPLY="$(artifact_get "$FILE_202" live_apply)"
C202_G10="$(artifact_get "$FILE_202" g10_close)"
C202_PREVIEW="$(artifact_get "$FILE_202" live_preview_observed)"
C202_DRY="$(artifact_get "$FILE_202" live_dry_run_observed)"
C202_141="$(artifact_get "$FILE_202" identity_drift_141_redone)"
C202_169R="$(artifact_get "$FILE_202" file_reconcile_169_redone)"
C202_179R="$(artifact_get "$FILE_202" identity_drift_179_redone)"
C202_SAML="$(artifact_get "$FILE_202" saml_claimed)"
C202_G18="$(artifact_get "$FILE_202" g18_status)"
C202_AUDIT="$(artifact_get "$FILE_202" audit_port_changed)"
C202_P0="$(artifact_get "$FILE_202" p0_m0_redone)"

[ "$C202_169" = "PASS" ] || fail "committed #202 leftover_169_cells_honest is $C202_169"
[ "$C202_179" = "PASS" ] || fail "committed #202 leftover_179_cells_honest is $C202_179"
[ "$C202_MINIO" = "SKIP" ] || fail "committed #202 live_minio_reconcile is $C202_MINIO; leftover requires honest SKIP on that artifact"
[ "$C202_KC" = "SKIP" ] || fail "committed #202 live_keycloak_8080 is $C202_KC; leftover requires honest SKIP on that artifact"
[ "$C202_APPLY" = "SKIP" ] || fail "committed #202 live_apply is $C202_APPLY"
[ "$C202_G10" = "NOT_CLAIMED" ] || fail "committed #202 g10_close is $C202_G10"
[ "$C202_PREVIEW" = "false" ] || fail "committed #202 live_preview_observed is $C202_PREVIEW"
[ "$C202_DRY" = "false" ] || fail "committed #202 live_dry_run_observed is $C202_DRY"
[ "$C202_141" = "false" ] || fail "committed #202 identity_drift_141_redone is $C202_141"
[ "$C202_169R" = "false" ] || fail "committed #202 file_reconcile_169_redone is $C202_169R"
[ "$C202_179R" = "false" ] || fail "committed #202 identity_drift_179_redone is $C202_179R"
[ "$C202_SAML" = "false" ] || fail "committed #202 saml_claimed is $C202_SAML"
[ "$C202_G18" = "closed_unchanged" ] || fail "committed #202 g18_status is $C202_G18"
[ "$C202_AUDIT" = "false" ] || fail "committed #202 audit_port_changed is $C202_AUDIT"
[ "$C202_P0" = "false" ] || fail "committed #202 p0_m0_redone is $C202_P0"

log "verify parent #202 leftover honesty fixtures (harness PASS; do not re-run collect)"
./scripts/g10-leftover-honesty.sh --verify-report \
  scripts/g10-leftover-honesty/fixtures/honest-skip.env | tee -a "$OUT/collect.log"
set +e
./scripts/g10-leftover-honesty.sh --verify-report \
  scripts/g10-leftover-honesty/fixtures/fake-live-minio-pass.env >"$OUT/fake-202-minio.log" 2>&1
FAKE202_MINIO=$?
./scripts/g10-leftover-honesty.sh --verify-report \
  scripts/g10-leftover-honesty/fixtures/fake-live-keycloak-pass.env >"$OUT/fake-202-keycloak.log" 2>&1
FAKE202_KC=$?
./scripts/g10-leftover-honesty.sh --verify-report \
  scripts/g10-leftover-honesty/fixtures/fake-live-apply-pass.env >"$OUT/fake-202-apply.log" 2>&1
FAKE202_APPLY=$?
./scripts/g10-leftover-honesty.sh --verify-report \
  scripts/g10-leftover-honesty/fixtures/fake-g10-close.env >"$OUT/fake-202-close.log" 2>&1
FAKE202_CLOSE=$?
set -e
[ "$FAKE202_MINIO" -ne 0 ] || fail "expected #202 fake-live-minio-pass.env to FAIL"
[ "$FAKE202_KC" -ne 0 ] || fail "expected #202 fake-live-keycloak-pass.env to FAIL"
[ "$FAKE202_APPLY" -ne 0 ] || fail "expected #202 fake-live-apply-pass.env to FAIL"
[ "$FAKE202_CLOSE" -ne 0 ] || fail "expected #202 fake-g10-close.env to FAIL"
log "parent #202 leftover honesty gate PASS (honest SKIP accepted; fakes rejected)"

MISSING_ROWS=""
LEFTOVER_ROWS_KEPT=true
LEFTOVER_141_KEPT=false
LEFTOVER_169_KEPT=false
LEFTOVER_179_KEPT=false
LEFTOVER_202_KEPT=false
LEFTOVER_203_KEPT=false
LEFTOVER_204_KEPT=false
LEFTOVER_197_KEPT=false
LEFTOVER_205_KEPT=false
LEFTOVER_188_KEPT=false
LEFTOVER_209_KEPT=false
LEFTOVER_217_KEPT=false
LEFTOVER_210_KEPT=false
LEFTOVER_215_KEPT=false
LEFTOVER_220_KEPT=false
LEFTOVER_216_KEPT=false
LEFTOVER_222_KEPT=false
LEFTOVER_223_KEPT=false
LEFTOVER_225_KEPT=false
LEFTOVER_221_KEPT=false
LEFTOVER_191_KEPT=false
LEFTOVER_192_KEPT=false
LEFTOVER_193_KEPT=false
LEFTOVER_195_KEPT=false
LEFTOVER_196_KEPT=false
LEFTOVER_198_KEPT=false
LEFTOVER_200_KEPT=false
for required_row in $REQUIRED_LEFTOVER_ROWS; do
  if grep -Fq "[${required_row}](./${required_row}.md)" "$README"; then
    case "$required_row" in
      g10-identity-drift-dry-run) LEFTOVER_141_KEPT=true ;;
      g10-file-reconcile-honesty) LEFTOVER_169_KEPT=true ;;
      g10-identity-drift-honesty) LEFTOVER_179_KEPT=true ;;
      g10-leftover-honesty) LEFTOVER_202_KEPT=true ;;
      g4-export-wizard-deny-honesty) LEFTOVER_203_KEPT=true ;;
      g2-h5-every-page-form-write-honesty) LEFTOVER_204_KEPT=true ;;
      g9-redis-down-invalidate-remainder) LEFTOVER_197_KEPT=true ;;
      g17-mfa-recovery-vue-otp-honesty) LEFTOVER_205_KEPT=true ;;
      g2-ops-pg-dump-honesty) LEFTOVER_188_KEPT=true ;;
      g9-sentinel-cluster-remainder-honesty) LEFTOVER_209_KEPT=true ;;
      g5-publishhook-ddl-honesty) LEFTOVER_217_KEPT=true ;;
      g17-gm-tls-nginx-handshake-honesty) LEFTOVER_210_KEPT=true ;;
      g1-queryast-export-honesty) LEFTOVER_215_KEPT=true ;;
      g15-wx-dt-native-bridge-remainder) LEFTOVER_220_KEPT=true ;;
      g17-separation-leftover-honesty) LEFTOVER_216_KEPT=true ;;
      g4-pkce-spa-verifier-remainder-honesty) LEFTOVER_222_KEPT=true ;;
      s0-secret-rotate-getenv-remainder-honesty) LEFTOVER_223_KEPT=true ;;
      g6-recovery-port-dry-run-remainder-honesty) LEFTOVER_225_KEPT=true ;;
      g2-report-physical-dump-honesty) LEFTOVER_221_KEPT=true ;;
      g1-queryast-boundsql-honesty) LEFTOVER_191_KEPT=true ;;
      g7-query-expense-travel-honesty) LEFTOVER_192_KEPT=true ;;
      linux-arm-profile-leftover-honesty) LEFTOVER_193_KEPT=true ;;
      g3-outbox-consume-crash-honesty) LEFTOVER_195_KEPT=true ;;
      g5-mid-publish-failure-honesty) LEFTOVER_196_KEPT=true ;;
      g15-wx-dt-native-bridge-honesty) LEFTOVER_198_KEPT=true ;;
      s0-secret-rotate-without-restart-honesty) LEFTOVER_200_KEPT=true ;;
    esac
  else
    LEFTOVER_ROWS_KEPT=false
    MISSING_ROWS="${MISSING_ROWS} ${required_row}"
  fi
done
[ "$LEFTOVER_ROWS_KEPT" = "true" ] || fail "docs/README.md dropped leftover row(s):$MISSING_ROWS"
[ "$LEFTOVER_141_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-identity-drift-dry-run (#141)"
[ "$LEFTOVER_169_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-file-reconcile-honesty (#169)"
[ "$LEFTOVER_179_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-identity-drift-honesty (#179)"
[ "$LEFTOVER_202_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-leftover-honesty (#202)"
[ "$LEFTOVER_220_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g15-wx-dt-native-bridge-remainder (#220)"
[ "$LEFTOVER_216_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-separation-leftover-honesty (#216)"
[ "$LEFTOVER_222_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-pkce-spa-verifier-remainder-honesty (#222)"
[ "$LEFTOVER_223_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover s0-secret-rotate-getenv-remainder-honesty (#223)"
[ "$LEFTOVER_225_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g6-recovery-port-dry-run-remainder-honesty (#225)"
[ "$LEFTOVER_221_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-report-physical-dump-honesty (#221)"
[ "$LEFTOVER_215_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g1-queryast-export-honesty (#215)"
if grep -Eiq '产品 G10 已关闭|product g10 closed|g10_close=CLOSED' "$README"; then
  fail "docs/README.md must not claim product G10 closed"
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
  MINIO_REASON="S3_ENDPOINT unset; nothing listening on 127.0.0.1:19000 / :9000; Docker forbidden; this leftover remainder does not start MinIO and does not re-run #169/#202"
elif [ "$S3_CREDENTIALS_SET" != "true" ]; then
  MINIO_REASON="S3 credentials unset; live reconcile SKIP (secrets stay in env/SecretPort); this leftover remainder does not re-run #169/#202"
elif [ "$LIVE_MINIO_LISTENER" != "true" ]; then
  MINIO_REASON="S3_ENDPOINT set but object store not reachable; live reconcile SKIP; this leftover remainder does not re-run #169/#202"
else
  MINIO_REASON="live listener present but this leftover remainder does not claim a walk until preview/apply/prefix were observed; does not re-run #169/#202"
fi

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
  KC_REASON="Keycloak 8080 unavailable; nothing listening on 127.0.0.1:8080 and OIDC discovery not reachable at ${ISSUER}; Docker forbidden; this leftover remainder does not start Keycloak and does not re-run #179/#202"
elif [ "$KC_LISTENER" != "true" ]; then
  KC_REASON="OIDC discovery answered but nothing listening on 127.0.0.1:8080; live Keycloak 8080 SKIP; this leftover remainder does not re-run #179/#202"
elif [ "$KC_DISCOVERY" != "true" ]; then
  KC_REASON="listener on 127.0.0.1:8080 but OIDC discovery not reachable at ${ISSUER}; live Keycloak 8080 SKIP; this leftover remainder does not re-run #179/#202"
else
  KC_REASON="Keycloak 8080 present (${ISSUER}) but this leftover remainder does not claim a dry-run walk until it was observed; does not start Keycloak and does not re-run #179/#202"
fi

APPLY_REASON="identity apply stays on nexus-p2-iam/scripts/run-g10-identity-reconcile.sh; this leftover remainder does not redo the product apply path or #141/#169/#179/#202"

# Honesty leftover remainder never fabricates a live walk. #141/#169/#179/#202 scripts are not re-run.
LIVE_MINIO=SKIP
LIVE_PREVIEW=false
LIVE_APPLY_OBS=false
LIVE_PREFIX=false
LIVE_KC=SKIP
LIVE_DRY=false

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
  kv leftover_rows_kept "$LEFTOVER_ROWS_KEPT"
  kv leftover_rows "$LEFTOVER_ROWS_CSV"
  kv leftover_141_kept "$LEFTOVER_141_KEPT"
  kv leftover_169_kept "$LEFTOVER_169_KEPT"
  kv leftover_179_kept "$LEFTOVER_179_KEPT"
  kv leftover_202_kept "$LEFTOVER_202_KEPT"
  kv leftover_203_kept "$LEFTOVER_203_KEPT"
  kv leftover_204_kept "$LEFTOVER_204_KEPT"
  kv leftover_197_kept "$LEFTOVER_197_KEPT"
  kv leftover_205_kept "$LEFTOVER_205_KEPT"
  kv leftover_188_kept "$LEFTOVER_188_KEPT"
  kv leftover_209_kept "$LEFTOVER_209_KEPT"
  kv leftover_217_kept "$LEFTOVER_217_KEPT"
  kv leftover_210_kept "$LEFTOVER_210_KEPT"
  kv leftover_215_kept "$LEFTOVER_215_KEPT"
  kv leftover_220_kept "$LEFTOVER_220_KEPT"
  kv leftover_216_kept "$LEFTOVER_216_KEPT"
  kv leftover_222_kept "$LEFTOVER_222_KEPT"
  kv leftover_223_kept "$LEFTOVER_223_KEPT"
  kv leftover_225_kept "$LEFTOVER_225_KEPT"
  kv leftover_221_kept "$LEFTOVER_221_KEPT"
  kv leftover_191_kept "$LEFTOVER_191_KEPT"
  kv leftover_192_kept "$LEFTOVER_192_KEPT"
  kv leftover_193_kept "$LEFTOVER_193_KEPT"
  kv leftover_195_kept "$LEFTOVER_195_KEPT"
  kv leftover_196_kept "$LEFTOVER_196_KEPT"
  kv leftover_198_kept "$LEFTOVER_198_KEPT"
  kv leftover_200_kept "$LEFTOVER_200_KEPT"
  kv honesty_169_rerun false
  kv honesty_179_rerun false
  kv honesty_202_rerun false
  kv counting_preview_apply PASS
  kv tenant_prefix_guard PASS
  kv dry_run_default_no_write true
  kv committed_169_live_minio "$C169_LIVE"
  kv committed_169_g10_close "$C169_G10"
  kv committed_179_live_keycloak "$C179_KC"
  kv committed_179_live_apply "$C179_APPLY"
  kv committed_179_g10_close "$C179_G10"
  kv committed_202_live_minio "$C202_MINIO"
  kv committed_202_live_keycloak "$C202_KC"
  kv committed_202_g10_close "$C202_G10"
  kv s3_endpoint_set "$S3_ENDPOINT_SET"
  kv s3_credentials_set "$S3_CREDENTIALS_SET"
  kv live_minio_listener "$LIVE_MINIO_LISTENER"
  kv live_minio_present "$LIVE_MINIO_PRESENT"
  kv live_minio_reconcile "$LIVE_MINIO"
  kv live_minio_skip_reason "$MINIO_REASON"
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
  kv saml_claimed false
  kv ldap_claimed false
  kv sms_mfa_claimed false
  kv g10_close NOT_CLAIMED
  kv g10_close_reason "Honesty leftover remainder beyond #202/#169/#179/#141. File-reconcile counting/tenant-prefix stay PASS. Identity dry-run stays no-write. Parent #202 leftover honesty fixtures stay PASS. Live MinIO/Keycloak SKIP when unavailable. Not product G10 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "# G10 leftover honesty remainder beyond #202/#169/#179/#141"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| leftover #169 cells still honest | PASS |"
  echo "| leftover #179 cells still honest | PASS |"
  echo "| leftover #202 cells still honest | PASS |"
  echo "| #202 leftover honesty fixtures | PASS |"
  echo "| counting preview vs apply | PASS |"
  echo "| tenant-prefix guard | PASS |"
  echo "| dry-run default no write | true |"
  echo "| Live MinIO / object store | SKIP |"
  echo "| Live Keycloak 8080 dry-run | SKIP |"
  echo "| Live apply | SKIP |"
  echo "| leftover rows kept | $LEFTOVER_ROWS_CSV |"
  echo "| #141/#169/#179/#202 redo | false |"
  echo "| product G10 close | NOT_CLAIMED |"
  echo
  echo "MinIO SKIP reason: $MINIO_REASON"
  echo "Keycloak SKIP reason: $KC_REASON"
} >"$MATRIX"

verify_report "$REPORT"
log "wrote $OUT/report.env"

SANITIZED="${ROOT}/docs/engineering/g10-leftover-honesty-remainder/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

echo
echo "PASS: leftover honesty remainder recorded (#169/#179/#202 cells honest; live MinIO SKIP; live Keycloak SKIP)"
echo "See docs/g10-leftover-honesty-remainder.md"
echo "Keep sibling leftovers: docs/g10-leftover-honesty.md, docs/g10-file-reconcile-honesty.md, docs/g10-identity-drift-honesty.md"
echo "Keep #220 g15-wx-dt-native-bridge-remainder, #215 g1-queryast-export-honesty, #210 g17-gm-tls-nginx-handshake-honesty"
echo "Not claimed: product G10 close / identity apply / SAML / LDAP / SMS MFA / #141 / #169 / #179 / #202"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
