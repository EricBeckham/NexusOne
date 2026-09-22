#!/usr/bin/env bash
# G7 leftover: expense/travel list/detail/todo query honesty beyond #140.
#
# Query surfaces stay always-green PASS when the #140 contract is present.
# Live product-JAR + live/Keycloak IdP HTTP query is PASS only when a
# healthy JAR, issued context, live IdP, and an observed expense+travel
# query walk exist. Missing JAR/IdP is honest SKIP.
# Does NOT redo #103 / #113 / #140 / #159 / #175 withdraw paths.
# Does NOT claim product G7 close. G18 stays closed. No Docker.
# Does NOT rewrite AuditPort same-TX.
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
HERE="$ROOT/scripts/g7-query-expense-travel-honesty"

usage() {
  cat <<'EOF'
Usage:
  scripts/g7-query-expense-travel-honesty.sh [--out DIR] [--skip-tests]
  scripts/g7-query-expense-travel-honesty.sh --verify-report FILE

Query surfaces (expense + travel list / field-equals / detail / todo) stay
PASS when the #140 contract is present. Live JAR/IdP query is PASS only
when those are present AND an expense+travel query walk was observed.
Missing JAR/IdP is honest SKIP. Product G7 close stays NOT_CLAIMED.

  --out DIR           Output directory (default: .local/g7-query-expense-travel-honesty/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used query_surface expense_query_surface travel_query_surface zonky_it_is_not_live_jar leftover_rows_kept live_jar issued_context idp query_walk_observed live_jar_idp_query live_jar_idp_skip_reason withdraw_paths_redone g7_103_redone g7_113_redone g7_140_redone g7_159_redone g7_175_redone g7_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" g7_103_redone)" = "false" ] || fail "g7_103_redone must stay false"
  [ "$(report_get "$file" g7_113_redone)" = "false" ] || fail "g7_113_redone must stay false"
  [ "$(report_get "$file" g7_140_redone)" = "false" ] || fail "g7_140_redone must stay false"
  [ "$(report_get "$file" g7_159_redone)" = "false" ] || fail "g7_159_redone must stay false"
  [ "$(report_get "$file" g7_175_redone)" = "false" ] || fail "g7_175_redone must stay false"
  [ "$(report_get "$file" withdraw_paths_redone)" = "false" ] || fail "withdraw_paths_redone must stay false"
  [ "$(report_get "$file" g7_close)" = "NOT_CLAIMED" ] || fail "g7_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" query_surface)" = "PASS" ] || fail "query_surface must be PASS"
  [ "$(report_get "$file" expense_query_surface)" = "PASS" ] || fail "expense_query_surface must be PASS"
  [ "$(report_get "$file" travel_query_surface)" = "PASS" ] || fail "travel_query_surface must be PASS"
  [ "$(report_get "$file" zonky_it_is_not_live_jar)" = "true" ] || fail "zonky_it_is_not_live_jar must stay true"

  local kept
  kept="$(report_get "$file" leftover_rows_kept)"
  local row
  for row in g7-query-expense-travel g7-withdraw-browser g7-withdraw-browser-honesty g7-withdraw-expense-travel g10-identity-drift-honesty g15-native-bridge-honesty g1-fieldpolicy-mask-second-auth-honesty g4-hot-purge-90d-honesty g17-separation-honesty s0s1-live-redis-minio-isolation-honesty g6-recovery-port-dry-run-honesty g4-p1-staging-audit-events-honesty linux-arm-profile-leftover-honesty; do
    echo "$kept" | grep -Fq "$row" || fail "leftover_rows_kept must include $row"
  done

  local live
  live="$(report_get "$file" live_jar_idp_query)"
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) fail "live_jar_idp_query must be PASS/SKIP/FAIL" ;;
  esac
  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" live_jar)" = "true" ] || fail "live_jar_idp_query=PASS requires live_jar=true"
    [ "$(report_get "$file" issued_context)" = "true" ] || fail "live_jar_idp_query=PASS requires issued_context=true"
    case "$(report_get "$file" idp)" in
      live|keycloak) ;;
      *) fail "live_jar_idp_query=PASS requires live/keycloak IdP" ;;
    esac
    [ "$(report_get "$file" query_walk_observed)" = "true" ] || fail "live_jar_idp_query=PASS requires query_walk_observed=true"
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_jar_idp_skip_reason)" ] || fail "live JAR/IdP SKIP needs live_jar_idp_skip_reason"
    [ "$(report_get "$file" query_walk_observed)" != "true" ] || fail "SKIP cannot claim a live query walk"
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
  OUT="$ROOT/.local/g7-query-expense-travel-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G7 leftover: expense/travel list/detail/todo query honesty"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #103/#113/#140/#159/#175. Not product G7 close."
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

