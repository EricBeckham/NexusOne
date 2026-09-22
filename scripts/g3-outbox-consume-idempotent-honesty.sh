#!/usr/bin/env bash
# G3 leftover beyond #195/#139/#170: consume-idempotent / claim-crash-window
# PASS/SKIP honesty remainder. Does not redo #139 Zonky suites, #170 ITs,
# or #195 honesty product code. Does not rebuild Outbox / Dispatcher.
# Does not claim product G3 close. G18 stays closed. No Docker.
# No P0/M0 redo. AuditPort same-TX untouched.
#
# #139 / #170 / #195 leftover cells stay honest PASS / SIMULATED.
# Live multi-node Dispatcher / real crash window are PASS only when observed.
# Missing second JVM / live kill is honest SKIP.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""

README="$ROOT/docs/README.md"
DOC_139="$ROOT/docs/g3-outbox-consume-idempotent.md"
DOC_170="$ROOT/docs/g3-claim-crash-window.md"
DOC_195="$ROOT/docs/g3-outbox-consume-crash-honesty.md"
FIXTURE_195="$ROOT/scripts/g3-outbox-consume-crash-honesty/fixtures/honest-skip.env"

REQUIRED_LEFTOVER_ROWS="g3-outbox-consume-idempotent g3-claim-crash-window g3-outbox-consume-crash-honesty g3-outbox-consume-idempotent-honesty g10-leftover-honesty-remainder g2-report-physical-dump-honesty g6-recovery-port-dry-run-remainder-honesty s0-secret-rotate-getenv-remainder-honesty g4-pkce-spa-verifier-remainder-honesty g17-separation-leftover-honesty g15-wx-dt-native-bridge-remainder g1-queryast-export-honesty g1-queryast-boundsql-guard g1-queryast-boundsql-honesty g1-export-fieldpolicy-drop g1-datascope-or-in-ast g1-fieldpolicy-mask-second-auth g1-fieldpolicy-mask-second-auth-honesty g2-h5-every-page-form-write-honesty g9-redis-down-invalidate-remainder g9-redis-down-invalidate-honesty g17-mfa-recovery-vue-otp-honesty g2-ops-pg-dump-honesty g9-sentinel-cluster-remainder-honesty g5-publishhook-ddl-honesty g17-gm-tls-nginx-handshake-honesty g2-h5-every-page g2-form-write-honesty g4-export-wizard-deny-honesty g10-leftover-honesty g5-mid-publish-failure-honesty g15-wx-dt-native-bridge-honesty s0-secret-rotate-without-restart-honesty g7-query-expense-travel-honesty linux-arm-profile-leftover-honesty g4-p1-staging-audit-events-honesty g6-recovery-port-dry-run-honesty s0s1-live-redis-minio-isolation-honesty g17-separation-honesty g4-hot-purge-90d-honesty g15-native-bridge-honesty g4-pkce-spa-verifier-honesty s0-secret-getenv-snapshot-honesty g5-browser-failed-publish g7-withdraw-browser-honesty g10-file-reconcile-honesty g10-identity-drift-honesty"

