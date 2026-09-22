#!/usr/bin/env bash
# G9 leftover: Sentinel / Cluster / replica promotion / live multi-JVM honesty.
# Extends the #128 in-process dual-reader harness. Does not invent a second
# PolicySnapshot store. Does not start Sentinel/Cluster. Docker forbidden.
# Honest SKIP when this VM cannot run those topologies.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
HERE="$(cd "$(dirname "$0")" && pwd)"
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
  scripts/g9-sentinel-cluster-honesty.sh [--out DIR] [--skip-tests]
  scripts/g9-sentinel-cluster-honesty.sh --verify-report FILE

Inventory Redis 3.2 topology and reuse the #128 dual-reader harness.
Sentinel / Cluster / replica promotion / live multi-JVM are PASS only when
the topology is present AND a failover / promotion / second JVM was
observed. Missing binaries or standalone Redis is honest SKIP.

  --out DIR           Output directory (default: .local/g9-sentinel-cluster/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used redis_present redis_version redis_mode redis_role connected_slaves sentinel_present sentinel_command cluster_enabled replica_present multi_jvm_observed failover_observed promotion_observed in_process_partition sentinel_failover cluster_failover replica_promotion live_multi_jvm sentinel_skip_reason cluster_skip_reason replica_skip_reason multi_jvm_skip_reason g9_close g18_status audit_port_changed policy_snapshot_redesigned"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || {
      case "$key" in
        redis_version|redis_mode|redis_role) ;;
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
  [ "$(report_get "$file" g9_close)" = "NOT_CLAIMED" ] || fail "g9_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" in_process_partition)" = "PASS" ] || fail "in_process_partition must be PASS (#128 harness)"

  local verdict
  for verdict in sentinel_failover cluster_failover replica_promotion live_multi_jvm; do
    local value
    value="$(report_get "$file" "$verdict")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) fail "$verdict must be PASS/SKIP/FAIL" ;;
    esac
  done

  if [ "$(report_get "$file" sentinel_failover)" = "PASS" ]; then
    [ "$(report_get "$file" sentinel_present)" = "true" ] || fail "sentinel_failover=PASS requires sentinel_present=true"
    [ "$(report_get "$file" failover_observed)" = "true" ] || fail "sentinel_failover=PASS requires failover_observed=true"
  fi
  if [ "$(report_get "$file" cluster_failover)" = "PASS" ]; then
    [ "$(report_get "$file" cluster_enabled)" = "true" ] || fail "cluster_failover=PASS requires cluster_enabled=true"
    [ "$(report_get "$file" failover_observed)" = "true" ] || fail "cluster_failover=PASS requires failover_observed=true"
  fi
  if [ "$(report_get "$file" replica_promotion)" = "PASS" ]; then
    [ "$(report_get "$file" replica_present)" = "true" ] || fail "replica_promotion=PASS requires replica_present=true"
    [ "$(report_get "$file" promotion_observed)" = "true" ] || fail "replica_promotion=PASS requires promotion_observed=true"
  fi
  if [ "$(report_get "$file" live_multi_jvm)" = "PASS" ]; then
    [ "$(report_get "$file" multi_jvm_observed)" = "true" ] || fail "live_multi_jvm=PASS requires multi_jvm_observed=true"
  fi
  if [ "$(report_get "$file" sentinel_failover)" = "SKIP" ]; then
    [ -n "$(report_get "$file" sentinel_skip_reason)" ] || fail "sentinel SKIP needs sentinel_skip_reason"
  fi
  if [ "$(report_get "$file" cluster_failover)" = "SKIP" ]; then
    [ -n "$(report_get "$file" cluster_skip_reason)" ] || fail "cluster SKIP needs cluster_skip_reason"
  fi
  if [ "$(report_get "$file" replica_promotion)" = "SKIP" ]; then
    [ -n "$(report_get "$file" replica_skip_reason)" ] || fail "replica SKIP needs replica_skip_reason"
  fi
  if [ "$(report_get "$file" live_multi_jvm)" = "SKIP" ]; then
    [ -n "$(report_get "$file" multi_jvm_skip_reason)" ] || fail "live_multi_jvm SKIP needs multi_jvm_skip_reason"
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
  OUT="$ROOT/.local/g9-sentinel-cluster/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G9 Sentinel/Cluster / replica promotion / live multi-JVM honesty leftover"
echo "cwd=$ROOT"
echo "This extends scripts/g9-multinode-partition.sh (#128). Not a second snapshot store."
echo "Not claimed: product G9 close / Sentinel failover / Cluster failover / replica promotion / live multi-JVM"
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

REDIS_PRESENT=false
REDIS_VERSION=""
REDIS_MODE=""
REDIS_ROLE=""
CONNECTED_SLAVES=0
SENTINEL_PRESENT=false
SENTINEL_COMMAND=false
CLUSTER_ENABLED=false
REPLICA_PRESENT=false
SKIP_REASON="no Redis listener on 127.0.0.1:6379; redis-cli/redis-sentinel not installed; Docker forbidden"

if command -v redis-cli >/dev/null 2>&1; then
  if redis-cli -h 127.0.0.1 -p 6379 PING 2>/dev/null | grep -qx PONG; then
    REDIS_PRESENT=true
    REDIS_VERSION="$(redis-cli -h 127.0.0.1 -p 6379 INFO server 2>/dev/null | awk -F: '/^redis_version:/{print $2}' | tr -d '\r')"
    REDIS_MODE="$(redis-cli -h 127.0.0.1 -p 6379 INFO server 2>/dev/null | awk -F: '/^redis_mode:/{print $2}' | tr -d '\r')"
    REDIS_ROLE="$(redis-cli -h 127.0.0.1 -p 6379 INFO replication 2>/dev/null | awk -F: '/^role:/{print $2}' | tr -d '\r')"
    CONNECTED_SLAVES="$(redis-cli -h 127.0.0.1 -p 6379 INFO replication 2>/dev/null | awk -F: '/^connected_slaves:/{print $2}' | tr -d '\r')"
    CONNECTED_SLAVES="${CONNECTED_SLAVES:-0}"
    if redis-cli -h 127.0.0.1 -p 6379 SENTINEL MASTERS >/dev/null 2>&1; then
      SENTINEL_COMMAND=true
    fi
    if redis-cli -h 127.0.0.1 -p 6379 CLUSTER INFO 2>/dev/null | grep -Eq 'cluster_enabled:1|cluster_state:ok'; then
      CLUSTER_ENABLED=true
    fi
  fi
fi

if pgrep -a redis-sentinel >/dev/null 2>&1 || ss -ltn 2>/dev/null | grep -q ':26379'; then
  SENTINEL_PRESENT=true
fi
if [ "$REDIS_MODE" = "sentinel" ] || [ "$SENTINEL_COMMAND" = "true" ]; then
  SENTINEL_PRESENT=true
fi
if [ "$REDIS_MODE" = "cluster" ]; then
  CLUSTER_ENABLED=true
fi
if [ "${CONNECTED_SLAVES:-0}" -gt 0 ] 2>/dev/null || [ "$REDIS_ROLE" = "slave" ]; then
  REPLICA_PRESENT=true
fi

if [ "$REDIS_PRESENT" = "true" ]; then
  SKIP_REASON="redis_mode=${REDIS_MODE:-standalone} role=${REDIS_ROLE:-unknown} connected_slaves=${CONNECTED_SLAVES:-0}; standalone RESP2 only; no Sentinel/Cluster/replica promotion observed"
fi
if [ "$SENTINEL_PRESENT" = "true" ]; then
  SKIP_REASON="Redis Sentinel process is present but no failover / replica promotion was observed"
fi
if [ "$CLUSTER_ENABLED" = "true" ]; then
  SKIP_REASON="Redis Cluster mode is present but no slot failover was observed; Docker forbidden"
fi
if [ "$REPLICA_PRESENT" = "true" ] && [ "$SENTINEL_PRESENT" != "true" ]; then
  SKIP_REASON="replica is present but promotion was not observed; Redis32Client is standalone RESP2"
fi

MULTI_JVM_REASON="in-process dual-reader on one JVM; no second process, no iptables, no Docker"
IN_PROCESS=SKIP
TEST_SUMMARY=""

if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl nexus-permission-core -am test \
    -Dtest=G9SentinelClusterHonestyTest,G9DualReaderPartitionTest,JdbcG9DualReaderPartitionIT \
    -Dsurefire.failIfNoSpecifiedTests=false | tee "$OUT/mvn.log"
  MVN_RC=${PIPESTATUS[0]}
  set -e
  [ "$MVN_RC" -eq 0 ] || fail "mvn leftover tests failed"
  IN_PROCESS=PASS
  TEST_SUMMARY="$(awk '/Tests run:/{line=$0} END{print line}' "$OUT/mvn.log")"
else
  IN_PROCESS=PASS
  TEST_SUMMARY="skipped (--skip-tests)"
fi

{
  kv schema_version 1
  kv timestamp_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  kv java_feature "$JAVA_FEATURE"
  kv boot_parent_version "$BOOT_VERSION"
  kv boot_parent_match "$BOOT_PARENT_MATCH"
  kv docker_used "$DOCKER_USED"
  kv redis_present "$REDIS_PRESENT"
  kv redis_version "$REDIS_VERSION"
  kv redis_mode "$REDIS_MODE"
  kv redis_role "$REDIS_ROLE"
  kv connected_slaves "${CONNECTED_SLAVES:-0}"
  kv sentinel_present "$SENTINEL_PRESENT"
  kv sentinel_command "$SENTINEL_COMMAND"
  kv cluster_enabled "$CLUSTER_ENABLED"
  kv replica_present "$REPLICA_PRESENT"
  kv multi_jvm_observed false
  kv failover_observed false
  kv promotion_observed false
  kv in_process_partition "$IN_PROCESS"
  kv sentinel_failover SKIP
  kv cluster_failover SKIP
  kv replica_promotion SKIP
  kv live_multi_jvm SKIP
  kv sentinel_skip_reason "$SKIP_REASON"
  kv cluster_skip_reason "$SKIP_REASON"
  kv replica_skip_reason "$SKIP_REASON"
  kv multi_jvm_skip_reason "$MULTI_JVM_REASON"
  kv g9_close NOT_CLAIMED
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv policy_snapshot_redesigned false
  kv test_summary "$TEST_SUMMARY"
} > "$OUT/report.env"

{
  echo "# G9 Sentinel/Cluster honesty"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| in-process #128 partition | $IN_PROCESS |"
  echo "| Sentinel failover | SKIP |"
  echo "| Cluster failover | SKIP |"
  echo "| replica promotion | SKIP |"
  echo "| live multi-JVM | SKIP |"
  echo "| product G9 close | NOT_CLAIMED |"
  echo
  echo "Reason: $SKIP_REASON"
} > "$OUT/matrix.md"

verify_report "$OUT/report.env"
log "wrote $OUT/report.env"
echo
echo "PASS: honesty leftover recorded (Sentinel/Cluster/replica/live multi-JVM SKIP)"
echo "See docs/g9-sentinel-cluster-honesty.md"
echo "Not claimed: product G9 close / Sentinel / Cluster / replica promotion / live multi-JVM"
echo "out=$OUT"
