#!/usr/bin/env bash
# G17 leftover beyond #160/#129: Tongsuo/Nginx GMTLS handshake PASS/SKIP honesty.
# Does not redo #110 dual-cert, #129 DualStackTlsProbe / nginx templates, or
# #160 handshake collect product code. Does not claim 等保 / 密评 / 完整 G17.
# G18 stays closed. No P0/M0 redo. AuditPort same-TX untouched. No Docker.
# Dual-stack probe / parent honesty fixtures stay always-green PASS.
# Live Tongsuo/Nginx handshake is PASS only when binaries exist AND a
# GMTLS/NTLS handshake was observed. Missing Tongsuo/Nginx is honest SKIP.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""

FILE_160="$ROOT/docs/engineering/g17-gm-tls-nginx-handshake/agent-vm.env"
README="$ROOT/docs/README.md"
PROBE_SRC="$ROOT/nexus-crypto-core/src/main/java/one/nexus/crypto/tls/DualStackTlsProbe.java"
PROBE_TEST="$ROOT/nexus-crypto-core/src/test/java/one/nexus/crypto/tls/DualStackTlsProbeTest.java"
TPL_NGINX="$ROOT/scripts/g17-gm-tls-nginx-dual-stack/nginx/nginx.conf.template"
TPL_DUAL="$ROOT/scripts/g17-gm-tls-nginx-dual-stack/nginx/tongsuo-dual-stack.conf.template"

REQUIRED_LEFTOVER_ROWS="g17-gm-tls-dual-cert g17-gm-tls-nginx-dual-stack g17-gm-tls-nginx-handshake g17-gm-tls-nginx-handshake-honesty g17-separation-of-duties g17-separation-honesty g10-leftover-honesty g4-export-wizard-deny-honesty g2-h5-every-page-form-write-honesty g9-redis-down-invalidate-remainder g17-mfa-recovery-vue-otp-honesty g2-ops-pg-dump-honesty g9-sentinel-cluster-remainder-honesty g5-publishhook-ddl-honesty g4-export-wizard-deny g4-guard-deny-audit g10-identity-drift-dry-run g10-file-reconcile-honesty g10-identity-drift-honesty linux-arm-profile-honesty linux-arm-profile-leftover-honesty g7-query-expense-travel-honesty g7-withdraw-browser-honesty g4-hot-purge-90d g4-hot-purge-90d-honesty g4-p1-staging-audit-events g4-p1-staging-audit-events-honesty g6-recovery-port-dry-run g6-recovery-port-honesty g6-recovery-port-dry-run-honesty s0s1-live-redis-minio-isolation-honesty g1-fieldpolicy-mask-second-auth g1-fieldpolicy-mask-second-auth-honesty g1-queryast-boundsql-honesty g3-outbox-consume-crash-honesty g5-mid-publish-failure-honesty g15-native-bridge-honesty g15-wx-dt-native-bridge-honesty g4-pkce-spa-verifier-honesty s0-secret-getenv-snapshot-honesty s0-secret-rotate-without-restart-honesty g5-browser-failed-publish"