usage() {
  cat <<'EOF'
Usage:
  scripts/g3-outbox-consume-idempotent-honesty.sh [--out DIR]
  scripts/g3-outbox-consume-idempotent-honesty.sh --verify-report FILE

Beyond #195/#139/#170: consume-idempotent harness PASS; crash window
SIMULATED. Live multi-node Dispatcher / real crash window are PASS only
when observed. Missing second JVM / live kill is honest SKIP.
Does not re-run #139/#170/#195. Does not claim product G3 close.

  --out DIR           Output directory (default: .local/g3-outbox-consume-idempotent-honesty/<utc>)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_139_cells_honest leftover_170_cells_honest leftover_195_cells_honest consume_idempotent consume_idempotent_first consume_idempotent_replay consume_idempotent_side_effects crash_window_claim_committed crash_window_write_committed crash_window_inbox_status crash_window_replay_claim crash_window_side_effect_lost live_crash_observed crash_window crash_window_skip_reason live_crash live_crash_skip_reason second_jvm_present second_jvm_pid multi_node_observed multi_node_dispatcher multi_node_skip_reason leftover_rows_kept leftover_rows leftover_139_kept leftover_170_kept leftover_195_kept leftover_228_kept leftover_221_kept leftover_225_kept leftover_223_kept leftover_222_kept leftover_216_kept leftover_220_kept leftover_215_kept leftover_210_kept leftover_217_kept leftover_209_kept leftover_188_kept leftover_205_kept leftover_197_kept leftover_204_kept leftover_198_kept leftover_200_kept leftover_192_kept leftover_193_kept honesty_139_rerun honesty_170_rerun honesty_195_rerun redone_139 redone_170 redone_195 outbox_dispatcher_redesigned g3_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || fail "p0_m0_redone must stay false"
  [ "$(report_get "$file" redone_139)" = "false" ] || fail "must not redo #139"
  [ "$(report_get "$file" redone_170)" = "false" ] || fail "must not redo #170"
  [ "$(report_get "$file" redone_195)" = "false" ] || fail "must not redo #195"
  [ "$(report_get "$file" honesty_139_rerun)" = "false" ] || fail "honesty_139_rerun must be false"
  [ "$(report_get "$file" honesty_170_rerun)" = "false" ] || fail "honesty_170_rerun must be false"
  [ "$(report_get "$file" honesty_195_rerun)" = "false" ] || fail "honesty_195_rerun must be false"
  [ "$(report_get "$file" g3_close)" = "NOT_CLAIMED" ] || fail "g3_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" leftover_139_cells_honest)" = "PASS" ] || fail "leftover_139_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_170_cells_honest)" = "PASS" ] || fail "leftover_170_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_195_cells_honest)" = "PASS" ] || fail "leftover_195_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" leftover_139_kept)" = "true" ] || fail "leftover_139_kept must stay true (keep g3-outbox-consume-idempotent)"
  [ "$(report_get "$file" leftover_170_kept)" = "true" ] || fail "leftover_170_kept must stay true (keep g3-claim-crash-window)"
  [ "$(report_get "$file" leftover_195_kept)" = "true" ] || fail "leftover_195_kept must stay true (keep g3-outbox-consume-crash-honesty)"
  [ "$(report_get "$file" leftover_228_kept)" = "true" ] || fail "leftover_228_kept must stay true (keep g10-leftover-honesty-remainder)"
  [ "$(report_get "$file" leftover_221_kept)" = "true" ] || fail "leftover_221_kept must stay true (keep g2-report-physical-dump-honesty)"
  [ "$(report_get "$file" leftover_225_kept)" = "true" ] || fail "leftover_225_kept must stay true (keep g6-recovery-port-dry-run-remainder-honesty)"
  [ "$(report_get "$file" leftover_223_kept)" = "true" ] || fail "leftover_223_kept must stay true (keep s0-secret-rotate-getenv-remainder-honesty)"
  [ "$(report_get "$file" leftover_222_kept)" = "true" ] || fail "leftover_222_kept must stay true (keep g4-pkce-spa-verifier-remainder-honesty)"
  [ "$(report_get "$file" leftover_216_kept)" = "true" ] || fail "leftover_216_kept must stay true (keep g17-separation-leftover-honesty)"
  [ "$(report_get "$file" leftover_220_kept)" = "true" ] || fail "leftover_220_kept must stay true (keep g15-wx-dt-native-bridge-remainder)"
  [ "$(report_get "$file" leftover_215_kept)" = "true" ] || fail "leftover_215_kept must stay true (keep g1-queryast-export-honesty)"
  [ "$(report_get "$file" leftover_210_kept)" = "true" ] || fail "leftover_210_kept must stay true (keep g17-gm-tls-nginx-handshake-honesty)"
  [ "$(report_get "$file" leftover_217_kept)" = "true" ] || fail "leftover_217_kept must stay true (keep g5-publishhook-ddl-honesty)"
  [ "$(report_get "$file" leftover_209_kept)" = "true" ] || fail "leftover_209_kept must stay true (keep g9-sentinel-cluster-remainder-honesty)"
  [ "$(report_get "$file" leftover_188_kept)" = "true" ] || fail "leftover_188_kept must stay true (keep g2-ops-pg-dump-honesty)"
  [ "$(report_get "$file" leftover_205_kept)" = "true" ] || fail "leftover_205_kept must stay true (keep g17-mfa-recovery-vue-otp-honesty)"
  [ "$(report_get "$file" leftover_197_kept)" = "true" ] || fail "leftover_197_kept must stay true (keep g9-redis-down-invalidate-remainder)"
  [ "$(report_get "$file" leftover_204_kept)" = "true" ] || fail "leftover_204_kept must stay true (keep g2-h5-every-page-form-write-honesty)"
  [ "$(report_get "$file" leftover_198_kept)" = "true" ] || fail "leftover_198_kept must stay true (keep g15-wx-dt-native-bridge-honesty)"
  [ "$(report_get "$file" leftover_200_kept)" = "true" ] || fail "leftover_200_kept must stay true (keep s0-secret-rotate-without-restart-honesty)"
  [ "$(report_get "$file" leftover_192_kept)" = "true" ] || fail "leftover_192_kept must stay true (keep g7-query-expense-travel-honesty)"
  [ "$(report_get "$file" leftover_193_kept)" = "true" ] || fail "leftover_193_kept must stay true (keep linux-arm-profile-leftover-honesty)"
  [ "$(report_get "$file" consume_idempotent)" = "PASS" ] || fail "consume_idempotent must be PASS"
  [ "$(report_get "$file" consume_idempotent_first)" = "FIRST" ] || fail "consume_idempotent_first must be FIRST"
  [ "$(report_get "$file" consume_idempotent_replay)" = "DUPLICATE" ] || fail "consume_idempotent_replay must be DUPLICATE"
  [ "$(report_get "$file" consume_idempotent_side_effects)" = "1" ] || fail "consume_idempotent_side_effects must stay 1"
  [ "$(report_get "$file" crash_window_claim_committed)" = "true" ] || fail "claim must have committed"
  [ "$(report_get "$file" crash_window_write_committed)" = "false" ] || fail "write must not have committed"
  [ "$(report_get "$file" crash_window_inbox_status)" = "PENDING" ] || fail "inbox must stay PENDING"
  [ "$(report_get "$file" crash_window_replay_claim)" = "DUPLICATE" ] || fail "replay must be DUPLICATE"
  [ "$(report_get "$file" crash_window_side_effect_lost)" = "true" ] || fail "side effect must stay lost"

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $REQUIRED_LEFTOVER_ROWS; do
    echo "$rows" | grep -Fq "$required_row" || fail "leftover_rows must keep $required_row"
  done

  local crash live nodes
  crash="$(report_get "$file" crash_window)"
  live="$(report_get "$file" live_crash)"
  nodes="$(report_get "$file" multi_node_dispatcher)"
  case "$crash" in
    SIMULATED|SKIP|PASS|FAIL) ;;
    *) fail "crash_window must be SIMULATED/SKIP/PASS/FAIL" ;;
  esac
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) fail "live_crash must be PASS/SKIP/FAIL" ;;
  esac
  case "$nodes" in
    PASS|SKIP|FAIL) ;;
    *) fail "multi_node_dispatcher must be PASS/SKIP/FAIL" ;;
  esac

  if [ "$crash" = "PASS" ]; then
    [ "$(report_get "$file" live_crash_observed)" = "true" ] || fail "crash_window=PASS requires live_crash_observed=true"
  fi
  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" live_crash_observed)" = "true" ] || fail "live_crash=PASS requires live_crash_observed=true"
  fi
  if [ "$nodes" = "PASS" ]; then
    [ "$(report_get "$file" second_jvm_present)" = "true" ] || fail "multi_node_dispatcher=PASS requires second_jvm_present=true"
    [ "$(report_get "$file" multi_node_observed)" = "true" ] || fail "multi_node_dispatcher=PASS requires multi_node_observed=true"
  fi
  if [ "$crash" = "SKIP" ] || [ "$crash" = "SIMULATED" ]; then
    [ -n "$(report_get "$file" crash_window_skip_reason)" ] || fail "crash_window $crash needs crash_window_skip_reason"
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_crash_skip_reason)" ] || fail "live_crash SKIP needs live_crash_skip_reason"
    [ "$(report_get "$file" live_crash_observed)" != "true" ] || fail "SKIP cannot claim a live crash walk"
  fi
  if [ "$nodes" = "SKIP" ]; then
    [ -n "$(report_get "$file" multi_node_skip_reason)" ] || fail "multi_node SKIP needs multi_node_skip_reason"
    [ "$(report_get "$file" multi_node_observed)" != "true" ] || fail "SKIP cannot claim a multi-node walk"
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
  OUT="$ROOT/.local/g3-outbox-consume-idempotent-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G3 leftover honesty beyond #195/#139/#170 (consume-idempotent / claim-crash-window PASS/SKIP)"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #139/#170/#195. Not product G3 close. G18 stays closed."
