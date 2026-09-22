#!/usr/bin/env bash
# G17 leftover: 三权分立 (SoD) honesty beyond #99.
#
# Extends docs/g17-separation-of-duties.md. Does NOT redo
# #96/#99/#110/#118/#129/#133/#154/#158/#160/#172.
# Does NOT claim product G17 / 等保 / 密评 / SoD close.
# G18 stays closed. No Docker. No P0/M0 redo.
#
# #99 in-process catalog + exclusive pairs stay always-green inventory.
# Remaining admin / auditor / operator HTTP boundaries are PASS only when
# a live product JAR + live IdP + issued context walk was observed.
# Missing live IdP / JAR is honest SKIP.
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
JSON_OUT="${NEXUS_G17_SOD_JSON:-$ROOT/docs/g17-separation-honesty/walk.json}"
VERIFY_REPORT=""
SKIP_TESTS=0

usage() {
  cat <<'EOF'
Usage:
  scripts/g17-separation-honesty.sh [--out DIR] [--json-out FILE] [--skip-tests]
  scripts/g17-separation-honesty.sh --verify-report FILE

#99 catalog exclusive-pair inventory is always-green.
Live admin/auditor/operator HTTP SoD is PASS only when live JAR + IdP +
issued context walks were observed. Missing live IdP/JAR is honest SKIP.

  --out DIR           Output directory (default: .local/g17-separation-honesty/<utc>)
  --json-out FILE     Sanitized walk.json
  --skip-tests        Inventory + honesty only (do not run mvn)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --json-out) JSON_OUT="${2:?}"; shift 2 ;;
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used catalog_99_inventory exclusive_pairs_documented operator_not_platform_duty p1_all_demo_rewired p1_all_demo_status product_duty_ui product_duty_ui_status leftover_rows_kept live_jar_endpoint_set live_jar_listener live_jar_present live_idp_reachable live_idp_context_set oidc_discovery live_sod_walk live_sod_skip_reason live_admin_boundary live_auditor_boundary live_operator_boundary live_admin_observed live_auditor_observed live_operator_observed dengbao_claimed mipin_claimed hsm_claimed g17_close g17_sod_close g18_status audit_port_changed p0_m0_redone redo_96 redo_99 redo_110 redo_118 redo_129 redo_133 redo_154 redo_158 redo_160 redo_172"
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
  [ "$(report_get "$file" dengbao_claimed)" = "false" ] || fail "dengbao_claimed must stay false"
  [ "$(report_get "$file" mipin_claimed)" = "false" ] || fail "mipin_claimed must stay false"
  [ "$(report_get "$file" hsm_claimed)" = "false" ] || fail "hsm_claimed must stay false"
  [ "$(report_get "$file" g17_close)" = "NOT_CLAIMED" ] || fail "g17_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" g17_sod_close)" = "NOT_CLAIMED" ] || fail "g17_sod_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" redo_96)" = "false" ] || fail "redo_96 must stay false"
  [ "$(report_get "$file" redo_99)" = "false" ] || fail "redo_99 must stay false"
  [ "$(report_get "$file" redo_110)" = "false" ] || fail "redo_110 must stay false"
  [ "$(report_get "$file" redo_118)" = "false" ] || fail "redo_118 must stay false"
  [ "$(report_get "$file" redo_129)" = "false" ] || fail "redo_129 must stay false"
  [ "$(report_get "$file" redo_133)" = "false" ] || fail "redo_133 must stay false"
  [ "$(report_get "$file" redo_154)" = "false" ] || fail "redo_154 must stay false"
  [ "$(report_get "$file" redo_158)" = "false" ] || fail "redo_158 must stay false"
  [ "$(report_get "$file" redo_160)" = "false" ] || fail "redo_160 must stay false"
  [ "$(report_get "$file" redo_172)" = "false" ] || fail "redo_172 must stay false"
  [ "$(report_get "$file" catalog_99_inventory)" = "PASS" ] || fail "catalog_99_inventory must be PASS"
  [ "$(report_get "$file" exclusive_pairs_documented)" = "true" ] || fail "exclusive_pairs_documented must be true"
  [ "$(report_get "$file" operator_not_platform_duty)" = "true" ] || fail "operator_not_platform_duty must be true"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" p1_all_demo_rewired)" = "false" ] || fail "p1_all_demo_rewired must stay false (P1 ALL_DEMO still open)"
  [ "$(report_get "$file" p1_all_demo_status)" = "OPEN" ] || fail "p1_all_demo_status must stay OPEN"
  [ "$(report_get "$file" product_duty_ui)" = "false" ] || fail "product_duty_ui must stay false"
  [ "$(report_get "$file" product_duty_ui_status)" = "OPEN" ] || fail "product_duty_ui_status must stay OPEN"

  local live admin auditor operator
  live="$(report_get "$file" live_sod_walk)"
  admin="$(report_get "$file" live_admin_boundary)"
  auditor="$(report_get "$file" live_auditor_boundary)"
  operator="$(report_get "$file" live_operator_boundary)"
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) fail "live_sod_walk must be PASS/SKIP/FAIL" ;;
  esac
  for role in "$admin" "$auditor" "$operator"; do
    case "$role" in
      PASS|SKIP|FAIL) ;;
      *) fail "live admin/auditor/operator boundary must be PASS/SKIP/FAIL" ;;
    esac
  done
  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || fail "live_sod_walk=PASS requires live_jar_present=true"
    [ "$(report_get "$file" live_idp_reachable)" = "true" ] || fail "live_sod_walk=PASS requires live_idp_reachable=true"
    [ "$(report_get "$file" live_idp_context_set)" = "true" ] || fail "live_sod_walk=PASS requires live_idp_context_set=true"
    [ "$(report_get "$file" oidc_discovery)" = "PASS" ] || fail "live_sod_walk=PASS requires oidc_discovery=PASS"
    [ "$(report_get "$file" live_admin_observed)" = "true" ] || fail "live_sod_walk=PASS requires live_admin_observed=true"
    [ "$(report_get "$file" live_auditor_observed)" = "true" ] || fail "live_sod_walk=PASS requires live_auditor_observed=true"
    [ "$(report_get "$file" live_operator_observed)" = "true" ] || fail "live_sod_walk=PASS requires live_operator_observed=true"
    [ "$admin" = "PASS" ] || fail "live_sod_walk=PASS requires live_admin_boundary=PASS"
    [ "$auditor" = "PASS" ] || fail "live_sod_walk=PASS requires live_auditor_boundary=PASS"
    [ "$operator" = "PASS" ] || fail "live_sod_walk=PASS requires live_operator_boundary=PASS"
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_sod_skip_reason)" ] || fail "live_sod_walk=SKIP needs live_sod_skip_reason"
    [ "$(report_get "$file" live_admin_observed)" != "true" ] || fail "SKIP cannot claim live_admin_observed=true"
    [ "$(report_get "$file" live_auditor_observed)" != "true" ] || fail "SKIP cannot claim live_auditor_observed=true"
    [ "$(report_get "$file" live_operator_observed)" != "true" ] || fail "SKIP cannot claim live_operator_observed=true"
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|totpSecret=|ACCESS_KEY=|TOKEN=' "$file"; then
    fail "report must not contain password/secret/token material"
  fi
  echo "PASS: report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g17-separation-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G17 leftover: 三权分立 honesty beyond #99"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #96/#99/#110/#118/#129/#133/#154/#158/#160/#172."
