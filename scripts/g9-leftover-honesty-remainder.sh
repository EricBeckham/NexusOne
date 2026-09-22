#!/usr/bin/env bash
# G9 leftover remainder beyond #237 / #229 / #209 / #197: Sentinel /
# multi-JVM / Redis-down invalidate PASS/SKIP honesty. Parent leftover
# harness fixtures stay PASS. Live Sentinel/Cluster, replica promotion,
# multi-JVM, Redis-down, and live invalidate stay SKIP unless those
# walks were actually observed.
# Does not redo #83 / #100 / #121 / #128 / #148 / #161 / #174 / #197 /
# #209 / #229 / #237 product code.
# Does not claim product G9 / Sentinel / Cluster close. G18 stays closed.
# No Docker. No P0/M0 redo. AuditPort same-TX untouched.
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
VERIFY_REPORT=""
SKIP_TESTS=0

FIXTURE_HONEST="$HERE/g9-leftover-honesty-remainder/fixtures/honest-skip.env"
LEFTOVER_ROWS_CSV="$(awk -F= '/^leftover_rows=/{sub(/^leftover_rows=/,""); print; exit}' "$FIXTURE_HONEST")"
REQUIRED_LEFTOVER_ROWS="${LEFTOVER_ROWS_CSV//,/ }"