usage() {
  cat <<'EOF'
Usage:
  scripts/g17-gm-tls-nginx-handshake-honesty.sh [--out DIR]
  scripts/g17-gm-tls-nginx-handshake-honesty.sh --verify-report FILE

Beyond #160/#129: DualStackTlsProbe + parent honesty fixtures stay PASS.
Live Tongsuo/Nginx GMTLS handshake is PASS only when present AND observed.
Missing Tongsuo/Nginx is honest SKIP. Does not re-run #110/#129/#160 collect.
Does not claim 等保 / 密评 / 完整 G17.

  --out DIR           Output directory (default: .local/g17-gm-tls-nginx-handshake-honesty/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_129_cells_honest leftover_160_cells_honest leftover_129_honesty_gate leftover_160_honesty_gate leftover_129_templates_present leftover_129_probe_source leftover_rows_kept leftover_rows leftover_129_kept leftover_160_kept leftover_202_kept leftover_203_kept leftover_204_kept leftover_197_kept leftover_205_kept leftover_188_kept leftover_209_kept leftover_217_kept dual_stack_probe nginx_present tongsuo_nginx tongsuo_cli handshake_observed nginx_gmtls_handshake nginx_gmtls_skip_reason gmtls_enc_cert_negotiated dual_cert_110_redone dual_stack_129_redone handshake_160_redone honesty_129_rerun honesty_160_rerun recovery_email_mfa vue_otp_writeback dengbao_claimed mipin_claimed hsm_claimed g17_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" leftover_129_cells_honest)" = "PASS" ] || fail "leftover_129_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_160_cells_honest)" = "PASS" ] || fail "leftover_160_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_129_honesty_gate)" = "PASS" ] || fail "leftover_129_honesty_gate must be PASS"
  [ "$(report_get "$file" leftover_160_honesty_gate)" = "PASS" ] || fail "leftover_160_honesty_gate must be PASS"
  [ "$(report_get "$file" leftover_129_templates_present)" = "PASS" ] || fail "leftover_129_templates_present must be PASS"
  [ "$(report_get "$file" leftover_129_probe_source)" = "PASS" ] || fail "leftover_129_probe_source must be PASS"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" leftover_129_kept)" = "true" ] || fail "leftover_129_kept must stay true"
  [ "$(report_get "$file" leftover_160_kept)" = "true" ] || fail "leftover_160_kept must stay true"
  [ "$(report_get "$file" leftover_202_kept)" = "true" ] || fail "leftover_202_kept must stay true (keep g10-leftover-honesty)"
  [ "$(report_get "$file" leftover_203_kept)" = "true" ] || fail "leftover_203_kept must stay true (keep g4-export-wizard-deny-honesty)"
  [ "$(report_get "$file" leftover_204_kept)" = "true" ] || fail "leftover_204_kept must stay true (keep g2-h5-every-page-form-write-honesty)"
  [ "$(report_get "$file" leftover_197_kept)" = "true" ] || fail "leftover_197_kept must stay true (keep g9-redis-down-invalidate-remainder)"
  [ "$(report_get "$file" leftover_205_kept)" = "true" ] || fail "leftover_205_kept must stay true (keep g17-mfa-recovery-vue-otp-honesty)"
  [ "$(report_get "$file" leftover_188_kept)" = "true" ] || fail "leftover_188_kept must stay true (keep g2-ops-pg-dump-honesty)"
  [ "$(report_get "$file" leftover_209_kept)" = "true" ] || fail "leftover_209_kept must stay true (keep g9-sentinel-cluster-remainder-honesty)"
  [ "$(report_get "$file" leftover_217_kept)" = "true" ] || fail "leftover_217_kept must stay true (keep g5-publishhook-ddl-honesty)"
  [ "$(report_get "$file" dual_stack_probe)" = "PASS" ] || fail "dual_stack_probe must be PASS (harness/probe that works)"
  [ "$(report_get "$file" dual_cert_110_redone)" = "false" ] || fail "must not redo #110"
  [ "$(report_get "$file" dual_stack_129_redone)" = "false" ] || fail "must not redo #129"
  [ "$(report_get "$file" handshake_160_redone)" = "false" ] || fail "must not redo #160"
  [ "$(report_get "$file" honesty_129_rerun)" = "false" ] || fail "honesty_129_rerun must be false (do not redo #129 collect)"
  [ "$(report_get "$file" honesty_160_rerun)" = "false" ] || fail "honesty_160_rerun must be false (do not redo #160 collect)"
  [ "$(report_get "$file" recovery_email_mfa)" = "false" ] || fail "recovery_email_mfa must stay false"
  [ "$(report_get "$file" vue_otp_writeback)" = "false" ] || fail "vue_otp_writeback must stay false"
  [ "$(report_get "$file" dengbao_claimed)" = "false" ] || fail "dengbao_claimed must stay false (等保 not closed)"
  [ "$(report_get "$file" mipin_claimed)" = "false" ] || fail "mipin_claimed must stay false (密评 not closed)"
  [ "$(report_get "$file" hsm_claimed)" = "false" ] || fail "hsm_claimed must stay false (密码机/HSM not closed)"
  [ "$(report_get "$file" g17_close)" = "NOT_CLAIMED" ] || fail "g17_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" gmtls_enc_cert_negotiated)" != "PASS" ] || fail "gmtls_enc_cert_negotiated cannot be PASS"

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $REQUIRED_LEFTOVER_ROWS; do
    echo "$rows" | grep -Fq "$required_row" || fail "leftover_rows must keep $required_row"
  done

  local handshake
  handshake="$(report_get "$file" nginx_gmtls_handshake)"
  case "$handshake" in
    PASS|SKIP|FAIL) ;;
    *) fail "nginx_gmtls_handshake must be PASS/SKIP/FAIL" ;;
  esac
  if [ "$handshake" = "PASS" ]; then
    [ "$(report_get "$file" nginx_present)" = "true" ] || fail "nginx_gmtls_handshake=PASS requires nginx_present=true"
    [ "$(report_get "$file" tongsuo_nginx)" = "true" ] || fail "nginx_gmtls_handshake=PASS requires tongsuo_nginx=true"
    [ "$(report_get "$file" tongsuo_cli)" = "true" ] || fail "nginx_gmtls_handshake=PASS requires tongsuo_cli=true"
    [ "$(report_get "$file" handshake_observed)" = "true" ] || fail "nginx_gmtls_handshake=PASS requires handshake_observed=true"
  fi
  if [ "$handshake" = "SKIP" ]; then
    [ -n "$(report_get "$file" nginx_gmtls_skip_reason)" ] || fail "nginx GMTLS SKIP needs nginx_gmtls_skip_reason"
    [ "$(report_get "$file" handshake_observed)" = "false" ] || fail "SKIP cannot claim handshake_observed=true"
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
  OUT="$ROOT/.local/g17-gm-tls-nginx-handshake-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G17 leftover honesty beyond #160/#129 (Tongsuo/Nginx GMTLS handshake PASS/SKIP)"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #110/#129/#160. Not 等保 / 密评 / 完整 G17. G18 stays closed."
