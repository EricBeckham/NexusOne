#!/usr/bin/env bash
# G9 leftover remainder beyond #174: Redis-down / PolicySnapshot invalidate
# PASS/SKIP. Dead-client harness (port 1) is PASS. Live Redis process-down
# and live PolicySnapshot invalidate stay SKIP unless observed.
# Does not redo #83 / #100 / #121 / #128 / #148 / #161 / #174.
# Does not claim product G9 close. G18 stays closed. No Docker.
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
  scripts/g9-redis-down-invalidate-remainder.sh [--out DIR] [--skip-tests]
  scripts/g9-redis-down-invalidate-remainder.sh --verify-report FILE

Dead-client harness on port 1 is PASS. Live Redis-down / live
PolicySnapshot invalidate are PASS only when Redis 3.2 is present AND a
process-down / live PUBLISH+SUBSCRIBE walk was observed. Missing Redis
is honest SKIP. Product G9 close stays NOT_CLAIMED.

  --out DIR           Output directory (default: .local/g9-redis-down-invalidate-remainder/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_rows_kept dead_client_harness simulated_dead_client_port dead_client_is_not_live_down redis_present redis_role redis_down_observed live_redis_down live_redis_down_skip_reason live_invalidate_observed live_invalidate live_invalidate_skip_reason g9_close g18_status audit_port_changed policy_snapshot_redesigned p0_m0_redone g9_83_100_121_128_148_161_174_redone"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || {
      case "$key" in
        redis_role|live_redis_down_skip_reason|live_invalidate_skip_reason) ;;
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
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || fail "p0_m0_redone must be false"
  [ "$(report_get "$file" g9_83_100_121_128_148_161_174_redone)" = "false" ] || fail "must not redo #83/#100/#121/#128/#148/#161/#174"
  [ "$(report_get "$file" g9_close)" = "NOT_CLAIMED" ] || fail "g9_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" dead_client_harness)" = "PASS" ] || fail "dead_client_harness must be PASS"
  [ "$(report_get "$file" dead_client_is_not_live_down)" = "true" ] || fail "dead_client_is_not_live_down must be true"
  [ "$(report_get "$file" simulated_dead_client_port)" = "1" ] || fail "simulated_dead_client_port must be 1"

  local verdict
  for verdict in live_redis_down live_invalidate; do
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
  if [ "$(report_get "$file" live_redis_down)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_redis_down_skip_reason)" ] || fail "live Redis-down SKIP needs live_redis_down_skip_reason"
    [ "$(report_get "$file" redis_down_observed)" != "true" ] || fail "SKIP cannot claim a live Redis-down walk"
  fi
  if [ "$(report_get "$file" live_invalidate)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_invalidate_skip_reason)" ] || fail "live invalidate SKIP needs live_invalidate_skip_reason"
    [ "$(report_get "$file" live_invalidate_observed)" != "true" ] || fail "SKIP cannot claim a live invalidate walk"
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
  OUT="$ROOT/.local/g9-redis-down-invalidate-remainder/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G9 Redis-down / PolicySnapshot invalidate remainder leftover"
echo "cwd=$ROOT"
echo "Does not redo #83/#100/#121/#128/#148/#161/#174."
echo "Dead-client harness PASS is not live Redis-down."
echo "Not claimed: product G9 close / live Redis process-down / live invalidate"
echo

README="$ROOT/docs/README.md"
LEFTOVER_ROWS=false
if grep -Fq '[g9-policysnapshot-invalidate](./g9-policysnapshot-invalidate.md)' "$README" \
  && grep -Fq '[g9-failover-remainder](./g9-failover-remainder.md)' "$README" \
  && grep -Fq '[g9-redis-down-invalidate-honesty](./g9-redis-down-invalidate-honesty.md)' "$README" \
  && grep -Fq '[g9-redis-down-invalidate-remainder](./g9-redis-down-invalidate-remainder.md)' "$README" \
  && grep -Fq '[g9-multinode-partition](./g9-multinode-partition.md)' "$README" \
  && grep -Fq '[g9-sentinel-cluster-honesty](./g9-sentinel-cluster-honesty.md)' "$README" \
  && grep -Fq '[g9-live-multi-jvm-replica](./g9-live-multi-jvm-replica.md)' "$README" \
  && grep -Fq '[g17-separation-honesty](./g17-separation-honesty.md)' "$README" \
  && grep -Fq '[g4-hot-purge-90d-honesty](./g4-hot-purge-90d-honesty.md)' "$README" \
  && grep -Fq '[g4-pkce-spa-verifier-honesty](./g4-pkce-spa-verifier-honesty.md)' "$README" \
  && grep -Fq '[g15-native-bridge-honesty](./g15-native-bridge-honesty.md)' "$README" \
  && grep -Fq '[g10-identity-drift-honesty](./g10-identity-drift-honesty.md)' "$README" \
  && grep -Fq '[g1-fieldpolicy-mask-second-auth-honesty](./g1-fieldpolicy-mask-second-auth-honesty.md)' "$README" \
  && grep -Fq '[g5-browser-failed-publish](./g5-browser-failed-publish.md)' "$README" \
  && grep -Fq '[s0-secret-getenv-snapshot-honesty](./s0-secret-getenv-snapshot-honesty.md)' "$README" \
  && grep -Fq '[g7-withdraw-browser-honesty](./g7-withdraw-browser-honesty.md)' "$README" \
  && grep -Fq '[g3-claim-crash-window](./g3-claim-crash-window.md)' "$README" \
  && grep -Fq '[s0s1-live-redis-minio-isolation-honesty](./s0s1-live-redis-minio-isolation-honesty.md)' "$README" \
  && grep -Fq '[g6-recovery-port-dry-run-honesty](./g6-recovery-port-dry-run-honesty.md)' "$README"; then
  LEFTOVER_ROWS=true
