#!/usr/bin/env bash
# G5 leftover: browser failed-publish click / PublishHook path toward real DDL.
# Does not redo #94 evolve DDL abort or #145 mid-publish failure rollback.
# Does not start a live JAR, IdP, or browser walk.
# Honest SKIP when live browser/IdP/JAR are unavailable.
# Does not claim product G5 close. Does not reopen G18. No Docker.
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
  scripts/g5-browser-failed-publish.sh [--out DIR] [--skip-tests]
  scripts/g5-browser-failed-publish.sh --verify-report FILE

Browser failed-publish click is PASS only when live JAR + IdP + browser
are present AND a failed Publish click through SchemaServicePublishHook
toward real DDL was observed with mid-fail/abort still honest.
Missing live browser/IdP/JAR is honest SKIP. Product G5 close stays
NOT_CLAIMED. The publish-log viewer is not a failed-publish click.

  --out DIR           Output directory (default: .local/g5-browser-failed-publish/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used live_jar jar_url live_idp idp_url live_browser browser_name click_observed publishhook_ddl_observed mid_fail_abort_honest browser_failed_publish browser_skip_reason simulate_publish_log_is_fail_click g5_close g18_status audit_port_changed p0_m0_redone g5_94_145_redone"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || {
      case "$key" in
        jar_url|idp_url|browser_name) ;;
        *) fail "missing key $key in $file" ;;
      esac
    }
  done

  [ "$(report_get "$file" java_feature)" = "21" ] || fail "java_feature must be 21"
  [ "$(report_get "$file" boot_parent_version)" = "4.1.1" ] || fail "boot_parent_version must be 4.1.1"
  [ "$(report_get "$file" boot_parent_match)" = "PASS" ] || fail "boot_parent_match must be PASS"
  [ "$(report_get "$file" docker_used)" = "false" ] || fail "docker_used must be false"
  [ "$(report_get "$file" g18_status)" = "closed_unchanged" ] || fail "g18_status must stay closed_unchanged"
  [ "$(report_get "$file" audit_port_changed)" = "false" ] || fail "audit_port_changed must be false"
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || fail "p0_m0_redone must be false"
  [ "$(report_get "$file" g5_94_145_redone)" = "false" ] || fail "must not redo #94/#145"
  [ "$(report_get "$file" g5_close)" = "NOT_CLAIMED" ] || fail "g5_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" simulate_publish_log_is_fail_click)" = "false" ] || fail "publish-log viewer is not a failed-publish click"

  local value
  value="$(report_get "$file" browser_failed_publish)"
  case "$value" in
    PASS|SKIP|FAIL) ;;
    *) fail "browser_failed_publish must be PASS/SKIP/FAIL" ;;
  esac

  if [ "$value" = "PASS" ]; then
    [ "$(report_get "$file" live_jar)" = "true" ] || fail "browser_failed_publish=PASS requires live_jar=true"
    [ "$(report_get "$file" live_idp)" = "true" ] || fail "browser_failed_publish=PASS requires live_idp=true"
    [ "$(report_get "$file" live_browser)" = "true" ] || fail "browser_failed_publish=PASS requires live_browser=true"
    [ "$(report_get "$file" click_observed)" = "true" ] || fail "browser_failed_publish=PASS requires click_observed=true"
    [ "$(report_get "$file" publishhook_ddl_observed)" = "true" ] || fail "browser_failed_publish=PASS requires publishhook_ddl_observed=true"
    [ "$(report_get "$file" mid_fail_abort_honest)" = "true" ] || fail "browser_failed_publish=PASS requires mid_fail_abort_honest=true"
  fi
  if [ "$value" = "SKIP" ]; then
    [ -n "$(report_get "$file" browser_skip_reason)" ] || fail "browser SKIP needs browser_skip_reason"
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
  OUT="$ROOT/.local/g5-browser-failed-publish/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G5 browser failed-publish / PublishHook honesty leftover"
echo "cwd=$ROOT"
echo "Does not redo #94/#145. Does not claim product G5 close."
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

probe_http() {
  local url="$1"
  command -v curl >/dev/null 2>&1 || return 1
  curl -fsS --max-time 1 -o /dev/null -w '%{http_code}' "$url" 2>/dev/null | grep -Eq '^[2-4][0-9][0-9]$'
}

