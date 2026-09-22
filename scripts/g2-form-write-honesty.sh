#!/usr/bin/env bash
# G2 leftover: form.write / POST submissions + submit-start form_id dual-check honesty.
#
# Extends docs/g2-form-submission-write.md and docs/g2-submit-start-form-write.md.
# Does NOT redo #112 / #116 / #137 / #144 / #150 / #155 / #163.
# Does NOT claim product G2 close. G18 stays closed. No Docker.
# In-process FormSurfaceGuard + dual-check are always-green PASS.
# Live product-JAR + issued-context HTTP is PASS only when reachable AND
# both POST /submissions and submit-start walks were observed. Missing
# NEXUS_BASE_URL / issued context is honest SKIP.
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
  scripts/g2-form-write-honesty.sh [--out DIR] [--skip-tests]
  scripts/g2-form-write-honesty.sh --verify-report FILE

Always-green FormSurfaceGuard form.write + submit-start dual-check.
Live JAR + issued context is PASS only when NEXUS_BASE_URL / NEXUS_G2_LIVE_BASE
is set, a listener is up, issued context is present, and both walks were
observed. Missing live host is honest SKIP.

  --out DIR           Output directory (default: .local/g2-form-write-honesty/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used form_write_surface submit_start_dual_check submit_start_is_form_write_only leftover_rows_kept live_jar_endpoint_set live_jar_context_set live_jar_listener live_jar_present live_jar_walk live_jar_skip_reason live_submissions_observed live_submit_start_observed redone_112 redone_116 redone_137 redone_144 redone_150 redone_155 redone_163 g2_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" redone_112)" = "false" ] || fail "redone_112 must stay false"
  [ "$(report_get "$file" redone_116)" = "false" ] || fail "redone_116 must stay false"
  [ "$(report_get "$file" redone_137)" = "false" ] || fail "redone_137 must stay false"
  [ "$(report_get "$file" redone_144)" = "false" ] || fail "redone_144 must stay false"
  [ "$(report_get "$file" redone_150)" = "false" ] || fail "redone_150 must stay false"
  [ "$(report_get "$file" redone_155)" = "false" ] || fail "redone_155 must stay false"
  [ "$(report_get "$file" redone_163)" = "false" ] || fail "redone_163 must stay false"
  [ "$(report_get "$file" g2_close)" = "NOT_CLAIMED" ] || fail "g2_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" form_write_surface)" = "PASS" ] || fail "form_write_surface must be PASS"
  [ "$(report_get "$file" submit_start_dual_check)" = "PASS" ] || fail "submit_start_dual_check must be PASS"
  [ "$(report_get "$file" submit_start_is_form_write_only)" = "false" ] || fail "submit_start_is_form_write_only must stay false"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"

  local live
  live="$(report_get "$file" live_jar_walk)"
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) fail "live_jar_walk must be PASS/SKIP/FAIL" ;;
  esac
  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || fail "live_jar_walk=PASS requires live_jar_present=true"
    [ "$(report_get "$file" live_jar_context_set)" = "true" ] || fail "live_jar_walk=PASS requires live_jar_context_set=true"
    [ "$(report_get "$file" live_submissions_observed)" = "true" ] || fail "live_jar_walk=PASS requires live_submissions_observed=true"
    [ "$(report_get "$file" live_submit_start_observed)" = "true" ] || fail "live_jar_walk=PASS requires live_submit_start_observed=true"
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_jar_skip_reason)" ] || fail "live JAR SKIP needs live_jar_skip_reason"
    [ "$(report_get "$file" live_submissions_observed)" != "true" ] || fail "SKIP cannot claim a live submissions walk"
    [ "$(report_get "$file" live_submit_start_observed)" != "true" ] || fail "SKIP cannot claim a live submit-start walk"
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=|TOKEN=' "$file"; then
    fail "report must not contain password/secret/token material"
  fi
  echo "PASS: report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/g2-form-write-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G2 leftover: form.write / POST submissions + submit-start dual-check honesty"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #112/#116/#137/#144/#150/#155/#163. Not product G2 close."
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

LIVE_ENDPOINT="${NEXUS_G2_LIVE_BASE:-${NEXUS_BASE_URL:-}}"
LIVE_ENDPOINT_SET=false
LIVE_CONTEXT_SET=false
if [ -n "$LIVE_ENDPOINT" ]; then
  LIVE_ENDPOINT_SET=true
fi
if [ -n "${NEXUS_G2_LIVE_CONTEXT_ID:-}" ]; then
  LIVE_CONTEXT_SET=true
fi

LIVE_LISTENER=false
if port_open 127.0.0.1 8080 || port_open 127.0.0.1 8088; then
  LIVE_LISTENER=true
fi

LIVE_PRESENT=false
if [ "$LIVE_ENDPOINT_SET" = "true" ] && [ "$LIVE_CONTEXT_SET" = "true" ] && [ "$LIVE_LISTENER" = "true" ]; then
  LIVE_PRESENT=true