echo "Not product G17 / SoD / 等保 / 密评. G18 stays closed."
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
LEFTOVER_ROWS=false
if grep -Fq '[g17-separation-of-duties](./g17-separation-of-duties.md)' "$README" \
  && grep -Fq '[g17-gm-tls-dual-cert](./g17-gm-tls-dual-cert.md)' "$README" \
  && grep -Fq '[g17-admin-totp-enrollment](./g17-admin-totp-enrollment.md)' "$README" \
  && grep -Fq '[g17-totp-enrollment-ux](./g17-totp-enrollment-ux.md)' "$README" \
  && grep -Fq '[g17-admin-mfa-recovery-email](./g17-admin-mfa-recovery-email.md)' "$README" \
  && grep -Fq '[g17-mfa-recovery-email-honesty](./g17-mfa-recovery-email-honesty.md)' "$README" \
  && grep -Fq '[g17-vue-otp-writeback](./g17-vue-otp-writeback.md)' "$README" \
  && grep -Fq '[g17-gm-tls-nginx-dual-stack](./g17-gm-tls-nginx-dual-stack.md)' "$README" \
  && grep -Fq '[g17-gm-tls-nginx-handshake](./g17-gm-tls-nginx-handshake.md)' "$README" \
  && grep -Fq '[g5-browser-failed-publish](./g5-browser-failed-publish.md)' "$README" \
  && grep -Fq '[s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md)' "$README" \
  && grep -Fq '[g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md)' "$README" \
  && grep -Fq '[g15-native-bridge-honesty](./g15-native-bridge-honesty.md)' "$README" \
  && grep -Fq '[g10-identity-drift-honesty](./g10-identity-drift-honesty.md)' "$README" \
  && grep -Fq '[g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md)' "$README" \
  && grep -Fq '[g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md)' "$README" \
  && grep -Fq '[g17-separation-honesty](./g17-separation-honesty.md)' "$README"; then
  LEFTOVER_ROWS=true