JAR_URL="${NEXUS_G5_JAR_HEALTH:-}"
if [ -z "$JAR_URL" ]; then
  if [ -n "${NEXUS_BASE_URL:-}" ]; then
    JAR_URL="${NEXUS_BASE_URL%/}/actuator/health"
  else
    JAR_URL="http://127.0.0.1:8080/actuator/health"
  fi
fi
LIVE_JAR=false
if probe_http "$JAR_URL"; then
  LIVE_JAR=true
fi

IDP_URL="${NEXUS_G5_IDP_DISCOVERY:-}"
if [ -z "$IDP_URL" ]; then
  if [ -n "${NEXUS_KEYCLOAK_ISSUER:-}" ]; then
    IDP_URL="${NEXUS_KEYCLOAK_ISSUER%/}/.well-known/openid-configuration"
  else
    IDP_URL="http://127.0.0.1:8080/realms/nexus/.well-known/openid-configuration"
  fi
fi
LIVE_IDP=false
if probe_http "$IDP_URL"; then
  LIVE_IDP=true
fi

BROWSER_NAME=""
for candidate in google-chrome google-chrome-stable chromium chromium-browser chrome; do
  if command -v "$candidate" >/dev/null 2>&1; then
    BROWSER_NAME="$candidate"
    break
  fi
done
LIVE_BROWSER=false
if [ -n "$BROWSER_NAME" ] || [ "${NEXUS_G5_BROWSER_PRESENT:-}" = "true" ]; then
  LIVE_BROWSER=true
  [ -n "$BROWSER_NAME" ] || BROWSER_NAME="env-present"
fi

CLICK_OBSERVED=false
DDL_OBSERVED=false
ABORT_HONEST=false
[ "${NEXUS_G5_FAILED_PUBLISH_CLICK_OBSERVED:-}" = "true" ] && CLICK_OBSERVED=true
[ "${NEXUS_G5_PUBLISHHOOK_DDL_OBSERVED:-}" = "true" ] && DDL_OBSERVED=true
[ "${NEXUS_G5_MID_FAIL_ABORT_HONEST:-}" = "true" ] && ABORT_HONEST=true

SKIP_REASON="live browser/IdP/JAR unavailable; jar=${LIVE_JAR} idp=${LIVE_IDP} browser=${LIVE_BROWSER}; publish-log viewer is not a failed-publish click; Docker forbidden"
if [ "$LIVE_JAR" = "true" ] && [ "$LIVE_IDP" = "true" ] && [ "$LIVE_BROWSER" = "true" ]; then
  SKIP_REASON="live surfaces listed but a failed Publish click through SchemaServicePublishHook toward real DDL was not observed; mid-fail/abort honesty stays on #94/#145; publish-log viewer is not a failed-publish click"
fi

TEST_SUMMARY=""
if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl nexus-p3-studio -am test \
    -Dtest=G5BrowserFailedPublishHonestyTest \
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
  kv live_jar "$LIVE_JAR"
  kv jar_url "$JAR_URL"
  kv live_idp "$LIVE_IDP"
  kv idp_url "$IDP_URL"
  kv live_browser "$LIVE_BROWSER"
  kv browser_name "$BROWSER_NAME"
  kv click_observed "$CLICK_OBSERVED"
  kv publishhook_ddl_observed "$DDL_OBSERVED"
  kv mid_fail_abort_honest "$ABORT_HONEST"
  kv browser_failed_publish SKIP
  kv browser_skip_reason "$SKIP_REASON"
  kv simulate_publish_log_is_fail_click false
  kv g5_close NOT_CLAIMED
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
  kv g5_94_145_redone false
  kv test_summary "$TEST_SUMMARY"
} > "$OUT/report.env"

{
  echo "# G5 browser failed-publish / PublishHook honesty"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| browser failed-publish click | SKIP |"
  echo "| product G5 close | NOT_CLAIMED |"
  echo "| #94 / #145 redo | false |"
  echo
  echo "Reason: $SKIP_REASON"
} > "$OUT/matrix.md"

verify_report "$OUT/report.env"
log "wrote $OUT/report.env"
echo
echo "PASS: honesty leftover recorded (browser failed-publish SKIP)"
echo "See docs/g5-browser-failed-publish.md"
echo "Not claimed: product G5 close"
echo "out=$OUT"
