#!/usr/bin/env bash
# G17 leftover: Nginx/Tongsuo (or local Java dual-stack) 国密 TLS evidence.
#
# Local two-port probe (international TLS 1.3 + RFC 8998 TLS_SM4_GCM_SM3) can
# run without Docker and without Tongsuo. Nginx/Tongsuo GMTLS is PASS only when
# a Tongsuo-built nginx is on PATH and a handshake actually succeeds.
# Honest SKIP when those binaries are missing. Never claims 等保 / 密评 /
# 密码机 / HSM / G17 close. Does not redo MFA / 三权 / TOTP. AuditPort untouched.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
HERE="$(cd "$(dirname "$0")" && pwd)"
export PATH="${HOME}/tools/apache-maven-3.8.7/bin:${PATH}"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""
SKIP_LOCAL=0
REQUIRE_NGINX=0
WRITE_MATERIAL=0

usage() {
  cat <<'EOF'
Usage:
  scripts/g17-gm-tls-nginx-dual-stack/run.sh [--out DIR] [--skip-local] [--require-nginx] [--write-material]
  scripts/g17-gm-tls-nginx-dual-stack/run.sh --verify-report FILE

Collect local dual-stack evidence + Nginx/Tongsuo inventory. Writes report.env
and matrix.md. Tongsuo/Nginx GMTLS is SKIP unless a Tongsuo-built nginx exists
and a handshake succeeds. Stock OpenSSL nginx is never a GMTLS PASS.

  --out DIR           Output directory (default: .local/g17-gm-tls-nginx/<utc>)
  --skip-local        Inventory + honesty only (do not run mvn / Java probe)
  --require-nginx     Missing Tongsuo nginx handshake is FAIL instead of SKIP
  --write-material    Also emit PKCS12/PEM under <out>/material (gitignored)
  --verify-report F   Honesty gate on an existing report.env

Environment (optional, never printed):
  NEXUS_GM_TLS_STORE_PASSWORD
  NEXUS_G17_NGINX          path to nginx binary
  NEXUS_G17_TONGSUO        path to tongsuo / openssl-tongsuo client
  NEXUS_G17_INTL_PORT      rendered intl listen port (default 18443)
  NEXUS_G17_GM_PORT        rendered gm listen port (default 18444)
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --skip-local) SKIP_LOCAL=1; shift ;;
    --require-nginx) REQUIRE_NGINX=1; shift ;;
    --write-material) WRITE_MATERIAL=1; shift ;;
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used local_dual_stack nginx_present tongsuo_nginx tongsuo_cli nginx_gmtls_handshake gmtls_enc_cert_negotiated dengbao_claimed mipin_claimed hsm_claimed g17_close g18_status audit_port_changed"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || fail "missing key $key in $file"
  done

  local java boot match docker local_ds nginx_present tongsuo_nginx handshake enc dengbao mipin hsm g17 g18 audit
  java="$(report_get "$file" java_feature)"
  boot="$(report_get "$file" boot_parent_version)"
  match="$(report_get "$file" boot_parent_match)"
  docker="$(report_get "$file" docker_used)"
  local_ds="$(report_get "$file" local_dual_stack)"
  nginx_present="$(report_get "$file" nginx_present)"
  tongsuo_nginx="$(report_get "$file" tongsuo_nginx)"
  handshake="$(report_get "$file" nginx_gmtls_handshake)"
  enc="$(report_get "$file" gmtls_enc_cert_negotiated)"
  dengbao="$(report_get "$file" dengbao_claimed)"
  mipin="$(report_get "$file" mipin_claimed)"
  hsm="$(report_get "$file" hsm_claimed)"
  g17="$(report_get "$file" g17_close)"
  g18="$(report_get "$file" g18_status)"
  audit="$(report_get "$file" audit_port_changed)"

  [ "$java" = "21" ] || fail "java_feature must be 21, got $java"
  [ "$boot" = "4.1.1" ] || fail "boot_parent_version must be 4.1.1, got $boot"
  [ "$match" = "PASS" ] || fail "boot_parent_match must be PASS"
  [ "$docker" = "false" ] || fail "docker_used must be false"
  [ "$g18" = "closed_unchanged" ] || fail "g18_status must stay closed_unchanged"
  [ "$audit" = "false" ] || fail "audit_port_changed must be false"
  [ "$dengbao" = "false" ] || fail "dengbao_claimed must stay false (等保 not closed)"
  [ "$mipin" = "false" ] || fail "mipin_claimed must stay false (密评 not closed)"
  [ "$hsm" = "false" ] || fail "hsm_claimed must stay false (密码机/HSM not closed)"
  [ "$g17" = "NOT_CLAIMED" ] || fail "g17_close must stay NOT_CLAIMED (got $g17)"
  [ "$enc" != "PASS" ] || fail "gmtls_enc_cert_negotiated cannot be PASS (GM/T 0024 enc-cert leftover)"

  if [ "$handshake" = "PASS" ]; then
    [ "$nginx_present" = "true" ] || fail "nginx_gmtls_handshake=PASS requires nginx_present=true"
    [ "$tongsuo_nginx" = "true" ] || fail "nginx_gmtls_handshake=PASS requires tongsuo_nginx=true (stock nginx is not GMTLS)"
    [ -n "$(report_get "$file" nginx_gmtls_client)" ] || fail "nginx_gmtls_handshake=PASS needs nginx_gmtls_client"
  fi
  if [ "$handshake" = "SKIP" ]; then
    [ -n "$(report_get "$file" nginx_gmtls_skip_reason)" ] || fail "nginx GMTLS SKIP needs a reason"
  fi
  case "$local_ds" in
    PASS|SKIP|FAIL) ;;
    *) fail "local_dual_stack must be PASS/SKIP/FAIL" ;;
  esac
  if [ "$local_ds" = "SKIP" ]; then
    [ -n "$(report_get "$file" local_dual_stack_skip_reason)" ] || fail "local dual-stack SKIP needs a reason"
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
  OUT="$ROOT/.local/g17-gm-tls-nginx/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