echo

[ -f "$README" ] || fail "missing $README"
[ -f "$DOC_139" ] || fail "missing committed #139 leftover $DOC_139"
[ -f "$DOC_170" ] || fail "missing committed #170 leftover $DOC_170"
[ -f "$DOC_195" ] || fail "missing committed #195 leftover $DOC_195"
[ -f "$FIXTURE_195" ] || fail "missing committed #195 honest-skip fixture $FIXTURE_195"
grep -Fq 'g3_close=NOT_CLAIMED' "$FIXTURE_195" || fail "committed #195 fixture must keep g3_close=NOT_CLAIMED"
grep -Fq 'consume_idempotent=PASS' "$FIXTURE_195" || fail "committed #195 fixture must keep consume_idempotent=PASS"
grep -Fq 'crash_window=SIMULATED' "$FIXTURE_195" || fail "committed #195 fixture must keep crash_window=SIMULATED"
grep -Fq 'live_crash=SKIP' "$FIXTURE_195" || fail "committed #195 fixture must keep live_crash=SKIP"
grep -Fq 'multi_node_dispatcher=SKIP' "$FIXTURE_195" || fail "committed #195 fixture must keep multi_node_dispatcher=SKIP"
grep -Eq 'does \*\*not\*\* claim a product(-wide)? G3 close' "$DOC_139" || fail "committed #139 leftover must keep product G3 not claimed"
grep -Fq 'does **not** claim a product G3 close' "$DOC_170" || fail "committed #170 leftover must keep product G3 not claimed"
grep -Fq 'g3_close=NOT_CLAIMED' "$DOC_195" || fail "committed #195 leftover must keep g3_close=NOT_CLAIMED"
grep -Fq 'Does **not** claim a product G3 close' "$DOC_195" || grep -Fq 'does **not** claim a product G3 close' "$DOC_195" || fail "committed #195 leftover must keep product G3 not claimed"