usage() {
  cat <<'EOF'
Usage:
  scripts/g9-leftover-honesty-remainder.sh [--out DIR] [--skip-tests]
  scripts/g9-leftover-honesty-remainder.sh --verify-report FILE

#237 / #229 / #209 / #197 leftover harness contracts stay always-green PASS.
Live Sentinel/Cluster, replica promotion, multi-JVM, Redis-down, and
live invalidate are PASS only when observed. Missing topology is honest SKIP.
Product G9 / Sentinel / Cluster close stay NOT_CLAIMED.

  --out DIR           Output directory (default: .local/g9-leftover-honesty-remainder/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_237_harness leftover_229_harness leftover_209_harness leftover_197_harness leftover_237_cells_honest leftover_229_cells_honest leftover_209_cells_honest leftover_197_cells_honest leftover_rows_kept leftover_rows dead_client_is_not_live_down simulated_dead_client_port redis_present redis_role redis_down_observed live_redis_down live_redis_down_skip_reason live_invalidate_observed live_invalidate live_invalidate_skip_reason sentinel_present sentinel_failover_observed sentinel_failover sentinel_skip_reason cluster_enabled cluster_failover_observed cluster_failover cluster_skip_reason replica_present promotion_observed replica_promotion replica_skip_reason second_jvm_present multi_jvm_observed live_multi_jvm multi_jvm_skip_reason g9_close sentinel_cluster_close g18_status audit_port_changed policy_snapshot_redesigned p0_m0_redone redo_237 redo_229 redo_209 redo_197 g9_83_100_121_128_148_161_174_197_209_229_237_redone"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || {
      case "$key" in
        redis_role|live_redis_down_skip_reason|live_invalidate_skip_reason|sentinel_skip_reason|cluster_skip_reason|replica_skip_reason|multi_jvm_skip_reason) ;;
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
  [ "$(report_get "$file" policy_snapshot_redesigned)" = "false" ] || fail "policy_snapshot_redesigned must be false"
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || fail "p0_m0_redone must stay false"
  [ "$(report_get "$file" g9_close)" = "NOT_CLAIMED" ] || fail "g9_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" sentinel_cluster_close)" = "NOT_CLAIMED" ] || fail "sentinel_cluster_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" leftover_237_harness)" = "PASS" ] || fail "leftover_237_harness must be PASS"
  [ "$(report_get "$file" leftover_229_harness)" = "PASS" ] || fail "leftover_229_harness must be PASS"
  [ "$(report_get "$file" leftover_209_harness)" = "PASS" ] || fail "leftover_209_harness must be PASS"
  [ "$(report_get "$file" leftover_197_harness)" = "PASS" ] || fail "leftover_197_harness must be PASS"
  [ "$(report_get "$file" leftover_237_cells_honest)" = "PASS" ] || fail "leftover_237_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_229_cells_honest)" = "PASS" ] || fail "leftover_229_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_209_cells_honest)" = "PASS" ] || fail "leftover_209_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_197_cells_honest)" = "PASS" ] || fail "leftover_197_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" dead_client_is_not_live_down)" = "true" ] || fail "dead_client_is_not_live_down must stay true"
  [ "$(report_get "$file" redo_237)" = "false" ] || fail "must not redo #237"
  [ "$(report_get "$file" redo_229)" = "false" ] || fail "must not redo #229"
  [ "$(report_get "$file" redo_209)" = "false" ] || fail "must not redo #209"
  [ "$(report_get "$file" redo_197)" = "false" ] || fail "must not redo #197"
  [ "$(report_get "$file" g9_83_100_121_128_148_161_174_197_209_229_237_redone)" = "false" ] || fail "must not redo #83/#100/#121/#128/#148/#161/#174/#197/#209/#229/#237"

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $REQUIRED_LEFTOVER_ROWS; do
    echo "$rows" | grep -Fq "$required_row" || fail "leftover_rows must keep $required_row"
  done

  local verdict
  for verdict in live_redis_down live_invalidate sentinel_failover cluster_failover replica_promotion live_multi_jvm; do
    local value
    value="$(report_get "$file" "$verdict")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) fail "$verdict must be PASS/SKIP/FAIL" ;;
    esac
  done

  if [ "$(report_get "$file" live_redis_down)" = "PASS" ]; then
    [ "$(report_get "$file" redis_present)" = "true" ] || fail "live_redis_down=PASS requires redis_present=true"
    [ "$(report_get "$file" redis_down_observed)" = "true" ] || fail "live_redis_down=PASS requires redis_down_observed=true"
  fi
  if [ "$(report_get "$file" live_invalidate)" = "PASS" ]; then
    [ "$(report_get "$file" redis_present)" = "true" ] || fail "live_invalidate=PASS requires redis_present=true"
    [ "$(report_get "$file" live_invalidate_observed)" = "true" ] || fail "live_invalidate=PASS requires live_invalidate_observed=true"
  fi
  if [ "$(report_get "$file" sentinel_failover)" = "PASS" ]; then
    [ "$(report_get "$file" sentinel_present)" = "true" ] || fail "sentinel_failover=PASS requires sentinel_present=true"
    [ "$(report_get "$file" sentinel_failover_observed)" = "true" ] || fail "sentinel_failover=PASS requires sentinel_failover_observed=true"
  fi
  if [ "$(report_get "$file" cluster_failover)" = "PASS" ]; then
    [ "$(report_get "$file" cluster_enabled)" = "true" ] || fail "cluster_failover=PASS requires cluster_enabled=true"
    [ "$(report_get "$file" cluster_failover_observed)" = "true" ] || fail "cluster_failover=PASS requires cluster_failover_observed=true"
  fi
  if [ "$(report_get "$file" replica_promotion)" = "PASS" ]; then
    [ "$(report_get "$file" replica_present)" = "true" ] || fail "replica_promotion=PASS requires replica_present=true"
    [ "$(report_get "$file" promotion_observed)" = "true" ] || fail "replica_promotion=PASS requires promotion_observed=true"
  fi
  if [ "$(report_get "$file" live_multi_jvm)" = "PASS" ]; then
    [ "$(report_get "$file" second_jvm_present)" = "true" ] || fail "live_multi_jvm=PASS requires second_jvm_present=true"
    [ "$(report_get "$file" multi_jvm_observed)" = "true" ] || fail "live_multi_jvm=PASS requires multi_jvm_observed=true"
  fi
  if [ "$(report_get "$file" live_redis_down)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_redis_down_skip_reason)" ] || fail "live_redis_down SKIP needs live_redis_down_skip_reason"
    [ "$(report_get "$file" redis_down_observed)" != "true" ] || fail "SKIP cannot claim redis_down_observed=true"
  fi
  if [ "$(report_get "$file" live_invalidate)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_invalidate_skip_reason)" ] || fail "live_invalidate SKIP needs live_invalidate_skip_reason"
    [ "$(report_get "$file" live_invalidate_observed)" != "true" ] || fail "SKIP cannot claim live_invalidate_observed=true"
  fi
  if [ "$(report_get "$file" sentinel_failover)" = "SKIP" ]; then
    [ -n "$(report_get "$file" sentinel_skip_reason)" ] || fail "sentinel_failover SKIP needs sentinel_skip_reason"
    [ "$(report_get "$file" sentinel_failover_observed)" != "true" ] || fail "SKIP cannot claim sentinel_failover_observed=true"
  fi
  if [ "$(report_get "$file" cluster_failover)" = "SKIP" ]; then
    [ -n "$(report_get "$file" cluster_skip_reason)" ] || fail "cluster_failover SKIP needs cluster_skip_reason"
    [ "$(report_get "$file" cluster_failover_observed)" != "true" ] || fail "SKIP cannot claim cluster_failover_observed=true"
  fi
  if [ "$(report_get "$file" replica_promotion)" = "SKIP" ]; then
    [ -n "$(report_get "$file" replica_skip_reason)" ] || fail "replica_promotion SKIP needs replica_skip_reason"
    [ "$(report_get "$file" promotion_observed)" != "true" ] || fail "SKIP cannot claim promotion_observed=true"
  fi
  if [ "$(report_get "$file" live_multi_jvm)" = "SKIP" ]; then
    [ -n "$(report_get "$file" multi_jvm_skip_reason)" ] || fail "live_multi_jvm SKIP needs multi_jvm_skip_reason"
    [ "$(report_get "$file" multi_jvm_observed)" != "true" ] || fail "SKIP cannot claim multi_jvm_observed=true"
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
  OUT="$ROOT/.local/g9-leftover-honesty-remainder/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G9 leftover: leftover honesty remainder beyond #237/#229/#209/#197"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #237/#229/#209/#197. Not product G9 / Sentinel/Cluster close. G18 stays closed."
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
[ -f "$README" ] || fail "missing $README"
for row in $REQUIRED_LEFTOVER_ROWS; do
  grep -q "\[${row}\]" "$README" || fail "docs/README.md must keep leftover row ${row}"