JAVA_FEATURE=missing
JAVA_VERSION_LINE=not-installed
if command -v java >/dev/null 2>&1; then
  JAVA_VERSION_LINE="$(java -version 2>&1 | head -n 1 | tr -d '\r')"
  JAVA_FEATURE="$(java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*java\.specification\.version[[:space:]]*=/{print $3; exit}')"
fi
JAVA_STATUS=FAIL
if [ "$JAVA_FEATURE" = "21" ]; then
  JAVA_STATUS=PASS
fi

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

DOCKER_PRESENT=false
DOCKER_USED=false
if command -v docker >/dev/null 2>&1; then
  DOCKER_PRESENT=true
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

INTL_PORT="${NEXUS_G17_INTL_PORT:-18443}"
GM_PORT="${NEXUS_G17_GM_PORT:-18444}"
MATERIAL_DIR="$OUT/material"
NGINX_PREFIX="$OUT/nginx"
mkdir -p "$NGINX_PREFIX"

render_nginx() {
  local sign_pem enc_pem intl_pem
  sign_pem="$1/public/server-sign.pem"
  enc_pem="$1/public/server-encrypt.pem"
  intl_pem="$1/public/intl-server.pem"
  local sign_key enc_key intl_key
  sign_key="$1/keys/gm-server-sign.key"
  enc_key="$1/keys/gm-server-encrypt.key"
  intl_key="$1/keys/intl-server.key"
  sed \
    -e "s|__PREFIX__|${NGINX_PREFIX}|g" \
    "$HERE/nginx/nginx.conf.template" >"$NGINX_PREFIX/nginx.conf"
  sed \
    -e "s|__INTL_PORT__|${INTL_PORT}|g" \
    -e "s|__GM_PORT__|${GM_PORT}|g" \
    -e "s|__INTL_CERT__|${intl_pem}|g" \
    -e "s|__INTL_KEY__|${intl_key}|g" \
    -e "s|__GM_SIGN_CERT__|${sign_pem}|g" \
    -e "s|__GM_SIGN_KEY__|${sign_key}|g" \
    -e "s|__GM_ENC_CERT__|${enc_pem}|g" \
    -e "s|__GM_ENC_KEY__|${enc_key}|g" \
    "$HERE/nginx/tongsuo-dual-stack.conf.template" >"$NGINX_PREFIX/dual-stack.conf"
}

