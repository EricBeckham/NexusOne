#!/usr/bin/env bash
# G17 leftover: real Tongsuo/Nginx GMTLS handshake honesty.
#
# Extends #129 dual-stack leftover. Does NOT redo DualStackTlsProbe,
# GmTlsDualCert, or documented nginx templates (#110 / #129). Does NOT
# start Docker. Does NOT claim 等保 / 密评 / 密码机 / HSM / G17 close.
# Orthogonal to recovery-code / email MFA and Vue otp write-back.
#
# PASS only when Tongsuo-built nginx AND Tongsuo s_client exist AND a
# GMTLS/NTLS handshake was observed. Missing binaries is honest SKIP.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HERE="$(cd "$(dirname "$0")" && pwd)"
TEMPLATES="${ROOT}/scripts/g17-gm-tls-nginx-dual-stack/nginx"
export PATH="${HOME}/tools/apache-maven-3.8.7/bin:${PATH}"
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""
REQUIRE_HANDSHAKE=0

usage() {
  cat <<'EOF'
Usage:
  scripts/g17-gm-tls-nginx-handshake.sh [--out DIR] [--require-handshake]
  scripts/g17-gm-tls-nginx-handshake.sh --verify-report FILE

Inventory Tongsuo-built nginx + Tongsuo s_client and attempt a real
GMTLS/NTLS handshake only when both exist and #129 material is already
present. Missing Tongsuo/Nginx is honest SKIP. Never claims 等保 / 密评 / HSM.

  --out DIR               Output directory (default: .local/g17-gm-tls-nginx-handshake/<utc>)
  --require-handshake     Missing Tongsuo handshake is FAIL instead of SKIP
  --verify-report F       Honesty gate on an existing report.env

Environment (optional, never printed):
  NEXUS_G17_NGINX          path to Tongsuo-built nginx
  NEXUS_G17_TONGSUO        path to tongsuo / openssl-tongsuo client
  NEXUS_G17_MATERIAL       existing #129 material dir (not generated here)
  NEXUS_G17_INTL_PORT      rendered intl listen port (default 18453)
  NEXUS_G17_GM_PORT        rendered gm listen port (default 18454)
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --require-handshake) REQUIRE_HANDSHAKE=1; shift ;;
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used nginx_present tongsuo_nginx tongsuo_cli handshake_observed nginx_gmtls_handshake gmtls_enc_cert_negotiated dual_cert_110_redone dual_stack_129_redone recovery_email_mfa vue_otp_writeback dengbao_claimed mipin_claimed hsm_claimed g17_close g18_status audit_port_changed p0_m0_redone"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || fail "missing key $key in $file"
  done

  local java boot match docker nginx_present tongsuo_nginx tongsuo_cli observed handshake enc redo110 redo129 recovery vue dengbao mipin hsm g17 g18 audit p0
  java="$(report_get "$file" java_feature)"
  boot="$(report_get "$file" boot_parent_version)"
  match="$(report_get "$file" boot_parent_match)"
  docker="$(report_get "$file" docker_used)"
  nginx_present="$(report_get "$file" nginx_present)"
  tongsuo_nginx="$(report_get "$file" tongsuo_nginx)"
  tongsuo_cli="$(report_get "$file" tongsuo_cli)"
  observed="$(report_get "$file" handshake_observed)"
  handshake="$(report_get "$file" nginx_gmtls_handshake)"
  enc="$(report_get "$file" gmtls_enc_cert_negotiated)"
  redo110="$(report_get "$file" dual_cert_110_redone)"
  redo129="$(report_get "$file" dual_stack_129_redone)"
  recovery="$(report_get "$file" recovery_email_mfa)"
  vue="$(report_get "$file" vue_otp_writeback)"
  dengbao="$(report_get "$file" dengbao_claimed)"
  mipin="$(report_get "$file" mipin_claimed)"
  hsm="$(report_get "$file" hsm_claimed)"
  g17="$(report_get "$file" g17_close)"
  g18="$(report_get "$file" g18_status)"
  audit="$(report_get "$file" audit_port_changed)"
  p0="$(report_get "$file" p0_m0_redone)"

  [ "$java" = "21" ] || fail "java_feature must be 21, got $java"
  [ "$boot" = "4.1.1" ] || fail "boot_parent_version must be 4.1.1, got $boot"
  [ "$match" = "PASS" ] || fail "boot_parent_match must be PASS"
  [ "$docker" = "false" ] || fail "docker_used must be false"
  [ "$g18" = "closed_unchanged" ] || fail "g18_status must stay closed_unchanged"
  [ "$audit" = "false" ] || fail "audit_port_changed must be false"
  [ "$p0" = "false" ] || fail "p0_m0_redone must stay false"
  [ "$redo110" = "false" ] || fail "dual_cert_110_redone must stay false (do not redo #110)"
  [ "$redo129" = "false" ] || fail "dual_stack_129_redone must stay false (do not redo #129)"
  [ "$recovery" = "false" ] || fail "recovery_email_mfa must stay false (orthogonal leftover)"
  [ "$vue" = "false" ] || fail "vue_otp_writeback must stay false (orthogonal leftover)"
  [ "$dengbao" = "false" ] || fail "dengbao_claimed must stay false (等保 not closed)"
  [ "$mipin" = "false" ] || fail "mipin_claimed must stay false (密评 not closed)"
  [ "$hsm" = "false" ] || fail "hsm_claimed must stay false (密码机/HSM not closed)"
  [ "$g17" = "NOT_CLAIMED" ] || fail "g17_close must stay NOT_CLAIMED (got $g17)"
  [ "$enc" != "PASS" ] || fail "gmtls_enc_cert_negotiated cannot be PASS (GM/T 0024 enc-cert leftover)"

  case "$handshake" in
    PASS|SKIP|FAIL) ;;
    *) fail "nginx_gmtls_handshake must be PASS/SKIP/FAIL" ;;
  esac

  if [ "$handshake" = "PASS" ]; then
    [ "$nginx_present" = "true" ] || fail "nginx_gmtls_handshake=PASS requires nginx_present=true"
    [ "$tongsuo_nginx" = "true" ] || fail "nginx_gmtls_handshake=PASS requires tongsuo_nginx=true (stock nginx is not GMTLS)"
    [ "$tongsuo_cli" = "true" ] || fail "nginx_gmtls_handshake=PASS requires tongsuo_cli=true"
    [ "$observed" = "true" ] || fail "nginx_gmtls_handshake=PASS requires handshake_observed=true"
    [ -n "$(report_get "$file" gmtls_cipher)" ] || fail "nginx_gmtls_handshake=PASS needs gmtls_cipher"
    [ -n "$(report_get "$file" nginx_gmtls_client)" ] || fail "nginx_gmtls_handshake=PASS needs nginx_gmtls_client"
  fi
  if [ "$handshake" = "SKIP" ]; then
    [ -n "$(report_get "$file" nginx_gmtls_skip_reason)" ] || fail "nginx GMTLS SKIP needs a reason"
    [ "$observed" = "false" ] || fail "SKIP cannot claim handshake_observed=true"
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
  OUT="$ROOT/.local/g17-gm-tls-nginx-handshake/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G17 leftover: real Tongsuo/Nginx GMTLS handshake honesty"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #110 dual-cert or #129 dual-stack probe. Not 等保 / 密评 / HSM."
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