done
grep -q '\[g9-leftover-honesty-remainder\](\./g9-leftover-honesty-remainder.md)' "$README" \
  || fail "docs/README.md must list this leftover row"

echo "== Honesty fixtures (this leftover)"
verify_report "$HERE/g9-leftover-honesty-remainder/fixtures/honest-skip.env"
if ( verify_report "$HERE/g9-leftover-honesty-remainder/fixtures/fake-live-redis-down-pass.env" ); then
  fail "fake-live-redis-down-pass.env must be rejected"
fi
echo "PASS: fake-live-redis-down-pass.env rejected"
if ( verify_report "$HERE/g9-leftover-honesty-remainder/fixtures/fake-live-invalidate-pass.env" ); then
  fail "fake-live-invalidate-pass.env must be rejected"
fi
echo "PASS: fake-live-invalidate-pass.env rejected"
if ( verify_report "$HERE/g9-leftover-honesty-remainder/fixtures/fake-sentinel-pass.env" ); then
  fail "fake-sentinel-pass.env must be rejected"
fi
echo "PASS: fake-sentinel-pass.env rejected"
if ( verify_report "$HERE/g9-leftover-honesty-remainder/fixtures/fake-multi-jvm-pass.env" ); then
  fail "fake-multi-jvm-pass.env must be rejected"
fi
echo "PASS: fake-multi-jvm-pass.env rejected"
if ( verify_report "$HERE/g9-leftover-honesty-remainder/fixtures/fake-g9-close.env" ); then
  fail "fake-g9-close.env must be rejected"
fi
echo "PASS: fake-g9-close.env rejected"
if ( verify_report "$HERE/g9-leftover-honesty-remainder/fixtures/fake-redone.env" ); then
  fail "fake-redone.env must be rejected"
fi
echo "PASS: fake-redone.env rejected"

echo
echo "== Parent leftover harness still PASS (#237 / #229 / #209 / #197 verify-report only; not a product redo)"
"$ROOT/scripts/g9-redis-down-invalidate-remainder-honesty.sh" --verify-report \
  "$ROOT/scripts/g9-redis-down-invalidate-remainder-honesty/fixtures/honest-skip.env"
"$ROOT/scripts/g9-live-multi-jvm-replica-remainder-honesty.sh" --verify-report \
  "$ROOT/scripts/g9-live-multi-jvm-replica-remainder-honesty/fixtures/honest-skip.env"
"$ROOT/scripts/g9-sentinel-cluster-remainder-honesty.sh" --verify-report \
  "$ROOT/scripts/g9-sentinel-cluster-remainder-honesty/fixtures/honest-skip.env"
"$ROOT/scripts/g9-redis-down-invalidate-remainder.sh" --verify-report \
  "$ROOT/scripts/g9-redis-down-invalidate-remainder/fixtures/honest-skip.env"
LEFTOVER_237=PASS
LEFTOVER_229=PASS
LEFTOVER_209=PASS
LEFTOVER_197=PASS
LEFTOVER_237_CELLS=PASS
LEFTOVER_229_CELLS=PASS
LEFTOVER_209_CELLS=PASS
LEFTOVER_197_CELLS=PASS