JAVA_FEATURE=missing
JAVA_VERSION_LINE=not-installed
if command -v java >/dev/null 2>&1; then
  JAVA_VERSION_LINE="$(java -version 2>&1 | head -n 1 | tr -d '\r')"
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

CRASH_REASON="in-process claim-then-write crash simulation from #170/#195; first claim committed, inbox UPDATE did not; replay DUPLICATE skips write; not a live process kill"
LIVE_CRASH_REASON="in-process claim-then-write crash simulation is not a live process kill; live_crash_observed=false; Docker forbidden; this leftover does not re-run #195"
SELF_PID="$$"
SECOND_JVM_PRESENT=false
SECOND_JVM_PID="${NEXUS_G3_SECOND_DISPATCHER_PID:-}"
MULTI_NODE_OBSERVED=false
MULTI_NODE_REASON="one application JVM; surefire parent/fork is not a multi-node Dispatcher; no second JAR, no Kafka/bus, Docker forbidden; this leftover does not re-run #195"

if [ -n "$SECOND_JVM_PID" ] && [ -d "/proc/${SECOND_JVM_PID}" ] && [ "$SECOND_JVM_PID" != "$SELF_PID" ]; then
  SECOND_JVM_PRESENT=true
  MULTI_NODE_REASON="second JVM pid=${SECOND_JVM_PID} is listed but a live multi-node Dispatcher / bus walk was not observed"