find_existing_material() {
  if [ -n "${NEXUS_G17_MATERIAL:-}" ]; then
    if [ -f "${NEXUS_G17_MATERIAL}/public/server-sign.pem" ] \
      && [ -f "${NEXUS_G17_MATERIAL}/public/server-encrypt.pem" ] \
      && [ -f "${NEXUS_G17_MATERIAL}/keys/gm-server-sign.key" ] \
      && [ -f "${NEXUS_G17_MATERIAL}/keys/gm-server-encrypt.key" ]; then
      printf '%s\n' "${NEXUS_G17_MATERIAL}"
      return 0
    fi
    return 1
  fi
  local candidate
  shopt -s nullglob
  for candidate in "$ROOT"/.local/g17-gm-tls-nginx/*/material; do
    if [ -f "$candidate/public/server-sign.pem" ] \
      && [ -f "$candidate/public/server-encrypt.pem" ] \
      && [ -f "$candidate/keys/gm-server-sign.key" ] \
      && [ -f "$candidate/keys/gm-server-encrypt.key" ]; then
      printf '%s\n' "$candidate"
      shopt -u nullglob
      return 0
    fi
  done
  shopt -u nullglob
  return 1
}

INTL_PORT="${NEXUS_G17_INTL_PORT:-18453}"
GM_PORT="${NEXUS_G17_GM_PORT:-18454}"
MATERIAL_DIR=""
MATERIAL_PRESENT=false
if MATERIAL_DIR="$(find_existing_material)"; then
  MATERIAL_PRESENT=true
fi

HANDSHAKE=SKIP
HANDSHAKE_REASON=""
HANDSHAKE_OBSERVED=false
GMTLS_CIPHER=""
NGINX_CLIENT=""
NGINX_LINT=SKIP
NGINX_LINT_REASON=""

if [ "$NGINX_PRESENT" != "true" ]; then
  HANDSHAKE=SKIP
  HANDSHAKE_REASON="nginx not on PATH (NEXUS_G17_NGINX unset); Tongsuo-built nginx is required for a real GMTLS handshake. Stock OpenSSL (${OPENSSL_IDENTITY}) cannot terminate NTLS. No Docker fallback. #129 dual-stack leftover stays documented only."
elif [ "$TONGSUO_NGINX" != "true" ]; then
  HANDSHAKE=SKIP
  HANDSHAKE_REASON="nginx is present but nginx -V does not report Tongsuo/BabaSSL/NTLS; stock nginx is not a GMTLS PASS. This leftover does not treat #129 Java DualStackTlsProbe as a Tongsuo handshake."
elif [ "$TONGSUO_CLI" != "true" ]; then
  HANDSHAKE=SKIP
  HANDSHAKE_REASON="Tongsuo-built nginx is present but no Tongsuo s_client on PATH (NEXUS_G17_TONGSUO unset); handshake not claimed"
elif [ "$MATERIAL_PRESENT" != "true" ]; then
  HANDSHAKE=SKIP
  HANDSHAKE_REASON="Tongsuo nginx + s_client present but #129 dual-cert material is absent; this leftover does not regenerate #110/#129 PKCS12/PEM"
else
  log "Tongsuo nginx + s_client + existing material; render #129 templates without rewriting them"
  NGINX_PREFIX="$OUT/nginx"
  mkdir -p "$NGINX_PREFIX"
  sed -e "s|__PREFIX__|${NGINX_PREFIX}|g" \
    "$TEMPLATES/nginx.conf.template" >"$NGINX_PREFIX/nginx.conf"
  sed \
    -e "s|__INTL_PORT__|${INTL_PORT}|g" \
    -e "s|__GM_PORT__|${GM_PORT}|g" \
    -e "s|__INTL_CERT__|${MATERIAL_DIR}/public/intl-server.pem|g" \
    -e "s|__INTL_KEY__|${MATERIAL_DIR}/keys/intl-server.key|g" \
    -e "s|__GM_SIGN_CERT__|${MATERIAL_DIR}/public/server-sign.pem|g" \
    -e "s|__GM_SIGN_KEY__|${MATERIAL_DIR}/keys/gm-server-sign.key|g" \
    -e "s|__GM_ENC_CERT__|${MATERIAL_DIR}/public/server-encrypt.pem|g" \
    -e "s|__GM_ENC_KEY__|${MATERIAL_DIR}/keys/gm-server-encrypt.key|g" \
    "$TEMPLATES/tongsuo-dual-stack.conf.template" >"$NGINX_PREFIX/dual-stack.conf"
  if "$NGINX_BIN" -p "$NGINX_PREFIX" -c "$NGINX_PREFIX/nginx.conf" -t >"$OUT/nginx-t.log" 2>&1; then
    NGINX_LINT=PASS
    NGINX_LINT_REASON=""
  else
    NGINX_LINT=FAIL
    NGINX_LINT_REASON="nginx -t failed (see nginx-t.log)"
    HANDSHAKE=FAIL
    HANDSHAKE_REASON="$NGINX_LINT_REASON"
  fi
  if [ "$NGINX_LINT" = "PASS" ]; then
    log "start Tongsuo nginx and probe GMTLS /g17"
    "$NGINX_BIN" -p "$NGINX_PREFIX" -c "$NGINX_PREFIX/nginx.conf" >"$OUT/nginx-run.log" 2>&1 &
    NGINX_PID=$!
    sleep 0.4
    GM_OK=0
    if "$TONGSUO_CLI_BIN" s_client -connect "127.0.0.1:${GM_PORT}" -enable_ntls \
          -cipher ECC-SM2-SM4-GCM-SM3 -quiet </dev/null >"$OUT/tongsuo-s_client.log" 2>&1; then
      if grep -Eq 'G17-GM-OK|Cipher is|Protocol' "$OUT/tongsuo-s_client.log"; then
        GM_OK=1
        GMTLS_CIPHER="$(awk -F': ' '/Cipher[[:space:]]*(is|:)/{print $2; exit}' "$OUT/tongsuo-s_client.log" | tr -d '\r')"
        GMTLS_CIPHER="${GMTLS_CIPHER:-ECC-SM2-SM4-GCM-SM3}"
      fi
    fi
    kill "$NGINX_PID" 2>/dev/null || true
    wait "$NGINX_PID" 2>/dev/null || true
    if [ "$GM_OK" = "1" ]; then
      HANDSHAKE=PASS
      HANDSHAKE_OBSERVED=true
      NGINX_CLIENT="$TONGSUO_CLI_BIN"
      HANDSHAKE_REASON=""
    else
      HANDSHAKE=FAIL
      HANDSHAKE_REASON="Tongsuo nginx started but GMTLS s_client handshake did not succeed"
    fi
  fi
fi

if [ "$REQUIRE_HANDSHAKE" = "1" ] && [ "$HANDSHAKE" != "PASS" ]; then
  HANDSHAKE=FAIL
  HANDSHAKE_REASON="${HANDSHAKE_REASON:---require-handshake set and handshake is not PASS}"
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
  kv openssl_identity "$OPENSSL_IDENTITY"
  kv openssl_is_tongsuo "$OPENSSL_IS_TONGSUO"
  kv nginx_present "$NGINX_PRESENT"
  kv nginx_bin "$NGINX_BIN"
  kv nginx_version "$NGINX_VERSION"
  kv tongsuo_nginx "$TONGSUO_NGINX"
  kv tongsuo_cli "$TONGSUO_CLI"
  kv tongsuo_cli_identity "$TONGSUO_CLI_IDENTITY"
  kv material_present "$MATERIAL_PRESENT"
  kv nginx_config_lint "$NGINX_LINT"
  kv nginx_config_lint_reason "$NGINX_LINT_REASON"
  kv handshake_observed "$HANDSHAKE_OBSERVED"
  kv nginx_gmtls_handshake "$HANDSHAKE"
  kv nginx_gmtls_skip_reason "$HANDSHAKE_REASON"
  kv nginx_gmtls_client "$NGINX_CLIENT"
  kv gmtls_cipher "$GMTLS_CIPHER"
  kv gmtls_enc_cert_negotiated SKIP
  kv gmtls_enc_cert_skip_reason "GM/T 0024 encryption-cert negotiation is not implemented; this leftover only observes a Tongsuo NTLS handshake when binaries exist"
  kv dual_cert_110_redone false
  kv dual_stack_129_redone false
  kv recovery_email_mfa false
  kv vue_otp_writeback false
  kv dengbao_claimed false
  kv mipin_claimed false
  kv hsm_claimed false
  kv g17_close NOT_CLAIMED
  kv g17_close_reason "Honesty leftover for a real Tongsuo/Nginx GMTLS handshake. Missing binaries SKIP. Not 等保 / 密评 / 密码机 / HSM / qualified GM/T Provider."
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
  echo "| OpenSSL identity | recorded | $OPENSSL_IDENTITY (tongsuo=$OPENSSL_IS_TONGSUO) |"
  echo "| nginx present | $NGINX_PRESENT | $NGINX_BIN |"
  echo "| Tongsuo-built nginx | $TONGSUO_NGINX | stock nginx is not GMTLS |"
  echo "| Tongsuo s_client | $TONGSUO_CLI | $TONGSUO_CLI_IDENTITY |"
  echo "| #129 material reused | $MATERIAL_PRESENT | not regenerated |"
  echo "| nginx -t (Tongsuo config) | $NGINX_LINT | ${NGINX_LINT_REASON} |"
  echo "| Real Tongsuo/Nginx GMTLS handshake | $HANDSHAKE | ${HANDSHAKE_REASON} |"
  echo "| handshake_observed | $HANDSHAKE_OBSERVED | cipher=${GMTLS_CIPHER:-n/a} |"
  echo "| GM/T 0024 enc-cert negotiated | SKIP | leftover |"
  echo "| #110 / #129 redo | false | dual-cert and dual-stack probe untouched |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| 等保 / 密评 / 密码机 / HSM | NOT_CLAIMED | dengbao=false mipin=false hsm=false |"
  echo "| G17 close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g17-gm-tls-nginx-handshake collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT"

if [ "$HANDSHAKE" = "FAIL" ]; then
  fail "${HANDSHAKE_REASON:-nginx GMTLS handshake failed}"
fi

SANITIZED="${ROOT}/docs/engineering/g17-gm-tls-nginx-handshake/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT nginx_gmtls=$HANDSHAKE g17_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (real Tongsuo/Nginx GMTLS handshake $HANDSHAKE)"
echo "See docs/g17-gm-tls-nginx-handshake.md"
echo "Not claimed: 等保 / 密评 / HSM / G17 close / #110 dual-cert redo / #129 dual-stack redo"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