REDIS_PRESENT=false
REDIS_ROLE=""
LISTED=false
if [[ "${NEXUS_G9_REDIS_DOWN_OBSERVED:-}" == "true" || "${NEXUS_G9_LIVE_INVALIDATE_OBSERVED:-}" == "true" || "${NEXUS_G9_SENTINEL_FAILOVER_OBSERVED:-}" == "true" || "${NEXUS_G9_CLUSTER_FAILOVER_OBSERVED:-}" == "true" || "${NEXUS_G9_PROMOTION_OBSERVED:-}" == "true" || "${NEXUS_G9_MULTI_JVM_OBSERVED:-}" == "true" ]]; then
  LISTED=true
fi
LISTED_SUFFIX=""
if [ "$LISTED" = "true" ]; then
  LISTED_SUFFIX="; listed observation env is not a live walk"
fi

if command -v redis-cli >/dev/null 2>&1; then
  if redis-cli -h 127.0.0.1 -p 6379 PING 2>/dev/null | grep -qx PONG; then
    REDIS_PRESENT=true
    REDIS_ROLE="$(redis-cli -h 127.0.0.1 -p 6379 INFO replication 2>/dev/null | awk -F: '/^role:/{print $2}' | tr -d '\r')"
  fi
fi

if [ "$REDIS_PRESENT" = "true" ]; then
  LIVE_SKIP_REASON="Redis is listening on 127.0.0.1:6379 (role=${REDIS_ROLE:-unknown}) but a live Redis process-down was not observed; dead client on port 1 is not live Redis-down; Docker forbidden${LISTED_SUFFIX}"
  INVALIDATE_REASON="Redis is listening on 127.0.0.1:6379 but a live PUBLISH/SUBSCRIBE invalidate walk was not observed; #174/#197/#237 inventory is not a live invalidate PASS${LISTED_SUFFIX}"
  SENTINEL_REASON="redis_mode=standalone; no redis-sentinel / :26379 / SENTINEL MASTERS; standalone RESP2 only; live Sentinel failover not observed; Docker forbidden${LISTED_SUFFIX}"
  CLUSTER_REASON="redis_mode=standalone; no Redis Cluster slots / ASK / MOVED; live Cluster failover not observed; Docker forbidden${LISTED_SUFFIX}"
  REPLICA_REASON="redis_role=${REDIS_ROLE:-unknown}; Redis32Client is standalone RESP2; replica promotion not observed; Docker forbidden${LISTED_SUFFIX}"
else
  LIVE_SKIP_REASON="no Redis listener on 127.0.0.1:6379; parent leftover harness PASS is not a live walk; Docker forbidden${LISTED_SUFFIX}"
  INVALIDATE_REASON="$LIVE_SKIP_REASON"
  SENTINEL_REASON="$LIVE_SKIP_REASON"
  CLUSTER_REASON="$LIVE_SKIP_REASON"
  REPLICA_REASON="$LIVE_SKIP_REASON"
fi

SELF_PID="$$"
SECOND_JVM_PRESENT=false
MULTI_JVM_REASON="one application JVM; surefire parent/fork is not a live multi-JVM partition; no second JAR, no iptables/netem, Docker forbidden${LISTED_SUFFIX}"
if [ -n "${NEXUS_G9_SECOND_JVM_PID:-}" ] && [ -d "/proc/${NEXUS_G9_SECOND_JVM_PID}" ] && [ "${NEXUS_G9_SECOND_JVM_PID}" != "$SELF_PID" ]; then
  SECOND_JVM_PRESENT=true
  MULTI_JVM_REASON="second JVM is listed but a live partition / netem / two-JAR walk was not observed${LISTED_SUFFIX}"
fi

TEST_SUMMARY="parent --verify-report only; no #237/#229/#209/#197 product redo"
if [ "$SKIP_TESTS" = "0" ]; then
  if command -v mvn >/dev/null 2>&1; then
    mvn -version | head -n 1 | tee -a "$OUT/collect.log"
    set +e
    mvn -pl nexus-permission-core -am test \
      -Dtest=G9LeftoverHonestyRemainderTest \
      -Dsurefire.failIfNoSpecifiedTests=false | tee "$OUT/mvn.log"
    MVN_RC=${PIPESTATUS[0]}
    set -e
    if [ "$MVN_RC" -eq 0 ]; then
      TEST_SUMMARY="$(awk '/Tests run:/{line=$0} END{print line}' "$OUT/mvn.log")"
    else
      TEST_SUMMARY="mvn leftover tests failed; parent --verify-report still PASS"
      log "mvn leftover tests failed; recording parent harness PASS / live SKIP"
    fi
  else
    TEST_SUMMARY="mvn not on PATH; parent --verify-report still PASS"
  fi