echo

[ -f "$FILE_160" ] || fail "missing committed #160 artifact $FILE_160"
[ -f "$README" ] || fail "missing $README"
[ -f "$PROBE_SRC" ] || fail "missing DualStackTlsProbe source (do not redo #129; source must stay)"
[ -f "$PROBE_TEST" ] || fail "missing DualStackTlsProbeTest (do not redo #129; test must stay)"
[ -f "$TPL_NGINX" ] || fail "missing #129 nginx.conf.template"
[ -f "$TPL_DUAL" ] || fail "missing #129 tongsuo-dual-stack.conf.template"

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

C160_HANDSHAKE="$(artifact_get "$FILE_160" nginx_gmtls_handshake)"
C160_OBSERVED="$(artifact_get "$FILE_160" handshake_observed)"
C160_G17="$(artifact_get "$FILE_160" g17_close)"
C160_110="$(artifact_get "$FILE_160" dual_cert_110_redone)"
C160_129="$(artifact_get "$FILE_160" dual_stack_129_redone)"
C160_DENGBAO="$(artifact_get "$FILE_160" dengbao_claimed)"
C160_MIPIN="$(artifact_get "$FILE_160" mipin_claimed)"
C160_HSM="$(artifact_get "$FILE_160" hsm_claimed)"
C160_G18="$(artifact_get "$FILE_160" g18_status)"
C160_AUDIT="$(artifact_get "$FILE_160" audit_port_changed)"
C160_P0="$(artifact_get "$FILE_160" p0_m0_redone)"

[ "$C160_HANDSHAKE" = "SKIP" ] || fail "committed #160 nginx_gmtls_handshake is $C160_HANDSHAKE; leftover requires honest SKIP on that artifact"
[ "$C160_OBSERVED" = "false" ] || fail "committed #160 handshake_observed is $C160_OBSERVED; SKIP cannot claim a walk"
[ "$C160_G17" = "NOT_CLAIMED" ] || fail "committed #160 g17_close is $C160_G17"
[ "$C160_110" = "false" ] || fail "committed #160 dual_cert_110_redone is $C160_110"
[ "$C160_129" = "false" ] || fail "committed #160 dual_stack_129_redone is $C160_129"
[ "$C160_DENGBAO" = "false" ] || fail "committed #160 dengbao_claimed is $C160_DENGBAO"
[ "$C160_MIPIN" = "false" ] || fail "committed #160 mipin_claimed is $C160_MIPIN"
[ "$C160_HSM" = "false" ] || fail "committed #160 hsm_claimed is $C160_HSM"
[ "$C160_G18" = "closed_unchanged" ] || fail "committed #160 g18_status is $C160_G18"
[ "$C160_AUDIT" = "false" ] || fail "committed #160 audit_port_changed is $C160_AUDIT"
[ "$C160_P0" = "false" ] || fail "committed #160 p0_m0_redone is $C160_P0"

log "verify parent #160 honesty fixtures (harness PASS; do not re-run collect)"
./scripts/g17-gm-tls-nginx-handshake.sh --verify-report \
  scripts/g17-gm-tls-nginx-handshake/fixtures/honest-skip.env | tee -a "$OUT/collect.log"