# Always render the documented config with placeholder-safe missing PEMs so the
# artifact shows what would be loaded. Real keys are written only with --write-material.
PLACEHOLDER_MATERIAL="$OUT/template-paths"
mkdir -p "$PLACEHOLDER_MATERIAL/public" "$PLACEHOLDER_MATERIAL/keys"
: >"$PLACEHOLDER_MATERIAL/public/server-sign.pem"
: >"$PLACEHOLDER_MATERIAL/public/server-encrypt.pem"
: >"$PLACEHOLDER_MATERIAL/public/intl-server.pem"
: >"$PLACEHOLDER_MATERIAL/keys/gm-server-sign.key"
: >"$PLACEHOLDER_MATERIAL/keys/gm-server-encrypt.key"
: >"$PLACEHOLDER_MATERIAL/keys/intl-server.key"
render_nginx "$PLACEHOLDER_MATERIAL"
cp "$HERE/nginx/tongsuo-dual-stack.conf.template" "$OUT/tongsuo-dual-stack.conf.template"

LOCAL_DUAL_STACK=SKIP
LOCAL_SKIP_REASON="--skip-local requested"
LOCAL_LINE=""
if [ "$SKIP_LOCAL" != "1" ]; then
  if [ "$JAVA_STATUS" != "PASS" ]; then
    LOCAL_DUAL_STACK=FAIL
    LOCAL_SKIP_REASON="JDK feature version is ${JAVA_FEATURE}, product baseline is 21"
  elif ! command -v mvn >/dev/null 2>&1; then
    LOCAL_DUAL_STACK=FAIL
    LOCAL_SKIP_REASON="mvn not installed on PATH"
  else
    log "local dual-stack mvn test"
    if (cd "$ROOT" && mvn -B -pl nexus-crypto-core -am test \
          -Dtest=DualStackTlsProbeTest,GmTlsHandshakeTest,GmTlsDualCertTest \
          -Dsurefire.failIfNoSpecifiedTests=false) >"$OUT/local-dual-stack.log" 2>&1; then
      if grep -Eq 'BUILD SUCCESS' "$OUT/local-dual-stack.log" \
        && ! grep -Eq 'Failures: [1-9]|Errors: [1-9]' "$OUT/local-dual-stack.log"; then
        LOCAL_DUAL_STACK=PASS
        LOCAL_SKIP_REASON=""
        LOCAL_LINE="$(grep -E 'G17 dual-stack |G17 GM TLS handshake ' "$OUT/local-dual-stack.log" | tail -n 2 | tr '\n' ' ')"
      else
        LOCAL_DUAL_STACK=FAIL
        LOCAL_SKIP_REASON="Surefire did not report a clean BUILD SUCCESS (see local-dual-stack.log)"
      fi
    else
      LOCAL_DUAL_STACK=FAIL
      LOCAL_SKIP_REASON="mvn test failed for DualStackTlsProbeTest (see local-dual-stack.log)"
    fi
  fi
fi

if [ "$WRITE_MATERIAL" = "1" ] && [ "$LOCAL_DUAL_STACK" = "PASS" ]; then
  log "write dual-stack material under $MATERIAL_DIR"
  mkdir -p "$MATERIAL_DIR"
  (cd "$ROOT" && mvn -q -pl nexus-crypto-core -am -DskipTests package \
      dependency:build-classpath -DincludeScope=runtime \
      -Dmdep.outputFile=nexus-crypto-core/target/runtime.cp) >"$OUT/write-material.log" 2>&1 || true
  if [ -f "$ROOT/nexus-crypto-core/target/runtime.cp" ]; then
    java -cp "$ROOT/nexus-crypto-core/target/classes:$ROOT/nexus-common/target/classes:$(cat "$ROOT/nexus-crypto-core/target/runtime.cp")" \
      one.nexus.crypto.tls.DualStackMaterialWriter "$MATERIAL_DIR" >>"$OUT/write-material.log" 2>&1 || true
    if [ -f "$MATERIAL_DIR/public/intl-server.pem" ]; then
      render_nginx "$MATERIAL_DIR"
    fi
  fi
fi

NGINX_LINT=SKIP
NGINX_LINT_REASON=""
NGINX_HANDSHAKE=SKIP
NGINX_HANDSHAKE_REASON=""
NGINX_CLIENT=""

