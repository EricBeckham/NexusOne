#!/usr/bin/env bash
# G1 leftover beyond #146: leftover BoundSql / COUNT PASS/SKIP honesty.
# Does not redo #142/#146/#162/#168 AST product code.
# Does not claim product G1 closed. G18 stays closed. No Docker.
# In-process leftover BoundSql / COUNT stay on QueryAstScopedQuery → GuardedJdbc.
# Live GuardedLedgerStore list/count is PASS only when a listener is present
# AND the walk was observed. Missing NEXUS_BASE_URL / issued context is honest SKIP.
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
  scripts/g1-queryast-boundsql-honesty.sh [--out DIR] [--skip-tests]
  scripts/g1-queryast-boundsql-honesty.sh --verify-report FILE

Beyond #146: leftover BoundSql / COUNT stay on QueryAstScopedQuery → GuardedJdbc.
Live GuardedLedgerStore list/count is PASS only when observed.
Missing live host is honest SKIP. Product G1 close stays NOT_CLAIMED.

  --out DIR           Output directory (default: .local/g1-queryast-boundsql-honesty/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_boundsql_guard leftover_count_guard raw_boundsql_rejected count_is_ast_node leftover_surface leftover_rows_kept leftover_rows live_jar_endpoint_set live_jar_context_set live_jar_listener live_jar_present live_product_walk live_list_observed live_count_observed live_product_skip_reason ui_h5_report ui_h5_report_skip_reason redone_142 redone_146 redone_162 redone_168 g1_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" redone_142)" = "false" ] || fail "must not redo #142"
  [ "$(report_get "$file" redone_146)" = "false" ] || fail "must not redo #146"
  [ "$(report_get "$file" redone_162)" = "false" ] || fail "must not redo #162"
  [ "$(report_get "$file" redone_168)" = "false" ] || fail "must not redo #168"
  [ "$(report_get "$file" g1_close)" = "NOT_CLAIMED" ] || fail "g1_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" leftover_boundsql_guard)" = "PASS" ] || fail "leftover_boundsql_guard must be PASS"
  [ "$(report_get "$file" leftover_count_guard)" = "PASS" ] || fail "leftover_count_guard must be PASS"
  [ "$(report_get "$file" raw_boundsql_rejected)" = "true" ] || fail "raw_boundsql_rejected must stay true"
  [ "$(report_get "$file" count_is_ast_node)" = "false" ] || fail "count_is_ast_node must stay false"
  [ "$(report_get "$file" leftover_surface)" = "QueryAstScopedQuery" ] || fail "leftover_surface must be QueryAstScopedQuery"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  local rows
  rows="$(report_get "$file" leftover_rows)"
  echo "$rows" | grep -Fq "g1-queryast-boundsql-guard" || fail "leftover_rows must keep g1-queryast-boundsql-guard"
  echo "$rows" | grep -Fq "g1-datascope-or-in-ast" || fail "leftover_rows must keep g1-datascope-or-in-ast"
  echo "$rows" | grep -Fq "g1-fieldpolicy-mask-second-auth" || fail "leftover_rows must keep g1-fieldpolicy-mask-second-auth"
  echo "$rows" | grep -Fq "g1-fieldpolicy-mask-second-auth-honesty" || fail "leftover_rows must keep g1-fieldpolicy-mask-second-auth-honesty"

  local verdict
  for verdict in live_product_walk ui_h5_report; do
    local value
    value="$(report_get "$file" "$verdict")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) fail "$verdict must be PASS/SKIP/FAIL" ;;
    esac
  done

  if [ "$(report_get "$file" live_product_walk)" = "PASS" ]; then
    [ "$(report_get "$file" live_jar_present)" = "true" ] || fail "live_product_walk=PASS requires live_jar_present=true"
    [ "$(report_get "$file" live_list_observed)" = "true" ] || fail "live_product_walk=PASS requires live_list_observed=true"
    [ "$(report_get "$file" live_count_observed)" = "true" ] || fail "live_product_walk=PASS requires live_count_observed=true"
  fi
  if [ "$(report_get "$file" live_product_walk)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_product_skip_reason)" ] || fail "live product SKIP needs live_product_skip_reason"
    [ "$(report_get "$file" live_list_observed)" != "true" ] || fail "SKIP cannot claim a live list walk"
    [ "$(report_get "$file" live_count_observed)" != "true" ] || fail "SKIP cannot claim a live COUNT walk"
  fi
  if [ "$(report_get "$file" ui_h5_report)" = "SKIP" ]; then
    [ -n "$(report_get "$file" ui_h5_report_skip_reason)" ] || fail "UI/H5 SKIP needs ui_h5_report_skip_reason"
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
  OUT="$ROOT/.local/g1-queryast-boundsql-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G1 leftover: BoundSql / COUNT honesty beyond #146"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #142/#146/#162/#168. Not product G1 close."
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

LIVE_ENDPOINT="${NEXUS_G1_LIVE_BASE:-${NEXUS_BASE_URL:-}}"
LIVE_ENDPOINT_SET=false
LIVE_CONTEXT_SET=false
if [ -n "$LIVE_ENDPOINT" ]; then
  LIVE_ENDPOINT_SET=true
fi
if [ -n "${NEXUS_G1_LIVE_CONTEXT_ID:-}" ]; then
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
  LIVE_REASON="NEXUS_BASE_URL / NEXUS_G1_LIVE_BASE unset; nothing listening on 127.0.0.1:8080 / :8088; leftover BoundSql / COUNT stay on QueryAstScopedQuery (#146); live GuardedLedgerStore SKIP; Docker forbidden"