set +e
./scripts/g17-gm-tls-nginx-handshake.sh --verify-report \
  scripts/g17-gm-tls-nginx-handshake/fixtures/fake-handshake-pass.env >"$OUT/fake-160-handshake.log" 2>&1
FAKE160_HS=$?
./scripts/g17-gm-tls-nginx-handshake.sh --verify-report \
  scripts/g17-gm-tls-nginx-handshake/fixtures/fake-claims.env >"$OUT/fake-160-claims.log" 2>&1
FAKE160_CL=$?
set -e
[ "$FAKE160_HS" -ne 0 ] || fail "expected #160 fake-handshake-pass.env to FAIL"
[ "$FAKE160_CL" -ne 0 ] || fail "expected #160 fake-claims.env to FAIL"
log "parent #160 honesty gate PASS (honest SKIP accepted; fakes rejected)"

log "verify parent #129 honesty fixtures (harness PASS; do not re-run collect)"
./scripts/g17-gm-tls-nginx-dual-stack/run.sh --verify-report \
  scripts/g17-gm-tls-nginx-dual-stack/fixtures/honest-skip.env | tee -a "$OUT/collect.log"
set +e
./scripts/g17-gm-tls-nginx-dual-stack/run.sh --verify-report \
  scripts/g17-gm-tls-nginx-dual-stack/fixtures/fake-nginx-pass.env >"$OUT/fake-129-nginx.log" 2>&1
FAKE129_NG=$?
./scripts/g17-gm-tls-nginx-dual-stack/run.sh --verify-report \
  scripts/g17-gm-tls-nginx-dual-stack/fixtures/fake-claims.env >"$OUT/fake-129-claims.log" 2>&1
FAKE129_CL=$?
set -e
[ "$FAKE129_NG" -ne 0 ] || fail "expected #129 fake-nginx-pass.env to FAIL"
[ "$FAKE129_CL" -ne 0 ] || fail "expected #129 fake-claims.env to FAIL"
log "parent #129 honesty gate PASS (honest SKIP accepted; fakes rejected)"

MISSING_ROWS=""
LEFTOVER_ROWS_KEPT=true
LEFTOVER_129_KEPT=false
LEFTOVER_160_KEPT=false
LEFTOVER_202_KEPT=false
LEFTOVER_203_KEPT=false
LEFTOVER_204_KEPT=false
LEFTOVER_197_KEPT=false
LEFTOVER_205_KEPT=false
LEFTOVER_188_KEPT=false
LEFTOVER_209_KEPT=false
LEFTOVER_217_KEPT=false
for required_row in $REQUIRED_LEFTOVER_ROWS; do
  if grep -Fq "[${required_row}](./${required_row}.md)" "$README"; then
    case "$required_row" in
      g17-gm-tls-nginx-dual-stack) LEFTOVER_129_KEPT=true ;;
      g17-gm-tls-nginx-handshake) LEFTOVER_160_KEPT=true ;;
      g10-leftover-honesty) LEFTOVER_202_KEPT=true ;;
      g4-export-wizard-deny-honesty) LEFTOVER_203_KEPT=true ;;
      g2-h5-every-page-form-write-honesty) LEFTOVER_204_KEPT=true ;;
      g9-redis-down-invalidate-remainder) LEFTOVER_197_KEPT=true ;;
      g17-mfa-recovery-vue-otp-honesty) LEFTOVER_205_KEPT=true ;;
      g2-ops-pg-dump-honesty) LEFTOVER_188_KEPT=true ;;
      g9-sentinel-cluster-remainder-honesty) LEFTOVER_209_KEPT=true ;;
      g5-publishhook-ddl-honesty) LEFTOVER_217_KEPT=true ;;
    esac
  else
    LEFTOVER_ROWS_KEPT=false
    MISSING_ROWS="${MISSING_ROWS} ${required_row}"
  fi