if [ "$NGINX_PRESENT" != "true" ]; then
  NGINX_LINT=SKIP
  NGINX_LINT_REASON="nginx not on PATH (NEXUS_G17_NGINX unset)"
  NGINX_HANDSHAKE=SKIP
  NGINX_HANDSHAKE_REASON="nginx not on PATH; Tongsuo-built nginx is required for GM/T 0024 dual-stack. Stock OpenSSL (${OPENSSL_IDENTITY}) cannot terminate NTLS. No Docker fallback."
elif [ "$TONGSUO_NGINX" != "true" ]; then
  NGINX_LINT=SKIP
  NGINX_LINT_REASON="nginx is present but nginx -V does not report Tongsuo/BabaSSL/NTLS; stock nginx cannot load enable_ntls / ssl_ntls_*"
  NGINX_HANDSHAKE=SKIP
  NGINX_HANDSHAKE_REASON="stock nginx without Tongsuo is not a GMTLS PASS (nginx -V has no Tongsuo/NTLS). Documented config is rendered only."
else
  log "Tongsuo nginx detected; lint rendered config"
  if "$NGINX_BIN" -p "$NGINX_PREFIX" -c "$NGINX_PREFIX/nginx.conf" -t >"$OUT/nginx-t.log" 2>&1; then
    NGINX_LINT=PASS
    NGINX_LINT_REASON=""
  else
    NGINX_LINT=FAIL
    NGINX_LINT_REASON="nginx -t failed (see nginx-t.log)"
  fi
  if [ "$NGINX_LINT" = "PASS" ] && [ "$TONGSUO_CLI" = "true" ]; then
    log "start Tongsuo nginx and probe /g17"
    "$NGINX_BIN" -p "$NGINX_PREFIX" -c "$NGINX_PREFIX/nginx.conf" >"$OUT/nginx-run.log" 2>&1 &
    NGINX_PID=$!
    sleep 0.4
    INTL_OK=0
    GM_OK=0
    if command -v curl >/dev/null 2>&1; then
      if curl -fsS --max-time 3 "https://127.0.0.1:${INTL_PORT}/g17" -k | grep -q 'G17-INTL-OK'; then
        INTL_OK=1
      fi
    fi
    if "$TONGSUO_CLI_BIN" s_client -connect "127.0.0.1:${GM_PORT}" -enable_ntls \
          -cipher ECC-SM2-SM4-GCM-SM3 -quiet </dev/null >"$OUT/tongsuo-s_client.log" 2>&1; then
      if grep -Eq 'G17-GM-OK|Cipher is|Protocol' "$OUT/tongsuo-s_client.log"; then
        GM_OK=1
      fi
    fi
    kill "$NGINX_PID" 2>/dev/null || true
    wait "$NGINX_PID" 2>/dev/null || true
    if [ "$INTL_OK" = "1" ] && [ "$GM_OK" = "1" ]; then
      NGINX_HANDSHAKE=PASS
      NGINX_CLIENT="$TONGSUO_CLI_BIN"
      NGINX_HANDSHAKE_REASON=""
    else
      NGINX_HANDSHAKE=FAIL
      NGINX_HANDSHAKE_REASON="Tongsuo nginx started but intl/GM /g17 handshake did not both succeed (intl=$INTL_OK gm=$GM_OK)"
    fi
  else
    NGINX_HANDSHAKE=SKIP
    if [ "$NGINX_LINT" != "PASS" ]; then
      NGINX_HANDSHAKE_REASON="$NGINX_LINT_REASON"
    else
      NGINX_HANDSHAKE_REASON="Tongsuo nginx lint PASS but no Tongsuo s_client on PATH; handshake not claimed"
    fi
  fi
fi

