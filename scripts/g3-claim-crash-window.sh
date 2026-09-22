#!/usr/bin/env bash
# G3 leftover: claim-then-write crash window / multi-node Dispatcher honesty.
# Does not redo #139 consume-idempotent leftover.
# Does not rebuild Outbox / Dispatcher. Does not claim product G3 close.
# Simulated crash window or honest SKIP for live multi-node.
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
  scripts/g3-claim-crash-window.sh [--out DIR] [--skip-tests]
  scripts/g3-claim-crash-window.sh --verify-report FILE

Claim-then-write crash window is recorded as SIMULATED (in-process).
Live multi-node Dispatcher is PASS only when a second JVM is present AND
a multi-node walk was observed. Missing second JVM is honest SKIP.
Product G3 close stays NOT_CLAIMED. #139 is not redone.

  --out DIR           Output directory (default: .local/g3-claim-crash-window/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used crash_window_claim_committed crash_window_write_committed crash_window_inbox_status crash_window_replay_claim crash_window_side_effect_lost live_crash_observed crash_window crash_window_skip_reason second_jvm_present second_jvm_pid multi_node_observed multi_node_dispatcher multi_node_skip_reason g3_close g18_status audit_port_changed outbox_dispatcher_redesigned p0_m0_redone g3_139_redone"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || {
      case "$key" in
        second_jvm_pid) ;;
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
  [ "$(report_get "$file" outbox_dispatcher_redesigned)" = "false" ] || fail "outbox_dispatcher_redesigned must be false"
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || fail "p0_m0_redone must be false"
  [ "$(report_get "$file" g3_139_redone)" = "false" ] || fail "must not redo #139"
  [ "$(report_get "$file" g3_close)" = "NOT_CLAIMED" ] || fail "g3_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" crash_window_claim_committed)" = "true" ] || fail "claim must have committed"
  [ "$(report_get "$file" crash_window_write_committed)" = "false" ] || fail "write must not have committed"
  [ "$(report_get "$file" crash_window_inbox_status)" = "PENDING" ] || fail "inbox must stay PENDING"
  [ "$(report_get "$file" crash_window_replay_claim)" = "DUPLICATE" ] || fail "replay must be DUPLICATE"
  [ "$(report_get "$file" crash_window_side_effect_lost)" = "true" ] || fail "side effect must stay lost"

  local crash
  crash="$(report_get "$file" crash_window)"
  case "$crash" in
    SIMULATED|SKIP|PASS|FAIL) ;;
    *) fail "crash_window must be SIMULATED/SKIP/PASS/FAIL" ;;
  esac
  local nodes
  nodes="$(report_get "$file" multi_node_dispatcher)"
  case "$nodes" in
    PASS|SKIP|FAIL) ;;
    *) fail "multi_node_dispatcher must be PASS/SKIP/FAIL" ;;
  esac

  if [ "$crash" = "PASS" ]; then
    [ "$(report_get "$file" live_crash_observed)" = "true" ] || fail "crash_window=PASS requires live_crash_observed=true"
  fi
  if [ "$nodes" = "PASS" ]; then
    [ "$(report_get "$file" second_jvm_present)" = "true" ] || fail "multi_node_dispatcher=PASS requires second_jvm_present=true"
    [ "$(report_get "$file" multi_node_observed)" = "true" ] || fail "multi_node_dispatcher=PASS requires multi_node_observed=true"
  fi
  if [ "$crash" = "SKIP" ] || [ "$crash" = "SIMULATED" ]; then
    [ -n "$(report_get "$file" crash_window_skip_reason)" ] || fail "crash_window $crash needs crash_window_skip_reason"
  fi
  if [ "$nodes" = "SKIP" ]; then
    [ -n "$(report_get "$file" multi_node_skip_reason)" ] || fail "multi_node SKIP needs multi_node_skip_reason"
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
  OUT="$ROOT/.local/g3-claim-crash-window/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G3 claim-then-write crash window / multi-node Dispatcher honesty leftover"
echo "cwd=$ROOT"
echo "Does not redo #139."
echo "Not claimed: product G3 close / Outbox rebuild / live multi-node Dispatcher"
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

CRASH_REASON="in-process claim-then-write crash simulation; first claim committed, inbox UPDATE did not; replay DUPLICATE skips write; not a live process kill"
SELF_PID="$$"
SECOND_JVM_PRESENT=false
SECOND_JVM_PID="${NEXUS_G3_SECOND_DISPATCHER_PID:-}"
MULTI_NODE_OBSERVED=false
MULTI_NODE_REASON="one application JVM; surefire parent/fork is not a multi-node Dispatcher; no second JAR, no Kafka/bus, Docker forbidden"

if [ -n "$SECOND_JVM_PID" ] && [ -d "/proc/${SECOND_JVM_PID}" ] && [ "$SECOND_JVM_PID" != "$SELF_PID" ]; then
  SECOND_JVM_PRESENT=true
  MULTI_NODE_REASON="second JVM pid=${SECOND_JVM_PID} is listed but a live multi-node Dispatcher / bus walk was not observed"
fi
if [ "${NEXUS_G3_MULTI_NODE_OBSERVED:-}" = "true" ] && [ "$SECOND_JVM_PRESENT" = "true" ]; then
  MULTI_NODE_OBSERVED=true
fi

TEST_SUMMARY=""
if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl nexus-event-core -am test \
    -Dtest=G3ClaimCrashWindowHonestyTest \
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
  kv crash_window_claim_committed true
  kv crash_window_write_committed false
  kv crash_window_inbox_status PENDING
  kv crash_window_replay_claim DUPLICATE
  kv crash_window_side_effect_lost true
  kv live_crash_observed false
  kv crash_window SIMULATED
  kv crash_window_skip_reason "$CRASH_REASON"
  kv second_jvm_present "$SECOND_JVM_PRESENT"
  kv second_jvm_pid "$SECOND_JVM_PID"
  kv multi_node_observed "$MULTI_NODE_OBSERVED"
  kv multi_node_dispatcher SKIP
  kv multi_node_skip_reason "$MULTI_NODE_REASON"
  kv g3_close NOT_CLAIMED
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv outbox_dispatcher_redesigned false
  kv p0_m0_redone false
  kv g3_139_redone false
  kv test_summary "$TEST_SUMMARY"
} > "$OUT/report.env"

{
  echo "# G3 claim-then-write crash window / multi-node Dispatcher honesty"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| claim-then-write crash window | SIMULATED |"
  echo "| multi-node Dispatcher | SKIP |"
  echo "| product G3 close | NOT_CLAIMED |"
  echo
  echo "Crash reason: $CRASH_REASON"
  echo "Multi-node reason: $MULTI_NODE_REASON"
} > "$OUT/matrix.md"

verify_report "$OUT/report.env"
log "wrote $OUT/report.env"
echo
echo "PASS: honesty leftover recorded (crash window SIMULATED / multi-node SKIP)"
echo "See docs/g3-claim-crash-window.md"
echo "Not claimed: product G3 close / Outbox rebuild / live multi-node Dispatcher"
echo "out=$OUT"