fi
[ "$LEFTOVER_ROWS" = "true" ] || fail "docs/README.md must keep g17 siblings + g5/g4/s0/g15/g10/g1 leftovers + this leftover"

eval "$(python3 - "$ROOT" <<'PY'
import pathlib, sys
root = pathlib.Path(sys.argv[1])
catalog = (root / "nexus-permission-core/src/main/java/one/nexus/permission/admin/PlatformDutyCatalog.java").read_text(encoding="utf-8")
actor = (root / "nexus-p1-tenant/src/main/java/one/nexus/p1/domain/Actor.java").read_text(encoding="utf-8")
demo = (root / "nexus-p1-tenant/src/main/java/one/nexus/p1/infra/isolation/DemoPlatformContextFilter.java").read_text(encoding="utf-8")
needed = (
    'SYSTEM_ADMIN = "platform_system_admin"' in catalog
    and 'SECURITY_ADMIN = "platform_security_admin"' in catalog
    and 'AUDIT_ADMIN = "platform_audit_admin"' in catalog
    and "exclusivePairs()" in catalog
    and "tenant_member remains demo bootstrap" in catalog
)
print("CATALOG_OK=%s" % ("true" if needed else "false"))
print("OPERATOR_NOT_DUTY=%s" % ("true" if "tenant_member remains demo bootstrap and is not a platform duty" in catalog else "false"))
all_demo = "duties.contains(PlatformDuty.ALL_DEMO)" in actor
demo_stamp = "Set.of(PlatformDuty.ALL_DEMO)" in demo
print("P1_ALL_DEMO_STILL=%s" % ("true" if all_demo and demo_stamp else "false"))
ui_hits = []
web = root / "web/src"
if web.is_dir():
    for path in web.rglob("*"):
        if path.suffix.lower() not in {".vue", ".js", ".ts"}:
            continue
        text = path.read_text(encoding="utf-8", errors="ignore")
        if "platform_system_admin" in text and ("assign" in text.lower() or "duties/example" in text):
            ui_hits.append(str(path))
print("PRODUCT_DUTY_UI=%s" % ("true" if ui_hits else "false"))
PY
)"
[ "$CATALOG_OK" = "true" ] || fail "#99 PlatformDutyCatalog inventory missing"
[ "$OPERATOR_NOT_DUTY" = "true" ] || fail "tenant_member must stay documented as not a platform duty"
[ "$P1_ALL_DEMO_STILL" = "true" ] || fail "P1 ALL_DEMO bypass must still be present (remaining SoD path)"
[ "$PRODUCT_DUTY_UI" = "false" ] || fail "product duty-assign UI appeared; leftover must stay honest"

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

LIVE_ADMIN_OBS=false
LIVE_AUDITOR_OBS=false
LIVE_OPERATOR_OBS=false
LIVE_WALK=SKIP
LIVE_ADMIN=SKIP
LIVE_AUDITOR=SKIP
LIVE_OPERATOR=SKIP

if [ "$LIVE_ENDPOINT_SET" != "true" ] && [ "$LIVE_LISTENER" != "true" ] && [ "$KC_REACHABLE" != "true" ]; then
  LIVE_REASON="live JAR unset (NEXUS_BASE_URL / NEXUS_G17_SOD_LIVE_BASE); nothing on 127.0.0.1:8080 / :8088; live IdP unreachable; Docker forbidden"
elif [ "$KC_REACHABLE" != "true" ]; then
  LIVE_REASON="live IdP unreachable (NEXUS_KEYCLOAK_ISSUER / loopback :8080/:8180); admin/auditor/operator HTTP SoD SKIP"
elif [ "$LIVE_CONTEXT_SET" != "true" ]; then
  LIVE_REASON="issued SoD context unset (NEXUS_G17_SOD_LIVE_CONTEXT_ID); live walk SKIP"
elif [ "$LIVE_LISTENER" != "true" ]; then
  LIVE_REASON="live IdP reachable but product JAR not listening; live SoD walk SKIP"
else
  LIVE_REASON="live listener/IdP present but admin/auditor/operator HTTP walks were not observed"
fi

CATALOG_INVENTORY=PASS
TEST_SUMMARY=""
if [ "$SKIP_TESTS" = "0" ] && command -v mvn >/dev/null 2>&1; then
  echo "== Inventory only: do not redo #99 G17AuditAdminIT / G17SeparationEngineTest"
  TEST_SUMMARY="mvn present; leftover does not re-run #99 ITs"