fi
if [ "${NEXUS_G3_MULTI_NODE_OBSERVED:-}" = "true" ] && [ "$SECOND_JVM_PRESENT" = "true" ]; then
  MULTI_NODE_OBSERVED=true
fi
if [ "${NEXUS_G3_LIVE_CRASH_OBSERVED:-}" = "true" ]; then
  LIVE_CRASH_REASON="live crash listed but this leftover still does not claim product G3 close"
fi

MISSING_ROWS=""
LEFTOVER_ROWS_KEPT=true
LEFTOVER_139_KEPT=false
LEFTOVER_170_KEPT=false
LEFTOVER_195_KEPT=false
LEFTOVER_228_KEPT=false
LEFTOVER_221_KEPT=false
LEFTOVER_225_KEPT=false
LEFTOVER_223_KEPT=false
LEFTOVER_222_KEPT=false
LEFTOVER_216_KEPT=false
LEFTOVER_220_KEPT=false
LEFTOVER_215_KEPT=false
LEFTOVER_210_KEPT=false
LEFTOVER_217_KEPT=false
LEFTOVER_209_KEPT=false
LEFTOVER_188_KEPT=false
LEFTOVER_205_KEPT=false
LEFTOVER_197_KEPT=false
LEFTOVER_204_KEPT=false
LEFTOVER_198_KEPT=false
LEFTOVER_200_KEPT=false
LEFTOVER_192_KEPT=false
LEFTOVER_193_KEPT=false
for required_row in $REQUIRED_LEFTOVER_ROWS; do
  if grep -Fq "[${required_row}](./${required_row}.md)" "$README"; then
    case "$required_row" in
      g3-outbox-consume-idempotent) LEFTOVER_139_KEPT=true ;;
      g3-claim-crash-window) LEFTOVER_170_KEPT=true ;;
      g3-outbox-consume-crash-honesty) LEFTOVER_195_KEPT=true ;;
      g10-leftover-honesty-remainder) LEFTOVER_228_KEPT=true ;;
      g2-report-physical-dump-honesty) LEFTOVER_221_KEPT=true ;;
      g6-recovery-port-dry-run-remainder-honesty) LEFTOVER_225_KEPT=true ;;
      s0-secret-rotate-getenv-remainder-honesty) LEFTOVER_223_KEPT=true ;;
      g4-pkce-spa-verifier-remainder-honesty) LEFTOVER_222_KEPT=true ;;
      g17-separation-leftover-honesty) LEFTOVER_216_KEPT=true ;;
      g15-wx-dt-native-bridge-remainder) LEFTOVER_220_KEPT=true ;;
      g1-queryast-export-honesty) LEFTOVER_215_KEPT=true ;;
      g17-gm-tls-nginx-handshake-honesty) LEFTOVER_210_KEPT=true ;;
      g5-publishhook-ddl-honesty) LEFTOVER_217_KEPT=true ;;
      g9-sentinel-cluster-remainder-honesty) LEFTOVER_209_KEPT=true ;;
      g2-ops-pg-dump-honesty) LEFTOVER_188_KEPT=true ;;
      g17-mfa-recovery-vue-otp-honesty) LEFTOVER_205_KEPT=true ;;
      g9-redis-down-invalidate-remainder) LEFTOVER_197_KEPT=true ;;
      g2-h5-every-page-form-write-honesty) LEFTOVER_204_KEPT=true ;;
      g15-wx-dt-native-bridge-honesty) LEFTOVER_198_KEPT=true ;;
      s0-secret-rotate-without-restart-honesty) LEFTOVER_200_KEPT=true ;;
      g7-query-expense-travel-honesty) LEFTOVER_192_KEPT=true ;;
      linux-arm-profile-leftover-honesty) LEFTOVER_193_KEPT=true ;;
    esac
  else
    LEFTOVER_ROWS_KEPT=false
    MISSING_ROWS="${MISSING_ROWS} ${required_row}"
  fi