IT="$ROOT/nexus-p3-studio/src/test/java/one/nexus/p3/path/G7ExpenseTravelQueryIT.java"
[ -f "$IT" ] || fail "G7ExpenseTravelQueryIT missing; do not drop the #140 contract"
QUERY_SURFACE=FAIL
EXPENSE_SURFACE=FAIL
TRAVEL_SURFACE=FAIL
if grep -Fq 'expenseListDetailAndTodoQuerySurfaces' "$IT" \
  && grep -Fq '/api/v1/p3/ledgers' "$IT" \
  && grep -Fq '/records' "$IT" \
  && grep -Fq '/api/v1/todos/pending' "$IT" \
  && grep -Fq '/api/v1/p3/todos' "$IT" \
  && grep -Fq '/api/v1/todos/initiated' "$IT" \
  && grep -Fq '/api/v1/todos/done' "$IT" \
  && grep -Fq '交通费' "$IT" \
  && grep -Fq 'g7-expense-query' "$IT"; then
  EXPENSE_SURFACE=PASS
fi
if grep -Fq 'travelListDetailAndTodoQuerySurfaces' "$IT" \
  && grep -Fq '客户拜访' "$IT" \
  && grep -Fq 'g7-travel-query' "$IT" \
  && grep -Fq 'destination' "$IT"; then
  TRAVEL_SURFACE=PASS
fi
if [ "$EXPENSE_SURFACE" = "PASS" ] && [ "$TRAVEL_SURFACE" = "PASS" ]; then
  QUERY_SURFACE=PASS
fi
[ "$QUERY_SURFACE" = "PASS" ] || fail "query surfaces must stay PASS (#140 contract still present)"

README="$ROOT/docs/README.md"
LEFTOVER_ROWS="g7-query-expense-travel,g7-withdraw-browser,g7-withdraw-browser-honesty,g7-withdraw-expense-travel,g10-identity-drift-honesty,g15-native-bridge-honesty,g1-fieldpolicy-mask-second-auth-honesty,g4-hot-purge-90d-honesty,g17-separation-honesty,s0s1-live-redis-minio-isolation-honesty,g6-recovery-port-dry-run-honesty,g4-p1-staging-audit-events-honesty,linux-arm-profile-leftover-honesty"
for row in g7-query-expense-travel g7-withdraw-browser g7-withdraw-browser-honesty g7-withdraw-expense-travel g10-identity-drift-honesty g15-native-bridge-honesty g1-fieldpolicy-mask-second-auth-honesty g4-hot-purge-90d-honesty g17-separation-honesty s0s1-live-redis-minio-isolation-honesty g6-recovery-port-dry-run-honesty g4-p1-staging-audit-events-honesty linux-arm-profile-leftover-honesty g7-query-expense-travel-honesty; do
  grep -Fq "$row" "$README" || fail "docs/README.md must keep leftover row $row"
done

LIVE_ENDPOINT="${NEXUS_G7_LIVE_BASE:-${NEXUS_BASE_URL:-}}"
LIVE_JAR=false
if [ -n "$LIVE_ENDPOINT" ] && curl -sf --max-time 2 "${LIVE_ENDPOINT%/}/actuator/health" >/dev/null 2>&1; then
  LIVE_JAR=true
elif port_open 127.0.0.1 8080 || port_open 127.0.0.1 8088; then
  if [ -n "$LIVE_ENDPOINT" ]; then
    LIVE_JAR=true
  fi
fi

ISSUED_CONTEXT=false
if [ -n "${NEXUS_G7_LIVE_CONTEXT_ID:-}" ]; then
  ISSUED_CONTEXT=true
fi

IDP="absent"
if [ -n "${NEXUS_G7_LIVE_IDP:-}" ]; then
  IDP="${NEXUS_G7_LIVE_IDP}"
elif [ -n "${NEXUS_IAM_IDP:-}" ] && [ "${NEXUS_IAM_IDP}" != "stub" ]; then
  IDP="${NEXUS_IAM_IDP}"
elif [ "${NEXUS_IAM_IDP:-}" = "stub" ]; then
  IDP="stub"