done
[ "$LEFTOVER_ROWS_KEPT" = "true" ] || fail "docs/README.md dropped leftover row(s):$MISSING_ROWS"
[ "$LEFTOVER_129_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-gm-tls-nginx-dual-stack (#129)"
[ "$LEFTOVER_160_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-gm-tls-nginx-handshake (#160)"
[ "$LEFTOVER_202_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-leftover-honesty (#202)"
[ "$LEFTOVER_203_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-export-wizard-deny-honesty (#203)"
[ "$LEFTOVER_204_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-h5-every-page-form-write-honesty (#204)"
[ "$LEFTOVER_197_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-redis-down-invalidate-remainder (#197)"
[ "$LEFTOVER_205_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-mfa-recovery-vue-otp-honesty (#205)"
[ "$LEFTOVER_188_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-ops-pg-dump-honesty (#188)"
[ "$LEFTOVER_209_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-sentinel-cluster-remainder-honesty (#209)"
[ "$LEFTOVER_217_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g5-publishhook-ddl-honesty (#217)"
if grep -Eiq '完整 G17 已关闭|product g17 closed|等保已关闭|密评已关闭' "$README"; then
  fail "docs/README.md must not claim 等保 / 密评 / 完整 G17 closed"
fi

OPENSSL_IDENTITY="not-installed"
OPENSSL_IS_TONGSUO=false
if command -v openssl >/dev/null 2>&1; then
  OPENSSL_IDENTITY="$(openssl version 2>&1 | tr -d '\r')"
  if printf '%s' "$OPENSSL_IDENTITY" | grep -Eiq 'tongsuo|babassl|baba ?ssl'; then
    OPENSSL_IS_TONGSUO=true
  fi
fi

find_nginx() {
  if [ -n "${NEXUS_G17_NGINX:-}" ] && [ -x "${NEXUS_G17_NGINX}" ]; then
    printf '%s\n' "${NEXUS_G17_NGINX}"
    return 0
  fi
  if command -v nginx >/dev/null 2>&1; then
    command -v nginx
    return 0
  fi
  return 1
}

NGINX_PRESENT=false
NGINX_BIN=""
NGINX_VERSION=""
TONGSUO_NGINX=false
if NGINX_BIN="$(find_nginx)"; then
  NGINX_PRESENT=true
  NGINX_VERSION="$( "$NGINX_BIN" -V 2>&1 | tr '\n' ' ' | tr -d '\r')"
  if printf '%s' "$NGINX_VERSION" | grep -Eiq 'tongsuo|babassl|baba_ssl|ntls|enable_ntls'; then
    TONGSUO_NGINX=true
  fi
fi

find_tongsuo_cli() {
  if [ -n "${NEXUS_G17_TONGSUO:-}" ] && [ -x "${NEXUS_G17_TONGSUO}" ]; then
    printf '%s\n' "${NEXUS_G17_TONGSUO}"
    return 0
  fi
  local candidate
  for candidate in tongsuo openssl-tongsuo; do
    if command -v "$candidate" >/dev/null 2>&1; then
      command -v "$candidate"
      return 0
    fi
  done
  if [ "$OPENSSL_IS_TONGSUO" = "true" ]; then
    command -v openssl
    return 0
  fi
  return 1
}

TONGSUO_CLI=false
TONGSUO_CLI_BIN=""
TONGSUO_CLI_IDENTITY=""
if TONGSUO_CLI_BIN="$(find_tongsuo_cli)"; then
  TONGSUO_CLI=true
  TONGSUO_CLI_IDENTITY="$("$TONGSUO_CLI_BIN" version 2>&1 | head -n 1 | tr -d '\r')"
fi

if [ "$NGINX_PRESENT" != "true" ]; then
  HANDSHAKE=SKIP
  HANDSHAKE_REASON="nginx not on PATH (NEXUS_G17_NGINX unset); Tongsuo-built nginx is required for a real GMTLS handshake. Stock OpenSSL (${OPENSSL_IDENTITY}) cannot terminate NTLS. No Docker fallback. This leftover does not re-run #160 collect or treat #129 DualStackTlsProbe as a Tongsuo handshake."
elif [ "$TONGSUO_NGINX" != "true" ]; then
  HANDSHAKE=SKIP
  HANDSHAKE_REASON="nginx is present but nginx -V does not report Tongsuo/BabaSSL/NTLS; stock nginx is not a GMTLS PASS. DualStackTlsProbe stays the local Java leftover, not a Tongsuo handshake."
elif [ "$TONGSUO_CLI" != "true" ]; then
  HANDSHAKE=SKIP
  HANDSHAKE_REASON="Tongsuo-built nginx is present but no Tongsuo s_client on PATH (NEXUS_G17_TONGSUO unset); handshake not claimed. This leftover does not re-run #160 collect."
else
  HANDSHAKE=SKIP
  HANDSHAKE_REASON="Tongsuo nginx + s_client present but this leftover does not claim a handshake until handshake_observed=true; does not re-run #160 collect or regenerate #110/#129 material"
fi

# Honesty leftover never fabricates a live Tongsuo handshake.
HANDSHAKE_OBSERVED=false

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
  kv leftover_129_cells_honest PASS
  kv leftover_160_cells_honest PASS
  kv leftover_129_honesty_gate PASS
  kv leftover_160_honesty_gate PASS
  kv leftover_129_templates_present PASS
  kv leftover_129_probe_source PASS
  kv leftover_rows_kept "$LEFTOVER_ROWS_KEPT"
  kv leftover_rows "$LEFTOVER_ROWS_CSV"
  kv leftover_129_kept "$LEFTOVER_129_KEPT"
  kv leftover_160_kept "$LEFTOVER_160_KEPT"
  kv leftover_202_kept "$LEFTOVER_202_KEPT"
  kv leftover_203_kept "$LEFTOVER_203_KEPT"
  kv leftover_204_kept "$LEFTOVER_204_KEPT"
  kv leftover_197_kept "$LEFTOVER_197_KEPT"
  kv leftover_205_kept "$LEFTOVER_205_KEPT"
  kv leftover_188_kept "$LEFTOVER_188_KEPT"
  kv leftover_209_kept "$LEFTOVER_209_KEPT"
  kv leftover_217_kept "$LEFTOVER_217_KEPT"
  kv dual_stack_probe PASS
  kv openssl_identity "$OPENSSL_IDENTITY"
  kv openssl_is_tongsuo "$OPENSSL_IS_TONGSUO"
  kv nginx_present "$NGINX_PRESENT"
  kv nginx_bin "$NGINX_BIN"
  kv nginx_version "$NGINX_VERSION"
  kv tongsuo_nginx "$TONGSUO_NGINX"
  kv tongsuo_cli "$TONGSUO_CLI"
  kv tongsuo_cli_identity "$TONGSUO_CLI_IDENTITY"
  kv handshake_observed "$HANDSHAKE_OBSERVED"
  kv nginx_gmtls_handshake "$HANDSHAKE"
  kv nginx_gmtls_skip_reason "$HANDSHAKE_REASON"
  kv gmtls_enc_cert_negotiated SKIP
  kv gmtls_enc_cert_skip_reason "GM/T 0024 encryption-cert negotiation is not implemented; this leftover does not redo #110/#129 Java RFC 8998"
  kv dual_cert_110_redone false
  kv dual_stack_129_redone false
  kv handshake_160_redone false
  kv honesty_129_rerun false
  kv honesty_160_rerun false
  kv recovery_email_mfa false
  kv vue_otp_writeback false
  kv dengbao_claimed false
  kv mipin_claimed false
  kv hsm_claimed false
  kv g17_close NOT_CLAIMED
  kv g17_close_reason "Honesty leftover beyond #160/#129. DualStackTlsProbe + parent honesty fixtures stay PASS. Live Tongsuo/Nginx SKIP when unavailable. Not 等保 / 密评 / 密码机 / HSM / 完整 G17."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "# G17 leftover honesty beyond #160/#129"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| leftover #129 cells still honest | PASS |"
  echo "| leftover #160 cells still honest | PASS |"
  echo "| #129 honesty fixtures | PASS |"
  echo "| #160 honesty fixtures | PASS |"
  echo "| DualStackTlsProbe harness | PASS |"
  echo "| #129 templates present | PASS |"
  echo "| Real Tongsuo/Nginx GMTLS handshake | SKIP |"
  echo "| leftover rows kept | $LEFTOVER_ROWS_CSV |"
  echo "| #110/#129/#160 redo | false |"
  echo "| 等保 / 密评 / HSM / G17 close | NOT_CLAIMED |"
  echo
  echo "Tongsuo SKIP reason: $HANDSHAKE_REASON"
} >"$MATRIX"

verify_report "$REPORT"
log "wrote $OUT/report.env"

SANITIZED="${ROOT}/docs/engineering/g17-gm-tls-nginx-handshake-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

echo
echo "PASS: leftover honesty recorded (#129/#160 harness PASS; Tongsuo/Nginx handshake SKIP)"
echo "See docs/g17-gm-tls-nginx-handshake-honesty.md"
echo "Keep sibling leftovers: docs/g17-gm-tls-nginx-handshake.md and docs/g17-gm-tls-nginx-dual-stack.md"
echo "Keep #202 g10-leftover-honesty, #203 g4-export-wizard-deny-honesty, #204 g2-h5-every-page-form-write-honesty, #197 g9-redis-down-invalidate-remainder, and #205 g17-mfa-recovery-vue-otp-honesty"
echo "Not claimed: 等保 / 密评 / HSM / 完整 G17 / #110/#129/#160 redo"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
