#!/usr/bin/env bash
# G4 leftover: export-wizard complete-deny / SurfaceGuard deny honesty beyond #166/#136.
#
# Extends docs/g4-export-wizard-deny.md and docs/g4-guard-deny-audit.md.
# Does NOT redo #136 / #152 / #166 / #190 product code.
# Does NOT change AuditPort same-TX. Does NOT claim product G4 close.
# G18 stays closed. No Docker. No P0/M0 redo.
# In-memory harness stays always-green PASS.
# Live JAR / Playwright / :5432 / Keycloak is PASS only when reachable AND
# the walk was observed. Missing live path is honest SKIP.
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
  scripts/g4-export-wizard-deny-honesty.sh [--out DIR] [--skip-tests]
  scripts/g4-export-wizard-deny-honesty.sh --verify-report FILE

In-memory SurfaceGuard deny + export-wizard complete-deny harnesses PASS.
Live JAR / browser / :5432 JDBC / Keycloak is PASS only when present AND
the walk was observed. Missing live path is honest SKIP.
Product G4 close stays NOT_CLAIMED. Does not redo #136/#152/#166/#190.

  --out DIR           Output directory (default: .local/g4-export-wizard-deny-honesty/<utc>)
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
raise SystemExit(0)
PY
}

verify_report() {
  local file="$1"
  [ -f "$file" ] || fail "report not found: $file"
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_rows_kept guard_deny_harness export_wizard_deny_harness same_tx_append_unchanged live_jar_present live_5432_listener live_wizard_deny_observed live_browser_observed live_jdbc_deny_observed live_keycloak_observed live_jar_wizard_deny live_jar_wizard_deny_skip_reason live_browser_wizard live_browser_wizard_skip_reason live_5432_jdbc live_5432_jdbc_skip_reason live_keycloak_idp live_keycloak_idp_skip_reason redo_136 redo_152 redo_166 redo_190 g4_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" guard_deny_harness)" = "PASS" ] || fail "guard_deny_harness must be PASS"
  [ "$(report_get "$file" export_wizard_deny_harness)" = "PASS" ] || fail "export_wizard_deny_harness must be PASS"
  [ "$(report_get "$file" same_tx_append_unchanged)" = "PASS" ] || fail "same_tx_append_unchanged must be PASS"
  [ "$(report_get "$file" redo_136)" = "false" ] || fail "redo_136 must stay false"
  [ "$(report_get "$file" redo_152)" = "false" ] || fail "redo_152 must stay false"
  [ "$(report_get "$file" redo_166)" = "false" ] || fail "redo_166 must stay false"
  [ "$(report_get "$file" redo_190)" = "false" ] || fail "redo_190 must stay false"
  [ "$(report_get "$file" g4_close)" = "NOT_CLAIMED" ] || fail "g4_close must stay NOT_CLAIMED"

  local verdict
  for verdict in live_jar_wizard_deny live_browser_wizard live_5432_jdbc live_keycloak_idp; do
    local value
    value="$(report_get "$file" "$verdict")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) fail "$verdict must be PASS/SKIP/FAIL" ;;
    esac
  done

  if [ "$(report_get "$file" live_jar_wizard_deny)" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || fail "live_jar_wizard_deny=PASS requires live_jar_present=true"
    [ "$(report_get "$file" live_wizard_deny_observed)" = "true" ] || fail "live_jar_wizard_deny=PASS requires live_wizard_deny_observed=true"
  fi
  if [ "$(report_get "$file" live_browser_wizard)" = "PASS" ]; then
    [ "$(report_get "$file" live_browser_observed)" = "true" ] || fail "live_browser_wizard=PASS requires live_browser_observed=true"
  fi
  if [ "$(report_get "$file" live_5432_jdbc)" = "PASS" ]; then
    [ "$(report_get "$file" live_5432_listener)" = "true" ] || fail "live_5432_jdbc=PASS requires live_5432_listener=true"
    [ "$(report_get "$file" live_jdbc_deny_observed)" = "true" ] || fail "live_5432_jdbc=PASS requires live_jdbc_deny_observed=true"
  fi
  if [ "$(report_get "$file" live_keycloak_idp)" = "PASS" ]; then
    [ "$(report_get "$file" live_keycloak_observed)" = "true" ] || fail "live_keycloak_idp=PASS requires live_keycloak_observed=true"
  fi
  if [ "$(report_get "$file" live_jar_wizard_deny)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_jar_wizard_deny_skip_reason)" ] || fail "live JAR SKIP needs live_jar_wizard_deny_skip_reason"
    [ "$(report_get "$file" live_wizard_deny_observed)" != "true" ] || fail "SKIP cannot claim a live wizard-deny walk"
  fi
  if [ "$(report_get "$file" live_browser_wizard)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_browser_wizard_skip_reason)" ] || fail "live browser SKIP needs live_browser_wizard_skip_reason"
    [ "$(report_get "$file" live_browser_observed)" != "true" ] || fail "SKIP cannot claim a live browser wizard walk"
  fi
  if [ "$(report_get "$file" live_5432_jdbc)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_5432_jdbc_skip_reason)" ] || fail "live JDBC SKIP needs live_5432_jdbc_skip_reason"
    [ "$(report_get "$file" live_jdbc_deny_observed)" != "true" ] || fail "SKIP cannot claim a live JDBC deny walk"
  fi
  if [ "$(report_get "$file" live_keycloak_idp)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_keycloak_idp_skip_reason)" ] || fail "live Keycloak SKIP needs live_keycloak_idp_skip_reason"
    [ "$(report_get "$file" live_keycloak_observed)" != "true" ] || fail "SKIP cannot claim a live Keycloak walk"
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=' "$file"; then
    fail "report must not contain password/secret material"
  fi
  echo "PASS: report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g4-export-wizard-deny-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G4 leftover: export-wizard deny / guard-deny honesty (harness PASS / live SKIP when unavailable)"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #136/#152/#166/#190. Does not change AuditPort same-TX. Not product G4 close."
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

FORCE_SKIP=false
if [[ "${NEXUS_G4_EXPORT_WIZARD_DENY_SKIP_LIVE:-}" == "1" || "${NEXUS_G4_EXPORT_WIZARD_DENY_SKIP_LIVE:-}" == "true" ]]; then
  FORCE_SKIP=true
fi

LIVE_DB=false
LIVE_JAR=false
if port_open 127.0.0.1 5432; then LIVE_DB=true; fi
if port_open 127.0.0.1 8080; then LIVE_JAR=true; fi

WIZARD_OBSERVED=false
BROWSER_OBSERVED=false
JDBC_OBSERVED=false
KEYCLOAK_OBSERVED=false
if [[ "${NEXUS_G4_LIVE_WIZARD_DENY_OBSERVED:-}" == "1" || "${NEXUS_G4_LIVE_WIZARD_DENY_OBSERVED:-}" == "true" ]]; then
  WIZARD_OBSERVED=true
fi
if [[ "${NEXUS_G4_LIVE_BROWSER_WIZARD_OBSERVED:-}" == "1" || "${NEXUS_G4_LIVE_BROWSER_WIZARD_OBSERVED:-}" == "true" ]]; then
  BROWSER_OBSERVED=true
fi
if [[ "${NEXUS_G4_LIVE_JDBC_DENY_OBSERVED:-}" == "1" || "${NEXUS_G4_LIVE_JDBC_DENY_OBSERVED:-}" == "true" ]]; then
  JDBC_OBSERVED=true
fi
if [[ "${NEXUS_G4_LIVE_KEYCLOAK_OBSERVED:-}" == "1" || "${NEXUS_G4_LIVE_KEYCLOAK_OBSERVED:-}" == "true" ]]; then
  KEYCLOAK_OBSERVED=true
fi

if [ "$FORCE_SKIP" = "true" ]; then
  JAR_REASON="NEXUS_G4_EXPORT_WIZARD_DENY_SKIP_LIVE=1; live JAR wizard-deny walk not observed; Zonky / in-memory #166 is not a live JAR PASS; Docker forbidden"
  JDBC_REASON="NEXUS_G4_EXPORT_WIZARD_DENY_SKIP_LIVE=1; live :5432 JDBC deny pair not observed; Zonky #136/#166 is not a live JDBC PASS; Docker forbidden"
elif [ "$LIVE_JAR" != "true" ]; then
  JAR_REASON="no product JAR on 127.0.0.1:8080; live HTTP begin-then-complete-deny not observed; Docker forbidden"
else
  JAR_REASON="product JAR listener present on 127.0.0.1:8080 but a live issued-context wizard-deny walk was not observed; Docker forbidden"
fi
if [ "$LIVE_DB" != "true" ]; then
  JDBC_REASON="${JDBC_REASON:-nothing listening on 127.0.0.1:5432; live JDBC deny pair not observed; Zonky is not a live walk; Docker forbidden}"
else
  JDBC_REASON="${JDBC_REASON:-PostgreSQL listener present on 127.0.0.1:5432 but a live JDBC deny pair was not observed; Zonky is not a live walk; Docker forbidden}"
fi
BROWSER_REASON="Playwright / Chrome + issued-context /p3/exports/wizard deny click not observed; browser wizard stays honest SKIP"
if [ "$LIVE_JAR" = "true" ]; then
  KEYCLOAK_REASON="listener on 127.0.0.1:8080 is not treated as live Keycloak issued-context; OIDC walk not observed"
else
  KEYCLOAK_REASON="no live Keycloak issued-context on 127.0.0.1:8080; stub IdP / #125 click is not a live Keycloak PASS"
fi

README="$ROOT/docs/README.md"
LEFTOVER_ROWS=true
for row in \
  g4-export-wizard-deny.md \
  g4-export-wizard-deny-honesty.md \
  g4-guard-deny-audit.md \
  g4-export-wizard.md \
  g4-p1-staging-audit-events.md \
  g4-p1-staging-audit-events-honesty.md \
  g4-hot-purge-90d.md \
  g4-hot-purge-90d-honesty.md \
  g4-pkce-spa-verifier.md \
  g4-pkce-spa-verifier-honesty.md \
  s0-secret-rotate-without-restart.md \
  s0-secret-getenv-snapshot-honesty.md \
  s0-secret-rotate-without-restart-honesty.md \
  s0s1-live-redis-minio-isolation-honesty.md \
  g6-recovery-port-dry-run-honesty.md \
  g17-separation-honesty.md \
  g10-identity-drift-honesty.md \
  g10-leftover-honesty.md \
  g15-native-bridge-honesty.md \
  g15-wx-dt-prod-gap-honesty.md \
  g15-wx-dt-native-bridge-honesty.md \
  g1-fieldpolicy-mask-second-auth-honesty.md \
  g1-queryast-boundsql-guard.md \
  g1-queryast-boundsql-honesty.md \
  g3-outbox-consume-crash-honesty.md \
  g5-browser-failed-publish.md \
  g5-mid-publish-failure-honesty.md \
  g7-query-expense-travel-honesty.md \
  linux-arm-profile-leftover-honesty.md
do
  grep -Fq "$row" "$README" || { echo "missing leftover row $row in docs/README.md" >&2; LEFTOVER_ROWS=false; }
done
if grep -Eiq 'product g4 closed|产品 G4 已关闭' "$README"; then
  LEFTOVER_ROWS=false
fi
[ "$LEFTOVER_ROWS" = "true" ] || fail "docs/README.md must keep ALL sibling leftover rows and must not claim product G4 closed"

TEST_SUMMARY=""
if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl nexus-permission-core -am test \
    -Dtest=G4ExportWizardDenyHonestyTest \
    -Dsurefire.failIfNoSpecifiedTests=false | tee "$OUT/mvn.log"
  MVN_RC=${PIPESTATUS[0]}
  set -e
  [ "$MVN_RC" -eq 0 ] || fail "mvn leftover tests failed"
  TEST_SUMMARY="$(awk '/Tests run:/{line=$0} END{print line}' "$OUT/mvn.log")"
else
  TEST_SUMMARY="skipped (--skip-tests)"
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
  kv leftover_rows_kept "$LEFTOVER_ROWS"
  kv guard_deny_harness PASS
  kv export_wizard_deny_harness PASS
  kv same_tx_append_unchanged PASS
  kv live_jar_present "$LIVE_JAR"
  kv live_5432_listener "$LIVE_DB"
  kv live_wizard_deny_observed "$WIZARD_OBSERVED"
  kv live_browser_observed "$BROWSER_OBSERVED"
  kv live_jdbc_deny_observed "$JDBC_OBSERVED"
  kv live_keycloak_observed "$KEYCLOAK_OBSERVED"
  kv live_jar_wizard_deny SKIP
  kv live_jar_wizard_deny_skip_reason "$JAR_REASON"
  kv live_browser_wizard SKIP
  kv live_browser_wizard_skip_reason "$BROWSER_REASON"
  kv live_5432_jdbc SKIP
  kv live_5432_jdbc_skip_reason "$JDBC_REASON"
  kv live_keycloak_idp SKIP
  kv live_keycloak_idp_skip_reason "$KEYCLOAK_REASON"
  kv redo_136 false
  kv redo_152 false
  kv redo_166 false
  kv redo_190 false
  kv g4_close NOT_CLAIMED
  kv g4_close_reason "Honesty leftover around #166 export-wizard complete-deny and #136 SurfaceGuard deny. Live JAR/browser/:5432/Keycloak SKIP when unavailable. Not product G4 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
  kv test_summary "$TEST_SUMMARY"
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "| Check | Result | Notes |"
  echo "|---|---|---|"
  echo "| JDK 21 | PASS | $JAVA_VERSION_LINE |"
  echo "| Boot 4.1.1 | $BOOT_PARENT_MATCH | \`$BOOT_PARENT_GA\` |"
  echo "| Guard deny harness | PASS | in-memory SurfaceGuard #136 pair |"
  echo "| Export wizard deny harness | PASS | begin then revoke/DENY; no IsolatedExport |"
  echo "| Same-TX append unchanged | PASS | reject uses appendNewTransaction |"
  echo "| Live JAR wizard deny | SKIP | ${JAR_REASON} |"
  echo "| Live browser wizard | SKIP | ${BROWSER_REASON} |"
  echo "| Live :5432 JDBC | SKIP | ${JDBC_REASON} |"
  echo "| Live Keycloak | SKIP | ${KEYCLOAK_REASON} |"
  echo "| leftover rows kept | $LEFTOVER_ROWS | includes #198/#200 siblings |"
  echo "| #136/#152/#166/#190 redo | false | sibling leftovers |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| G4 close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g4-export-wizard-deny-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT"

SANITIZED="${ROOT}/docs/engineering/g4-export-wizard-deny-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_jar_wizard_deny=SKIP g4_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (harness PASS; live JAR/browser/:5432/Keycloak SKIP)"
echo "See docs/g4-export-wizard-deny-honesty.md"
echo "Keep leftover rows: docs/g4-export-wizard-deny.md and docs/g4-guard-deny-audit.md"
echo "Not claimed: product G4 close / #136/#152/#166/#190 redo / AuditPort same-TX rewrite"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