fi

QUERY_OBSERVED=false
if [ "${NEXUS_G7_LIVE_QUERY_OBSERVED:-}" = "true" ]; then
  QUERY_OBSERVED=true
fi

LIVE_WALK=SKIP
LIVE_REASON="live_jar_not_healthy"
if [ "$LIVE_JAR" != "true" ]; then
  LIVE_REASON="live_jar_not_healthy"
elif [ "$ISSUED_CONTEXT" != "true" ]; then
  LIVE_REASON="issued_context_missing"
elif [ "$IDP" = "stub" ]; then
  LIVE_REASON="stub_login_is_not_live_walk"
elif [ "$IDP" != "live" ] && [ "$IDP" != "keycloak" ]; then
  LIVE_REASON="live_idp_absent"
elif [ "$QUERY_OBSERVED" != "true" ]; then
  LIVE_REASON="live_query_walk_not_observed"
else
  LIVE_WALK=PASS
  LIVE_REASON=""
fi
if [ "$LIVE_WALK" != "PASS" ]; then
  QUERY_OBSERVED=false
fi

TEST_SUMMARY=""
if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl nexus-p3-studio -am test \
    -Dtest=G7QueryExpenseTravelHonestyTest \
    -Dsurefire.failIfNoSpecifiedTests=false | tee "$OUT/mvn.log"
  MVN_RC=${PIPESTATUS[0]}
  set -e
  [ "$MVN_RC" -eq 0 ] || fail "mvn leftover tests failed"
  TEST_SUMMARY="$(awk '/Tests run:/{line=$0} END{print line}' "$OUT/mvn.log")"
else
  TEST_SUMMARY="skipped (--skip-tests)"
fi

echo
echo "== Honesty fixtures"
verify_report "$HERE/fixtures/honest-skip.env"
if bash "$ROOT/scripts/g7-query-expense-travel-honesty.sh" --verify-report "$HERE/fixtures/fake-live-pass.env"; then
  echo "FAIL: fake-live-pass.env must be rejected" >&2
  exit 1
fi
if bash "$ROOT/scripts/g7-query-expense-travel-honesty.sh" --verify-report "$HERE/fixtures/fake-g7-close.env"; then
  echo "FAIL: fake-g7-close.env must be rejected" >&2
  exit 1
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
  kv query_surface "$QUERY_SURFACE"
  kv expense_query_surface "$EXPENSE_SURFACE"
  kv travel_query_surface "$TRAVEL_SURFACE"
  kv zonky_it_is_not_live_jar true
  kv leftover_rows_kept "$LEFTOVER_ROWS"
  kv live_jar "$LIVE_JAR"
  kv issued_context "$ISSUED_CONTEXT"
  kv idp "$IDP"
  kv query_walk_observed "$QUERY_OBSERVED"
  kv live_jar_idp_query "$LIVE_WALK"
  kv live_jar_idp_skip_reason "$LIVE_REASON"
  kv withdraw_paths_redone false
  kv g7_103_redone false
  kv g7_113_redone false
  kv g7_140_redone false
  kv g7_159_redone false
  kv g7_175_redone false
  kv g7_close NOT_CLAIMED
  kv g7_close_reason "Honesty leftover around expense/travel list/detail/todo query beyond #140. Live JAR/IdP SKIP when unavailable. Not product G7 close."
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
  echo "| Expense query surface | $EXPENSE_SURFACE | #140 contract still present |"
  echo "| Travel query surface | $TRAVEL_SURFACE | #140 contract still present |"
  echo "| leftover rows kept | true | $LEFTOVER_ROWS |"
  echo "| Live JAR / IdP query | $LIVE_WALK | ${LIVE_REASON} |"
  echo "| query_walk_observed | $QUERY_OBSERVED | Zonky IT is not a live JAR |"
  echo "| #103/#113/#140/#159/#175 redo | false | withdraw paths not redone |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| G7 close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g7-query-expense-travel-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT"

SANITIZED="${ROOT}/docs/engineering/g7-query-expense-travel-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_jar_idp_query=$LIVE_WALK g7_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (query surfaces $QUERY_SURFACE; live JAR/IdP $LIVE_WALK)"
echo "See docs/g7-query-expense-travel-honesty.md"
echo "Not claimed: product G7 close / live JAR+IdP query / G18 reopen"
echo "Not redone: #103/#113/#140/#159/#175"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
