#!/usr/bin/env bash
# G9 leftover: live multi-JVM / replica promotion honesty.
# Does not redo #89 / #83 / #100 / #121 / #128 / #148.
# Does not start a second JVM, a replica, Sentinel, Cluster, or Docker.
# Honest SKIP when multi-JVM / replica are not available.
# Does not claim Sentinel / Cluster product close.
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
  scripts/g9-live-multi-jvm-replica.sh [--out DIR] [--skip-tests]
  scripts/g9-live-multi-jvm-replica.sh --verify-report FILE

Live multi-JVM / replica promotion are PASS only when the topology is
present AND a promotion / second JVM partition was observed. Missing
replica or a single JVM is honest SKIP. Sentinel / Cluster product close
stays NOT_CLAIMED.

  --out DIR           Output directory (default: .local/g9-live-multi-jvm-replica/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used redis_present redis_role connected_slaves replica_present promotion_observed replica_promotion replica_skip_reason second_jvm_present second_jvm_pid multi_jvm_observed live_multi_jvm multi_jvm_skip_reason sentinel_cluster_close g9_close g18_status audit_port_changed policy_snapshot_redesigned p0_m0_redone g9_89_83_100_121_128_148_redone"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || {
      case "$key" in
        redis_role|second_jvm_pid) ;;
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
  [ "$(report_get "$file" g9_89_83_100_121_128_148_redone)" = "false" ] || fail "must not redo #89/#83/#100/#121/#128/#148"
  [ "$(report_get "$file" g9_close)" = "NOT_CLAIMED" ] || fail "g9_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" sentinel_cluster_close)" = "NOT_CLAIMED" ] || fail "sentinel_cluster_close must stay NOT_CLAIMED"

  local verdict
  for verdict in replica_promotion live_multi_jvm; do
    local value
    value="$(report_get "$file" "$verdict")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) fail "$verdict must be PASS/SKIP/FAIL" ;;
    esac
  done

  if [ "$(report_get "$file" replica_promotion)" = "PASS" ]; then
    [ "$(report_get "$file" replica_present)" = "true" ] || fail "replica_promotion=PASS requires replica_present=true"
    [ "$(report_get "$file" promotion_observed)" = "true" ] || fail "replica_promotion=PASS requires promotion_observed=true"
  fi
  if [ "$(report_get "$file" live_multi_jvm)" = "PASS" ]; then
    [ "$(report_get "$file" second_jvm_present)" = "true" ] || fail "live_multi_jvm=PASS requires second_jvm_present=true"
    [ "$(report_get "$file" multi_jvm_observed)" = "true" ] || fail "live_multi_jvm=PASS requires multi_jvm_observed=true"
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
  OUT="$ROOT/.local/g9-live-multi-jvm-replica/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G9 live multi-JVM / replica promotion honesty leftover"
echo "cwd=$ROOT"
echo "Does not redo #89/#83/#100/#121/#128/#148."
echo "Not claimed: product G9 close / Sentinel / Cluster / replica promotion / live multi-JVM"
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
REDIS_ROLE=""
CONNECTED_SLAVES=0
REPLICA_PRESENT=false
PROMOTION_OBSERVED=false
REPLICA_REASON="no Redis listener on 127.0.0.1:6379; replica promotion not observed; Docker forbidden"

if command -v redis-cli >/dev/null 2>&1; then
  if redis-cli -h 127.0.0.1 -p 6379 PING 2>/dev/null | grep -qx PONG; then
    REDIS_PRESENT=true
    REDIS_ROLE="$(redis-cli -h 127.0.0.1 -p 6379 INFO replication 2>/dev/null | awk -F: '/^role:/{print $2}' | tr -d '\r')"
    CONNECTED_SLAVES="$(redis-cli -h 127.0.0.1 -p 6379 INFO replication 2>/dev/null | awk -F: '/^connected_slaves:/{print $2}' | tr -d '\r')"
    CONNECTED_SLAVES="${CONNECTED_SLAVES:-0}"
  fi
fi