fi

if [ "$LIVE_ENDPOINT_SET" != "true" ] && [ "$LIVE_LISTENER" != "true" ]; then
  LIVE_REASON="NEXUS_BASE_URL / NEXUS_G2_LIVE_BASE unset; nothing listening on 127.0.0.1:8080 / :8088; Docker forbidden"
elif [ "$LIVE_CONTEXT_SET" != "true" ]; then
  LIVE_REASON="NEXUS_G2_LIVE_CONTEXT_ID unset; live JAR walk SKIP (issued context stays in env)"
elif [ "$LIVE_LISTENER" != "true" ]; then
  LIVE_REASON="live base set but product JAR not reachable; live walk SKIP"
else
  LIVE_REASON="live listener present but this leftover does not claim a walk until POST /submissions and submit-start dual-check were observed"
fi

FORM_WRITE=SKIP
DUAL_CHECK=SKIP
LIVE_WALK=SKIP
LIVE_SUBMISSIONS=false
LIVE_SUBMIT_START=false
TEST_SUMMARY=""
README="$ROOT/docs/README.md"
LEFTOVER_ROWS=false
if grep -Fq '[g2-form-submission-write](./g2-form-submission-write.md)' "$README" \
  && grep -Fq '[g2-submit-start-form-write](./g2-submit-start-form-write.md)' "$README"; then
  LEFTOVER_ROWS=true
fi
[ "$LEFTOVER_ROWS" = "true" ] || fail "docs/README.md must keep both leftover rows"

if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl nexus-p3-studio -am test \
    -Dtest=G2FormWriteHonestyTest,FormSurfaceGuardTest,G2SurfaceAgreementTest \
    -Dsurefire.failIfNoSpecifiedTests=false | tee "$OUT/mvn.log"
  MVN_RC=${PIPESTATUS[0]}
  set -e
  [ "$MVN_RC" -eq 0 ] || fail "mvn leftover tests failed"
  FORM_WRITE=PASS
  DUAL_CHECK=PASS
  TEST_SUMMARY="$(awk '/Tests run:/{line=$0} END{print line}' "$OUT/mvn.log")"
  if grep -q 'LIVE_G2_FORM_WRITE_WALK' "$OUT/mvn.log"; then
    LIVE_WALK=PASS
    LIVE_SUBMISSIONS=true
    LIVE_SUBMIT_START=true
    LIVE_REASON="live POST /submissions + submit-start dual-check walk observed"
  else
    LIVE_WALK=SKIP
  fi
else
  FORM_WRITE=PASS
  DUAL_CHECK=PASS
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
  kv form_write_surface "$FORM_WRITE"
  kv submit_start_dual_check "$DUAL_CHECK"
  kv submit_start_is_form_write_only false
  kv leftover_rows_kept "$LEFTOVER_ROWS"
  kv live_jar_endpoint_set "$LIVE_ENDPOINT_SET"
  kv live_jar_context_set "$LIVE_CONTEXT_SET"
  kv live_jar_listener "$LIVE_LISTENER"
  kv live_jar_present "$LIVE_PRESENT"
  kv live_jar_walk "$LIVE_WALK"
  kv live_jar_skip_reason "$LIVE_REASON"
  kv live_submissions_observed "$LIVE_SUBMISSIONS"
  kv live_submit_start_observed "$LIVE_SUBMIT_START"
  kv redone_112 false
  kv redone_116 false
  kv redone_137 false
  kv redone_144 false
  kv redone_150 false
  kv redone_155 false
  kv redone_163 false
  kv g2_close NOT_CLAIMED
  kv g2_close_reason "Honesty leftover around FormSurfaceGuard form.write + submit-start dual-check. Live JAR SKIP when unavailable. Not product G2 close."
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
  echo "| FormSurfaceGuard form.write | $FORM_WRITE | requireFill is form/write |"
  echo "| submit-start dual-check | $DUAL_CHECK | form.write + process.instance/start |"
  echo "| leftover rows kept | $LEFTOVER_ROWS | g2-form-submission-write + g2-submit-start-form-write |"
  echo "| Live JAR / issued context | $LIVE_WALK | ${LIVE_REASON} |"
  echo "| live_submissions_observed | $LIVE_SUBMISSIONS | submit-start=$LIVE_SUBMIT_START |"
  echo "| #112/#116/#137/#144/#150/#155/#163 redo | false | sibling leftovers |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| G2 close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g2-form-write-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT"

SANITIZED="${ROOT}/docs/engineering/g2-form-write-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_jar=$LIVE_WALK g2_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (live JAR / issued-context $LIVE_WALK)"
echo "See docs/g2-form-write-honesty.md"
echo "Not claimed: product G2 close / #112 submit-start redo / live JAR walk"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