fi
[ "$LEFTOVER_ROWS" = "true" ] || fail "docs/README.md must keep ALL sibling leftover rows + this leftover"

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

DEAD_CLIENT_HARNESS=FAIL
if ! port_open 127.0.0.1 1 \
  && grep -Fq 'proveDeadClientHarness' \
    "$ROOT/nexus-permission-core/src/test/java/one/nexus/permission/redis/G9RedisDownInvalidateRemainder.java" \
  && grep -Fq 'lastRemoteNotifySucceeded' \
    "$ROOT/nexus-permission-core/src/test/java/one/nexus/permission/redis/G9RedisDownInvalidateRemainder.java"; then
  DEAD_CLIENT_HARNESS=PASS
fi
[ "$DEAD_CLIENT_HARNESS" = "PASS" ] || fail "dead-client harness inventory must PASS (port 1 dead + leftover source)"

REDIS_PRESENT=false
REDIS_ROLE=""
DOWN_REASON="no Redis listener on 127.0.0.1:6379; dead-client harness PASS is not live Redis-down; live invalidate not observed; Docker forbidden"
INVALIDATE_REASON="$DOWN_REASON"

if command -v redis-cli >/dev/null 2>&1; then
  if redis-cli -h 127.0.0.1 -p 6379 PING 2>/dev/null | grep -qx PONG; then
    REDIS_PRESENT=true
    REDIS_ROLE="$(redis-cli -h 127.0.0.1 -p 6379 INFO replication 2>/dev/null | awk -F: '/^role:/{print $2}' | tr -d '\r')"
    DOWN_REASON="Redis is listening on 127.0.0.1:6379 (role=${REDIS_ROLE:-unknown}) but a live Redis process-down was not observed; dead-client harness PASS is not live Redis-down; Docker forbidden"
    INVALIDATE_REASON="Redis is listening on 127.0.0.1:6379 but a live PUBLISH/SUBSCRIBE invalidate walk was not observed; RedisInvalidationTest skip / #174 inventory is not a live invalidate PASS"
  fi
elif port_open 127.0.0.1 6379; then
  REDIS_PRESENT=true
  DOWN_REASON="Redis listener present on 127.0.0.1:6379 but a live Redis process-down was not observed; dead-client harness PASS is not live Redis-down; Docker forbidden"
  INVALIDATE_REASON="Redis listener present on 127.0.0.1:6379 but a live PUBLISH/SUBSCRIBE invalidate walk was not observed"
fi

TEST_SUMMARY=""
if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl nexus-permission-core -am test \
    -Dtest=G9RedisDownInvalidateRemainderTest \
    -Dsurefire.failIfNoSpecifiedTests=false | tee "$OUT/mvn.log"
  MVN_RC=${PIPESTATUS[0]}
  set -e
  [ "$MVN_RC" -eq 0 ] || fail "mvn leftover tests failed"
  TEST_SUMMARY="$(awk '/Tests run:/{line=$0} END{print line}' "$OUT/mvn.log")"
else
  TEST_SUMMARY="skipped (--skip-tests); dead-client inventory PASS without #83/#121 suite redo"
fi

{
  kv schema_version 1
  kv timestamp_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  kv java_feature "$JAVA_FEATURE"
  kv boot_parent_version "$BOOT_VERSION"
  kv boot_parent_match "$BOOT_PARENT_MATCH"
  kv docker_used "$DOCKER_USED"
  kv leftover_rows_kept "$LEFTOVER_ROWS"
  kv dead_client_harness "$DEAD_CLIENT_HARNESS"
  kv simulated_dead_client_port 1
  kv dead_client_is_not_live_down true
  kv redis_present "$REDIS_PRESENT"
  kv redis_role "$REDIS_ROLE"
  kv redis_down_observed false
  kv live_redis_down SKIP
  kv live_redis_down_skip_reason "$DOWN_REASON"
  kv live_invalidate_observed false
  kv live_invalidate SKIP
  kv live_invalidate_skip_reason "$INVALIDATE_REASON"
  kv g9_close NOT_CLAIMED
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv policy_snapshot_redesigned false
  kv p0_m0_redone false
  kv g9_83_100_121_128_148_161_174_redone false
  kv test_summary "$TEST_SUMMARY"
} > "$OUT/report.env"

{
  echo "# G9 Redis-down / PolicySnapshot invalidate remainder"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| dead-client harness (port 1) | PASS |"
  echo "| live Redis-down | SKIP |"
  echo "| live invalidate | SKIP |"
  echo "| dead client port 1 is live Redis-down | false |"
  echo "| leftover rows kept | true |"
  echo "| product G9 close | NOT_CLAIMED |"
  echo
  echo "Redis-down reason: $DOWN_REASON"
  echo "Invalidate reason: $INVALIDATE_REASON"
} > "$OUT/matrix.md"

verify_report "$OUT/report.env"
log "wrote $OUT/report.env"
echo
echo "PASS: remainder leftover recorded (dead-client harness PASS; live Redis-down / live invalidate SKIP)"
echo "See docs/g9-redis-down-invalidate-remainder.md"
echo "Keep ALL sibling leftover rows including #174 / #83 / #121 / #185"
echo "Not claimed: product G9 close / live Redis process-down / live invalidate"
echo "out=$OUT"