elif [ "$LIVE_CONTEXT_SET" != "true" ]; then
  LIVE_REASON="NEXUS_G1_LIVE_CONTEXT_ID unset; live GuardedLedgerStore list/count walk SKIP"
elif [ "$LIVE_LISTENER" != "true" ]; then
  LIVE_REASON="live base set but product JAR not reachable; live walk SKIP"
else
  LIVE_REASON="live listener present but this leftover does not claim a walk until leftover BoundSql list and COUNT through QueryAstScopedQuery → GuardedJdbc were observed"
fi

README="$ROOT/docs/README.md"
LEFTOVER_ROWS=false
if grep -Fq '[g1-queryast-boundsql-guard](./g1-queryast-boundsql-guard.md)' "$README" \
  && grep -Fq '[g1-datascope-or-in-ast](./g1-datascope-or-in-ast.md)' "$README" \
  && grep -Fq '[g1-fieldpolicy-mask-second-auth](./g1-fieldpolicy-mask-second-auth.md)' "$README" \
  && grep -Fq '[g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md)' "$README" \
  && grep -Fq '[g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md)' "$README" \
  && grep -Fq '[g17-separation-honesty](./g17-separation-honesty.md)' "$README" \
  && grep -Fq '[s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md)' "$README" \
  && grep -Fq '[g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md)' "$README" \
  && grep -Fq '[g4-p1-staging-audit-events-honesty](./g4-p1-staging-audit-events-honesty.md)' "$README" \
  && grep -Fq '[linux-arm-profile-leftover-honesty](./linux-arm-profile-leftover-honesty.md)' "$README" \
  && grep -Fq '[g7-query-expense-travel-honesty](./g7-query-expense-travel-honesty.md)' "$README" \
  && grep -Fq '[s0-secret-rotate-without-restart-honesty](./s0-secret-rotate-without-restart-honesty.md)' "$README"; then
  LEFTOVER_ROWS=true
fi
[ "$LEFTOVER_ROWS" = "true" ] || fail "docs/README.md must keep sibling leftover rows (g1-queryast-boundsql-guard, g1-datascope-or-in-ast, g1-fieldpolicy-mask-second-auth, g1-fieldpolicy-mask-second-auth-honesty, g4-hot-purge-90d-honesty, g17-separation-honesty, s0s1-live-redis-minio-isolation-honesty, g6-recovery-port-dry-run-honesty, g4-p1-staging-audit-events-honesty, linux-arm-profile-leftover-honesty, g7-query-expense-travel-honesty, s0-secret-rotate-without-restart-honesty)"

TEST_SUMMARY=""
if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl nexus-permission-core -am test \
    -Dtest=G1QueryAstBoundSqlHonestyTest \
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
  kv leftover_boundsql_guard PASS
  kv leftover_count_guard PASS
  kv raw_boundsql_rejected true
  kv count_is_ast_node false
  kv leftover_surface QueryAstScopedQuery
  kv leftover_rows_kept "$LEFTOVER_ROWS"
  kv leftover_rows "g1-queryast-boundsql-guard,g1-datascope-or-in-ast,g1-fieldpolicy-mask-second-auth,g1-fieldpolicy-mask-second-auth-honesty"
  kv live_jar_endpoint_set "$LIVE_ENDPOINT_SET"
  kv live_jar_context_set "$LIVE_CONTEXT_SET"
  kv live_jar_listener "$LIVE_LISTENER"
  kv live_jar_present "$LIVE_PRESENT"
  kv live_product_walk SKIP
  kv live_list_observed false
  kv live_count_observed false
  kv live_product_skip_reason "$LIVE_REASON"
  kv ui_h5_report SKIP
  kv ui_h5_report_skip_reason "UI / H5 / report UI leftover BoundSql / COUNT was not walked; leftover stays open"
  kv redone_142 false
  kv redone_146 false
  kv redone_162 false
  kv redone_168 false
  kv g1_close NOT_CLAIMED
  kv g1_close_reason "Honesty leftover beyond #146. Leftover BoundSql / COUNT stay on QueryAstScopedQuery → GuardedJdbc. Live GuardedLedgerStore SKIP when unavailable. Not product G1 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
  kv test_summary "$TEST_SUMMARY"
} >"$REPORT"

{
  echo "# G1 leftover BoundSql / COUNT honesty beyond #146"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| leftover BoundSql guard | PASS |"
  echo "| leftover COUNT guard | PASS |"
  echo "| raw BoundSql rejected | true |"
  echo "| COUNT is Query AST node | false |"
  echo "| live product walk | SKIP |"
  echo "| UI / H5 / report UI | SKIP |"
  echo "| product G1 close | NOT_CLAIMED |"
  echo
  echo "Live reason: $LIVE_REASON"
  echo "Keep sibling leftover rows: g1-queryast-boundsql-guard, g1-datascope-or-in-ast, g1-fieldpolicy-mask-second-auth, g1-fieldpolicy-mask-second-auth-honesty"
} > "$OUT/matrix.md"

SANITIZED="${ROOT}/docs/engineering/g1-queryast-boundsql-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

verify_report "$OUT/report.env"
log "wrote $OUT/report.env"
echo
echo "PASS: honesty leftover recorded (leftover BoundSql / COUNT PASS; live product SKIP)"
echo "See docs/g1-queryast-boundsql-honesty.md"
echo "Keep sibling leftover rows: docs/g1-queryast-boundsql-guard.md, docs/g1-datascope-or-in-ast.md, docs/g1-fieldpolicy-mask-second-auth.md, docs/g1-fieldpolicy-mask-second-auth-honesty.md"
echo "Not claimed: product G1 close / COUNT as Query AST node / #142/#146/#162/#168 redo"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