else
  TEST_SUMMARY="inventory-only (no #99 IT redo)"
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
  kv catalog_99_inventory "$CATALOG_INVENTORY"
  kv exclusive_pairs_documented true
  kv operator_not_platform_duty "$OPERATOR_NOT_DUTY"
  kv p1_all_demo_rewired false
  kv p1_all_demo_status OPEN
  kv product_duty_ui false
  kv product_duty_ui_status OPEN
  kv leftover_rows_kept "$LEFTOVER_ROWS"
  kv live_jar_endpoint_set "$LIVE_ENDPOINT_SET"
  kv live_jar_listener "$LIVE_LISTENER"
  kv live_jar_present "$LIVE_JAR_PRESENT"
  kv live_idp_reachable "$KC_REACHABLE"
  kv live_idp_context_set "$LIVE_CONTEXT_SET"
  kv oidc_discovery "$OIDC_DISCOVERY"
  kv probed_issuer "${KEYCLOAK_ISSUER:-}"
  kv live_sod_walk "$LIVE_WALK"
  kv live_sod_skip_reason "$LIVE_REASON"
  kv live_admin_boundary "$LIVE_ADMIN"
  kv live_auditor_boundary "$LIVE_AUDITOR"
  kv live_operator_boundary "$LIVE_OPERATOR"
  kv live_admin_observed "$LIVE_ADMIN_OBS"
  kv live_auditor_observed "$LIVE_AUDITOR_OBS"
  kv live_operator_observed "$LIVE_OPERATOR_OBS"
  kv dengbao_claimed false
  kv mipin_claimed false
  kv hsm_claimed false
  kv g17_close NOT_CLAIMED
  kv g17_sod_close NOT_CLAIMED
  kv g17_close_reason "Honesty leftover around #99 SoD. Live admin/auditor/operator HTTP SKIP when JAR/IdP unavailable. P1 ALL_DEMO and product duty UI stay OPEN. Not product G17 / 等保 / 密评."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
  kv redo_96 false
  kv redo_99 false
  kv redo_110 false
  kv redo_118 false
  kv redo_129 false
  kv redo_133 false
  kv redo_154 false
  kv redo_158 false
  kv redo_160 false
  kv redo_172 false
  kv test_summary "$TEST_SUMMARY"
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
    "leftover": "g17-separation-honesty",
    "sibling": "g17-separation-of-duties",
    "catalog_99_inventory": vals.get("catalog_99_inventory"),
    "live_sod_walk": vals.get("live_sod_walk"),
    "live_sod_skip_reason": vals.get("live_sod_skip_reason"),
    "live_admin_boundary": vals.get("live_admin_boundary"),
    "live_auditor_boundary": vals.get("live_auditor_boundary"),
    "live_operator_boundary": vals.get("live_operator_boundary"),
    "p1_all_demo_status": vals.get("p1_all_demo_status"),
    "product_duty_ui_status": vals.get("product_duty_ui_status"),
    "g17Close": "NOT_CLAIMED",
    "g17SodClose": "NOT_CLAIMED",
    "g18": "closed_unchanged",
    "timestampUtc": vals.get("timestamp_utc"),
    "evidence": "scripts/g17-separation-honesty.sh",
}
open(out, "w", encoding="utf-8").write(json.dumps(walk, indent=2) + "\n")
print("wrote", out)
PY

verify_report "$REPORT"

echo "== Honesty fixtures"
verify_report "$HERE/g17-separation-honesty/fixtures/honest-skip.env"
if ( verify_report "$HERE/g17-separation-honesty/fixtures/fake-live-pass.env" ); then
  fail "fake-live-pass.env must be rejected"
fi
echo "PASS: fake-live-pass.env rejected"
if ( verify_report "$HERE/g17-separation-honesty/fixtures/fake-g17-close.env" ); then
  fail "fake-g17-close.env must be rejected"
fi
echo "PASS: fake-g17-close.env rejected"
if ( verify_report "$HERE/g17-separation-honesty/fixtures/fake-p1-rewired.env" ); then
  fail "fake-p1-rewired.env must be rejected"
fi
echo "PASS: fake-p1-rewired.env rejected"

SANITIZED="${ROOT}/docs/engineering/g17-separation-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_sod=$LIVE_WALK g17_close=NOT_CLAIMED g17_sod_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (live SoD $LIVE_WALK catalog=$CATALOG_INVENTORY)"
echo "See docs/g17-separation-honesty.md"
echo "Not claimed: product G17 / SoD close / 等保 / 密评 / P1 ALL_DEMO rewired / duty UI"
echo "Not redone: #96/#99/#110/#118/#129/#133/#154/#158/#160/#172"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
echo "walk=$JSON_OUT"