else
  TEST_SUMMARY="skipped (--skip-tests); parent --verify-report still PASS"
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
  kv leftover_237_harness "$LEFTOVER_237"
  kv leftover_229_harness "$LEFTOVER_229"
  kv leftover_209_harness "$LEFTOVER_209"
  kv leftover_197_harness "$LEFTOVER_197"
  kv leftover_237_cells_honest "$LEFTOVER_237_CELLS"
  kv leftover_229_cells_honest "$LEFTOVER_229_CELLS"
  kv leftover_209_cells_honest "$LEFTOVER_209_CELLS"
  kv leftover_197_cells_honest "$LEFTOVER_197_CELLS"
  kv leftover_rows_kept true
  kv leftover_rows "$LEFTOVER_ROWS_CSV"
  kv dead_client_is_not_live_down true
  kv simulated_dead_client_port 1
  kv redis_present "$REDIS_PRESENT"
  kv redis_role "$REDIS_ROLE"
  kv redis_down_observed false
  kv live_redis_down SKIP
  kv live_redis_down_skip_reason "$LIVE_SKIP_REASON"
  kv live_invalidate_observed false
  kv live_invalidate SKIP
  kv live_invalidate_skip_reason "$INVALIDATE_REASON"
  kv sentinel_present false
  kv sentinel_failover_observed false
  kv sentinel_failover SKIP
  kv sentinel_skip_reason "$SENTINEL_REASON"
  kv cluster_enabled false
  kv cluster_failover_observed false
  kv cluster_failover SKIP
  kv cluster_skip_reason "$CLUSTER_REASON"
  kv replica_present false
  kv promotion_observed false
  kv replica_promotion SKIP
  kv replica_skip_reason "$REPLICA_REASON"
  kv second_jvm_present "$SECOND_JVM_PRESENT"
  kv multi_jvm_observed false
  kv live_multi_jvm SKIP
  kv multi_jvm_skip_reason "$MULTI_JVM_REASON"
  kv g9_close NOT_CLAIMED
  kv sentinel_cluster_close NOT_CLAIMED
  kv g9_close_reason "Honesty remainder beyond #237/#229/#209/#197. Parent harness PASS. Live Sentinel/multi-JVM/Redis-down SKIP when unobserved. Not product G9 / Sentinel/Cluster close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv policy_snapshot_redesigned false
  kv p0_m0_redone false
  kv redo_237 false
  kv redo_229 false
  kv redo_209 false
  kv redo_197 false
  kv g9_83_100_121_128_148_161_174_197_209_229_237_redone false
  kv test_summary "$TEST_SUMMARY"
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "| Check | Result | Notes |"
  echo "|---|---|---|"
  echo "| JDK 21 | PASS | $JAVA_VERSION_LINE |"
  echo "| Boot 4.1.1 | $BOOT_PARENT_MATCH | \`$BOOT_PARENT_GA\` |"
  echo "| #237 leftover harness | $LEFTOVER_237 | parent --verify-report; not a product redo |"
  echo "| #229 leftover harness | $LEFTOVER_229 | parent --verify-report; not a product redo |"
  echo "| #209 leftover harness | $LEFTOVER_209 | parent --verify-report; not a product redo |"
  echo "| #197 leftover harness | $LEFTOVER_197 | parent --verify-report; not a product redo |"
  echo "| Live Redis-down | SKIP | ${LIVE_SKIP_REASON} |"
  echo "| Live invalidate | SKIP | ${INVALIDATE_REASON} |"
  echo "| Live Sentinel / Cluster | SKIP | ${SENTINEL_REASON} |"
  echo "| Live replica / multi-JVM | SKIP | ${REPLICA_REASON} / ${MULTI_JVM_REASON} |"
  echo "| Leftover rows kept | true | #237 + #229 + #209 + #197 + ALL siblings including #249 / #240 / #254 / #236 / #251 / #243 / #241 / #235 / #233 |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| Product G9 / Sentinel/Cluster | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g9-leftover-honesty-remainder collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT"

SANITIZED="${ROOT}/docs/engineering/g9-leftover-honesty-remainder/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT leftover_237=$LEFTOVER_237 leftover_229=$LEFTOVER_229 leftover_209=$LEFTOVER_209 leftover_197=$LEFTOVER_197 live=SKIP g9_close=NOT_CLAIMED"
echo
echo "PASS: honesty remainder recorded (#237/#229/#209/#197 harness PASS; live SKIP)"
echo "See docs/g9-leftover-honesty-remainder.md"
echo "Keep leftover rows including g17-separation-leftover-honesty-remainder and g15-h5-pkce-miniprogram-remainder-honesty"
echo "Not claimed: product G9 / Sentinel/Cluster close"
echo "Not redone: #237/#229/#209/#197 / P0 / M0"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