if [ "$REDIS_PRESENT" = "true" ]; then
  if [ "${CONNECTED_SLAVES:-0}" -gt 0 ] 2>/dev/null || [ "$REDIS_ROLE" = "slave" ]; then
    REPLICA_PRESENT=true
  fi
  if [ "$REPLICA_PRESENT" = "true" ]; then
    REPLICA_REASON="replica is present (role=${REDIS_ROLE:-unknown}, connected_slaves=${CONNECTED_SLAVES:-0}) but promotion was not observed; Redis32Client is not a failover client"
  else
    REPLICA_REASON="redis_role=${REDIS_ROLE:-unknown} connected_slaves=${CONNECTED_SLAVES:-0}; Redis32Client is standalone RESP2; replica promotion not observed"
  fi
fi

if [ "${NEXUS_G9_PROMOTION_OBSERVED:-}" = "true" ] && [ "$REPLICA_PRESENT" = "true" ]; then
  PROMOTION_OBSERVED=true
fi

SELF_PID="$$"
SECOND_JVM_PRESENT=false
SECOND_JVM_PID="${NEXUS_G9_SECOND_JVM_PID:-}"
MULTI_JVM_OBSERVED=false
MULTI_JVM_REASON="one application JVM; surefire parent/fork is not a live multi-JVM partition; no second JAR, no iptables/netem, Docker forbidden"

if [ -n "$SECOND_JVM_PID" ] && [ -d "/proc/${SECOND_JVM_PID}" ] && [ "$SECOND_JVM_PID" != "$SELF_PID" ]; then
  SECOND_JVM_PRESENT=true
  MULTI_JVM_REASON="second JVM pid=${SECOND_JVM_PID} is listed but a live partition / netem / two-JAR walk was not observed"
fi
if [ "${NEXUS_G9_MULTI_JVM_OBSERVED:-}" = "true" ] && [ "$SECOND_JVM_PRESENT" = "true" ]; then
  MULTI_JVM_OBSERVED=true
fi

TEST_SUMMARY=""
if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl nexus-permission-core -am test \
    -Dtest=G9LiveMultiJvmReplicaHonestyTest \
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
  kv redis_present "$REDIS_PRESENT"
  kv redis_role "$REDIS_ROLE"
  kv connected_slaves "${CONNECTED_SLAVES:-0}"
  kv replica_present "$REPLICA_PRESENT"
  kv promotion_observed "$PROMOTION_OBSERVED"
  kv replica_promotion SKIP
  kv replica_skip_reason "$REPLICA_REASON"
  kv second_jvm_present "$SECOND_JVM_PRESENT"
  kv second_jvm_pid "$SECOND_JVM_PID"
  kv multi_jvm_observed "$MULTI_JVM_OBSERVED"
  kv live_multi_jvm SKIP
  kv multi_jvm_skip_reason "$MULTI_JVM_REASON"
  kv sentinel_cluster_close NOT_CLAIMED
  kv g9_close NOT_CLAIMED
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv policy_snapshot_redesigned false
  kv p0_m0_redone false
  kv g9_89_83_100_121_128_148_redone false
  kv test_summary "$TEST_SUMMARY"
} > "$OUT/report.env"

{
  echo "# G9 live multi-JVM / replica promotion honesty"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| replica promotion | SKIP |"
  echo "| live multi-JVM | SKIP |"
  echo "| Sentinel / Cluster product close | NOT_CLAIMED |"
  echo "| product G9 close | NOT_CLAIMED |"
  echo
  echo "Replica reason: $REPLICA_REASON"
  echo "Multi-JVM reason: $MULTI_JVM_REASON"
} > "$OUT/matrix.md"

verify_report "$OUT/report.env"
log "wrote $OUT/report.env"
echo
echo "PASS: honesty leftover recorded (replica / live multi-JVM SKIP)"
echo "See docs/g9-live-multi-jvm-replica.md"
echo "Not claimed: product G9 close / Sentinel / Cluster / replica promotion / live multi-JVM"
echo "out=$OUT"