if [ "$REQUIRE_NGINX" = "1" ] && [ "$NGINX_HANDSHAKE" != "PASS" ]; then
  NGINX_HANDSHAKE=FAIL
  NGINX_HANDSHAKE_REASON="${NGINX_HANDSHAKE_REASON:---require-nginx set and handshake is not PASS}"
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
  kv java_status "$JAVA_STATUS"
  kv boot_parent_ga "$BOOT_PARENT_GA"
  kv boot_parent_version "$BOOT_VERSION"
  kv boot_parent_match "$BOOT_PARENT_MATCH"
  kv docker_present "$DOCKER_PRESENT"
  kv docker_used "$DOCKER_USED"
  kv openssl_identity "$OPENSSL_IDENTITY"
  kv openssl_is_tongsuo "$OPENSSL_IS_TONGSUO"
  kv local_dual_stack "$LOCAL_DUAL_STACK"
  kv local_dual_stack_skip_reason "$LOCAL_SKIP_REASON"
  kv local_dual_stack_line "$LOCAL_LINE"
  kv nginx_present "$NGINX_PRESENT"
  kv nginx_bin "$NGINX_BIN"
  kv nginx_version "$NGINX_VERSION"
  kv tongsuo_nginx "$TONGSUO_NGINX"
  kv tongsuo_cli "$TONGSUO_CLI"
  kv tongsuo_cli_identity "$TONGSUO_CLI_IDENTITY"
  kv nginx_config_lint "$NGINX_LINT"
  kv nginx_config_lint_reason "$NGINX_LINT_REASON"
  kv nginx_gmtls_handshake "$NGINX_HANDSHAKE"
  kv nginx_gmtls_skip_reason "$NGINX_HANDSHAKE_REASON"
  kv nginx_gmtls_client "$NGINX_CLIENT"
  kv gmtls_enc_cert_negotiated SKIP
  kv gmtls_enc_cert_skip_reason "GM/T 0024 encryption-cert negotiation is not implemented; RFC 8998 TLS 1.3 uses the sign cert. Tongsuo NTLS config is documented only."
  kv dengbao_claimed false
  kv mipin_claimed false
  kv hsm_claimed false
  kv g17_close NOT_CLAIMED
  kv g17_close_reason "This leftover is scripts + documented Tongsuo config + local Java dual-stack. It does not close 等保 / 密评 / 密码机 / HSM / qualified GM/T Provider."
  kv g18_status closed_unchanged
  kv audit_port_changed false
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "| Check | Result | Notes |"
  echo "|---|---|---|"
  echo "| JDK 21 | $JAVA_STATUS | $JAVA_VERSION_LINE |"
  echo "| Boot 4.1.1 | $BOOT_PARENT_MATCH | \`$BOOT_PARENT_GA\` |"
  echo "| Local Java dual-stack (intl TLS 1.3 + GM TLS 1.3) | $LOCAL_DUAL_STACK | ${LOCAL_SKIP_REASON} |"
  echo "| OpenSSL identity | recorded | $OPENSSL_IDENTITY (tongsuo=$OPENSSL_IS_TONGSUO) |"
  echo "| nginx present | $NGINX_PRESENT | $NGINX_BIN |"
  echo "| Tongsuo-built nginx | $TONGSUO_NGINX | stock nginx is not GMTLS |"
  echo "| Tongsuo s_client | $TONGSUO_CLI | $TONGSUO_CLI_IDENTITY |"
  echo "| nginx -t (Tongsuo config) | $NGINX_LINT | ${NGINX_LINT_REASON} |"
  echo "| Nginx/Tongsuo GMTLS handshake | $NGINX_HANDSHAKE | ${NGINX_HANDSHAKE_REASON} |"
  echo "| GM/T 0024 enc-cert negotiated | SKIP | RFC 8998 leftover; NTLS documented only |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| 等保 / 密评 / 密码机 / HSM | NOT_CLAIMED | dengbao=false mipin=false hsm=false |"
  echo "| G17 close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g17-gm-tls-nginx-dual-stack collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT"

if [ "$BOOT_PARENT_MATCH" != "PASS" ]; then
  fail "root pom parent is not org.springframework.boot:spring-boot-starter-parent:4.1.1"
fi
if [ "$JAVA_STATUS" = "FAIL" ]; then
  fail "JDK feature version is ${JAVA_FEATURE}, product baseline is 21"
fi
if [ "$LOCAL_DUAL_STACK" = "FAIL" ]; then
  fail "${LOCAL_SKIP_REASON:-local dual-stack failed}"
fi
if [ "$NGINX_HANDSHAKE" = "FAIL" ]; then
  fail "${NGINX_HANDSHAKE_REASON:-nginx GMTLS handshake failed}"
fi

log "DONE report=$REPORT nginx_gmtls=$NGINX_HANDSHAKE g17_close=NOT_CLAIMED"
echo "$OUT"