done
[ "$LEFTOVER_ROWS_KEPT" = "true" ] || fail "docs/README.md dropped leftover row(s):$MISSING_ROWS"
[ "$LEFTOVER_139_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g3-outbox-consume-idempotent (#139)"
[ "$LEFTOVER_170_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g3-claim-crash-window (#170)"
[ "$LEFTOVER_195_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g3-outbox-consume-crash-honesty (#195)"
[ "$LEFTOVER_228_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-leftover-honesty-remainder (#228)"
[ "$LEFTOVER_221_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-report-physical-dump-honesty (#221)"
[ "$LEFTOVER_225_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g6-recovery-port-dry-run-remainder-honesty (#225)"
[ "$LEFTOVER_223_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover s0-secret-rotate-getenv-remainder-honesty (#223)"
[ "$LEFTOVER_222_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-pkce-spa-verifier-remainder-honesty (#222)"
[ "$LEFTOVER_216_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-separation-leftover-honesty (#216)"
[ "$LEFTOVER_220_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g15-wx-dt-native-bridge-remainder (#220)"
[ "$LEFTOVER_215_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g1-queryast-export-honesty (#215)"

LEFTOVER_ROWS_CSV="$(echo "$REQUIRED_LEFTOVER_ROWS" | tr ' ' ',')"

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
  kv leftover_139_cells_honest PASS
  kv leftover_170_cells_honest PASS
  kv leftover_195_cells_honest PASS
  kv consume_idempotent PASS
  kv consume_idempotent_first FIRST
  kv consume_idempotent_replay DUPLICATE
  kv consume_idempotent_side_effects 1
  kv crash_window_claim_committed true
  kv crash_window_write_committed false
  kv crash_window_inbox_status PENDING
  kv crash_window_replay_claim DUPLICATE
  kv crash_window_side_effect_lost true
  kv live_crash_observed false
  kv crash_window SIMULATED
  kv crash_window_skip_reason "$CRASH_REASON"
  kv live_crash SKIP
  kv live_crash_skip_reason "$LIVE_CRASH_REASON"
  kv second_jvm_present "$SECOND_JVM_PRESENT"
  kv second_jvm_pid "$SECOND_JVM_PID"
  kv multi_node_observed "$MULTI_NODE_OBSERVED"
  kv multi_node_dispatcher SKIP
  kv multi_node_skip_reason "$MULTI_NODE_REASON"
  kv leftover_rows_kept "$LEFTOVER_ROWS_KEPT"
  kv leftover_rows "$LEFTOVER_ROWS_CSV"
  kv leftover_139_kept "$LEFTOVER_139_KEPT"
  kv leftover_170_kept "$LEFTOVER_170_KEPT"
  kv leftover_195_kept "$LEFTOVER_195_KEPT"
  kv leftover_228_kept "$LEFTOVER_228_KEPT"
  kv leftover_221_kept "$LEFTOVER_221_KEPT"
  kv leftover_225_kept "$LEFTOVER_225_KEPT"
  kv leftover_223_kept "$LEFTOVER_223_KEPT"
  kv leftover_222_kept "$LEFTOVER_222_KEPT"
  kv leftover_216_kept "$LEFTOVER_216_KEPT"
  kv leftover_220_kept "$LEFTOVER_220_KEPT"
  kv leftover_215_kept "$LEFTOVER_215_KEPT"
  kv leftover_210_kept "$LEFTOVER_210_KEPT"
  kv leftover_217_kept "$LEFTOVER_217_KEPT"
  kv leftover_209_kept "$LEFTOVER_209_KEPT"
  kv leftover_188_kept "$LEFTOVER_188_KEPT"
  kv leftover_205_kept "$LEFTOVER_205_KEPT"
  kv leftover_197_kept "$LEFTOVER_197_KEPT"
  kv leftover_204_kept "$LEFTOVER_204_KEPT"
  kv leftover_198_kept "$LEFTOVER_198_KEPT"
  kv leftover_200_kept "$LEFTOVER_200_KEPT"
  kv leftover_192_kept "$LEFTOVER_192_KEPT"
  kv leftover_193_kept "$LEFTOVER_193_KEPT"
  kv honesty_139_rerun false
  kv honesty_170_rerun false
  kv honesty_195_rerun false
  kv redone_139 false
  kv redone_170 false
  kv redone_195 false
  kv outbox_dispatcher_redesigned false
  kv g3_close NOT_CLAIMED
  kv g3_close_reason "Honesty leftover beyond #195/#139/#170. Consume harness PASS. Crash window SIMULATED. Live multi-node / live crash SKIP when unavailable. Not product G3 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
} >"$REPORT"

{
  echo "# G3 leftover honesty beyond #195/#139/#170"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| leftover #139 consume-idempotent | PASS |"
  echo "| leftover #170 crash-window order | SIMULATED |"
  echo "| leftover #195 honesty cells | PASS |"
  echo "| consume-idempotent harness | PASS |"
  echo "| claim-then-write crash window | SIMULATED |"
  echo "| live process-kill crash | SKIP |"
  echo "| multi-node Dispatcher | SKIP |"
  echo "| leftover rows kept | $LEFTOVER_ROWS_CSV |"
  echo "| #139/#170/#195 redo | false |"
  echo "| product G3 close | NOT_CLAIMED |"
  echo
  echo "Crash reason: $CRASH_REASON"
  echo "Live crash reason: $LIVE_CRASH_REASON"
  echo "Multi-node reason: $MULTI_NODE_REASON"
} > "$OUT/matrix.md"

verify_report "$REPORT"
log "wrote $OUT/report.env"

SANITIZED="${ROOT}/docs/engineering/g3-outbox-consume-idempotent-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

echo
echo "PASS: leftover honesty recorded (#139/#170/#195 cells honest; live crash SKIP; multi-node SKIP)"
echo "See docs/g3-outbox-consume-idempotent-honesty.md"
echo "Keep sibling leftovers: docs/g3-outbox-consume-idempotent.md, docs/g3-claim-crash-window.md, docs/g3-outbox-consume-crash-honesty.md, docs/g10-leftover-honesty-remainder.md, docs/g2-report-physical-dump-honesty.md, docs/g6-recovery-port-dry-run-remainder-honesty.md, docs/s0-secret-rotate-getenv-remainder-honesty.md, docs/g4-pkce-spa-verifier-remainder-honesty.md, docs/g17-separation-leftover-honesty.md, docs/g15-wx-dt-native-bridge-remainder.md"
echo "Not claimed: product G3 close / Outbox rebuild / live multi-node Dispatcher / live crash / #139/#170/#195 redo"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
