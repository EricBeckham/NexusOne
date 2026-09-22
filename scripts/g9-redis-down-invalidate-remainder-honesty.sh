#!/usr/bin/env bash
# G9 leftover remainder honesty beyond #197 / #174 / #83 / #121:
# Redis-down / PolicySnapshot invalidate PASS/SKIP.
# Does not redo #83 / #100 / #121 / #128 / #148 / #161 / #174 / #197.
# Does not claim product G9 close. G18 stays closed. No Docker.
# No P0/M0 redo. AuditPort same-TX untouched. PolicySnapshot not redesigned.
#
# Parent leftover remainder / honesty fixtures stay PASS.
# Live Redis process-down / live invalidate are PASS only when present AND
# observed. Missing listener / unobserved walk is honest SKIP.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""

README="$ROOT/docs/README.md"
DOC_83="$ROOT/docs/g9-policysnapshot-invalidate.md"
DOC_121="$ROOT/docs/g9-failover-remainder.md"
DOC_174="$ROOT/docs/g9-redis-down-invalidate-honesty.md"
DOC_197="$ROOT/docs/g9-redis-down-invalidate-remainder.md"
FIXTURE_174="$ROOT/scripts/g9-redis-down-invalidate-honesty/fixtures/honest-skip.env"
FIXTURE_197="$ROOT/scripts/g9-redis-down-invalidate-remainder/fixtures/honest-skip.env"
REMAINDER_SRC="$ROOT/nexus-permission-core/src/test/java/one/nexus/permission/redis/G9RedisDownInvalidateRemainder.java"

REQUIRED_LEFTOVER_ROWS="g9-policysnapshot-invalidate g9-failover-remainder g9-redis-down-invalidate-honesty g9-redis-down-invalidate-remainder g9-redis-down-invalidate-remainder-honesty g9-multinode-partition g9-sentinel-cluster-honesty g9-sentinel-cluster-remainder-honesty g9-live-multi-jvm-replica g9-live-multi-jvm-replica-remainder-honesty g4-hot-purge-staging-leftover-honesty g7-withdraw-browser-evidence-honesty g2-h5-every-page-form-write-remainder-honesty g3-outbox-consume-idempotent-honesty g3-outbox-consume-crash-honesty g3-outbox-consume-idempotent g3-claim-crash-window g10-leftover-honesty-remainder g10-leftover-honesty g10-file-reconcile-honesty g10-identity-drift-honesty g2-report-physical-dump-honesty g6-recovery-port-dry-run-remainder-honesty s0-secret-rotate-getenv-remainder-honesty g4-pkce-spa-verifier-remainder-honesty g17-separation-leftover-honesty g15-wx-dt-native-bridge-remainder g1-queryast-export-honesty g17-gm-tls-nginx-handshake-honesty g5-publishhook-ddl-honesty g2-ops-pg-dump-honesty g17-mfa-recovery-vue-otp-honesty g2-h5-every-page-form-write-honesty g4-export-wizard-deny-honesty g5-mid-publish-failure-honesty g15-wx-dt-native-bridge-honesty s0-secret-rotate-without-restart-honesty g7-query-expense-travel-honesty linux-arm-profile-leftover-honesty g4-p1-staging-audit-events-honesty g6-recovery-port-dry-run-honesty s0s1-live-redis-minio-isolation-honesty g17-separation-honesty g4-hot-purge-90d-honesty g1-fieldpolicy-mask-second-auth-honesty g1-queryast-boundsql-honesty g15-native-bridge-honesty g4-pkce-spa-verifier-honesty s0-secret-getenv-snapshot-honesty g5-browser-failed-publish g7-withdraw-browser-honesty"

usage() {
  cat <<'EOF'
Usage:
  scripts/g9-redis-down-invalidate-remainder-honesty.sh [--out DIR]
  scripts/g9-redis-down-invalidate-remainder-honesty.sh --verify-report FILE

Beyond #197/#174/#83/#121: leftover remainder / honesty cells stay
honest; parent leftover fixtures stay PASS. Live Redis-down / live
PolicySnapshot invalidate are PASS only when Redis is present AND the
walk was observed. Missing listener is honest SKIP. Does not re-run
#83/#121/#174/#197. Does not claim product G9 close.

  --out DIR           Output directory (default: .local/g9-redis-down-invalidate-remainder-honesty/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_83_cells_honest leftover_121_cells_honest leftover_174_cells_honest leftover_197_cells_honest leftover_174_honesty_gate leftover_197_honesty_gate leftover_rows_kept leftover_rows leftover_83_kept leftover_121_kept leftover_174_kept leftover_197_kept leftover_226_kept leftover_228_kept leftover_229_kept leftover_230_kept leftover_212_kept leftover_232_kept leftover_221_kept leftover_225_kept leftover_223_kept leftover_222_kept leftover_216_kept leftover_220_kept leftover_215_kept leftover_209_kept leftover_195_kept dead_client_harness simulated_dead_client_port dead_client_is_not_live_down redis_present redis_role redis_down_observed live_redis_down live_redis_down_skip_reason live_invalidate_observed live_invalidate live_invalidate_skip_reason leftover_197_redone leftover_174_redone leftover_83_redone leftover_121_redone g9_83_100_121_128_148_161_174_197_redone honesty_197_rerun honesty_174_rerun g9_close g18_status audit_port_changed policy_snapshot_redesigned p0_m0_redone"
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
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || fail "p0_m0_redone must stay false"
  [ "$(report_get "$file" leftover_83_redone)" = "false" ] || fail "must not redo #83"
  [ "$(report_get "$file" leftover_121_redone)" = "false" ] || fail "must not redo #121"
  [ "$(report_get "$file" leftover_174_redone)" = "false" ] || fail "must not redo #174"
  [ "$(report_get "$file" leftover_197_redone)" = "false" ] || fail "must not redo #197"
  [ "$(report_get "$file" g9_83_100_121_128_148_161_174_197_redone)" = "false" ] || fail "must not redo #83/#100/#121/#128/#148/#161/#174/#197"
  [ "$(report_get "$file" honesty_197_rerun)" = "false" ] || fail "honesty_197_rerun must be false (do not redo #197 collect)"
  [ "$(report_get "$file" honesty_174_rerun)" = "false" ] || fail "honesty_174_rerun must be false (do not redo #174 collect)"
  [ "$(report_get "$file" g9_close)" = "NOT_CLAIMED" ] || fail "g9_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" leftover_83_cells_honest)" = "PASS" ] || fail "leftover_83_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_121_cells_honest)" = "PASS" ] || fail "leftover_121_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_174_cells_honest)" = "PASS" ] || fail "leftover_174_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_197_cells_honest)" = "PASS" ] || fail "leftover_197_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_174_honesty_gate)" = "PASS" ] || fail "leftover_174_honesty_gate must be PASS"
  [ "$(report_get "$file" leftover_197_honesty_gate)" = "PASS" ] || fail "leftover_197_honesty_gate must be PASS"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" leftover_83_kept)" = "true" ] || fail "leftover_83_kept must stay true"
  [ "$(report_get "$file" leftover_121_kept)" = "true" ] || fail "leftover_121_kept must stay true"
  [ "$(report_get "$file" leftover_174_kept)" = "true" ] || fail "leftover_174_kept must stay true"
  [ "$(report_get "$file" leftover_197_kept)" = "true" ] || fail "leftover_197_kept must stay true"
  [ "$(report_get "$file" leftover_226_kept)" = "true" ] || fail "leftover_226_kept must stay true (keep g3-outbox-consume-idempotent-honesty)"
  [ "$(report_get "$file" leftover_228_kept)" = "true" ] || fail "leftover_228_kept must stay true (keep g10-leftover-honesty-remainder)"
  [ "$(report_get "$file" leftover_229_kept)" = "true" ] || fail "leftover_229_kept must stay true (keep g9-live-multi-jvm-replica-remainder-honesty)"
  [ "$(report_get "$file" leftover_230_kept)" = "true" ] || fail "leftover_230_kept must stay true (keep g4-hot-purge-staging-leftover-honesty)"
  [ "$(report_get "$file" leftover_212_kept)" = "true" ] || fail "leftover_212_kept must stay true (keep g7-withdraw-browser-evidence-honesty)"
  [ "$(report_get "$file" leftover_232_kept)" = "true" ] || fail "leftover_232_kept must stay true (keep g2-h5-every-page-form-write-remainder-honesty)"
  [ "$(report_get "$file" leftover_221_kept)" = "true" ] || fail "leftover_221_kept must stay true"
  [ "$(report_get "$file" leftover_225_kept)" = "true" ] || fail "leftover_225_kept must stay true"
  [ "$(report_get "$file" leftover_223_kept)" = "true" ] || fail "leftover_223_kept must stay true"
  [ "$(report_get "$file" leftover_222_kept)" = "true" ] || fail "leftover_222_kept must stay true"
  [ "$(report_get "$file" leftover_216_kept)" = "true" ] || fail "leftover_216_kept must stay true"
  [ "$(report_get "$file" leftover_220_kept)" = "true" ] || fail "leftover_220_kept must stay true"
  [ "$(report_get "$file" leftover_215_kept)" = "true" ] || fail "leftover_215_kept must stay true"
  [ "$(report_get "$file" leftover_209_kept)" = "true" ] || fail "leftover_209_kept must stay true"
  [ "$(report_get "$file" leftover_195_kept)" = "true" ] || fail "leftover_195_kept must stay true"
  [ "$(report_get "$file" dead_client_harness)" = "PASS" ] || fail "dead_client_harness must be PASS"
  [ "$(report_get "$file" dead_client_is_not_live_down)" = "true" ] || fail "dead_client_is_not_live_down must be true"
  [ "$(report_get "$file" simulated_dead_client_port)" = "1" ] || fail "simulated_dead_client_port must be 1"

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $REQUIRED_LEFTOVER_ROWS; do
    echo "$rows" | grep -Fq "$required_row" || fail "leftover_rows must keep $required_row"
  done

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
  OUT="$ROOT/.local/g9-redis-down-invalidate-remainder-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G9 Redis-down / PolicySnapshot invalidate remainder honesty beyond #197/#174/#83/#121"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #83/#100/#121/#128/#148/#161/#174/#197."
echo "Dead-client harness PASS is not live Redis-down."
echo "Not claimed: product G9 close / live Redis process-down / live invalidate"
echo

[ -f "$DOC_83" ] || fail "missing $DOC_83"
[ -f "$DOC_121" ] || fail "missing $DOC_121"
[ -f "$DOC_174" ] || fail "missing $DOC_174"
[ -f "$DOC_197" ] || fail "missing $DOC_197"
[ -f "$FIXTURE_174" ] || fail "missing $FIXTURE_174"
[ -f "$FIXTURE_197" ] || fail "missing $FIXTURE_197"
[ -f "$README" ] || fail "missing $README"
[ -f "$REMAINDER_SRC" ] || fail "missing leftover remainder source $REMAINDER_SRC"

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

log "verify parent #197 leftover remainder fixtures (harness PASS; do not re-run collect)"
./scripts/g9-redis-down-invalidate-remainder.sh --verify-report \
  scripts/g9-redis-down-invalidate-remainder/fixtures/honest-skip.env | tee -a "$OUT/collect.log"
set +e
./scripts/g9-redis-down-invalidate-remainder.sh --verify-report \
  scripts/g9-redis-down-invalidate-remainder/fixtures/fake-live-redis-down-pass.env >"$OUT/fake-197-redis-down.log" 2>&1
FAKE197_DOWN=$?
./scripts/g9-redis-down-invalidate-remainder.sh --verify-report \
  scripts/g9-redis-down-invalidate-remainder/fixtures/fake-live-invalidate-pass.env >"$OUT/fake-197-invalidate.log" 2>&1
FAKE197_INV=$?
./scripts/g9-redis-down-invalidate-remainder.sh --verify-report \
  scripts/g9-redis-down-invalidate-remainder/fixtures/fake-g9-close.env >"$OUT/fake-197-close.log" 2>&1
FAKE197_CLOSE=$?
set -e
[ "$FAKE197_DOWN" -ne 0 ] || fail "expected #197 fake-live-redis-down-pass.env to FAIL"
[ "$FAKE197_INV" -ne 0 ] || fail "expected #197 fake-live-invalidate-pass.env to FAIL"
[ "$FAKE197_CLOSE" -ne 0 ] || fail "expected #197 fake-g9-close.env to FAIL"
log "parent #197 leftover remainder honesty gate PASS (honest SKIP accepted; fakes rejected)"

log "verify parent #174 leftover honesty fixtures (harness PASS; do not re-run collect)"
./scripts/g9-redis-down-invalidate-honesty.sh --verify-report \
  scripts/g9-redis-down-invalidate-honesty/fixtures/honest-skip.env | tee -a "$OUT/collect.log"
set +e
./scripts/g9-redis-down-invalidate-honesty.sh --verify-report \
  scripts/g9-redis-down-invalidate-honesty/fixtures/fake-live-redis-down-pass.env >"$OUT/fake-174-redis-down.log" 2>&1
FAKE174_DOWN=$?
./scripts/g9-redis-down-invalidate-honesty.sh --verify-report \
  scripts/g9-redis-down-invalidate-honesty/fixtures/fake-live-invalidate-pass.env >"$OUT/fake-174-invalidate.log" 2>&1
FAKE174_INV=$?
./scripts/g9-redis-down-invalidate-honesty.sh --verify-report \
  scripts/g9-redis-down-invalidate-honesty/fixtures/fake-g9-close.env >"$OUT/fake-174-close.log" 2>&1
FAKE174_CLOSE=$?
set -e
[ "$FAKE174_DOWN" -ne 0 ] || fail "expected #174 fake-live-redis-down-pass.env to FAIL"
[ "$FAKE174_INV" -ne 0 ] || fail "expected #174 fake-live-invalidate-pass.env to FAIL"
[ "$FAKE174_CLOSE" -ne 0 ] || fail "expected #174 fake-g9-close.env to FAIL"
log "parent #174 leftover honesty gate PASS (honest SKIP accepted; fakes rejected)"

DEAD_CLIENT_HARNESS=FAIL
if ! port_open 127.0.0.1 1 \
  && grep -Fq 'proveDeadClientHarness' "$REMAINDER_SRC" \
  && grep -Fq 'lastRemoteNotifySucceeded' "$REMAINDER_SRC" \
  && [ "$(report_get "$FIXTURE_197" dead_client_harness)" = "PASS" ] \
  && [ "$(report_get "$FIXTURE_197" dead_client_is_not_live_down)" = "true" ]; then
  DEAD_CLIENT_HARNESS=PASS
fi
[ "$DEAD_CLIENT_HARNESS" = "PASS" ] || fail "dead-client harness inventory must PASS (port 1 dead + leftover source + #197 fixture)"

MISSING_ROWS=""
LEFTOVER_ROWS_KEPT=true
LEFTOVER_83_KEPT=false
LEFTOVER_121_KEPT=false
LEFTOVER_174_KEPT=false
LEFTOVER_197_KEPT=false
LEFTOVER_226_KEPT=false
LEFTOVER_228_KEPT=false
LEFTOVER_229_KEPT=false
LEFTOVER_230_KEPT=false
LEFTOVER_212_KEPT=false
LEFTOVER_232_KEPT=false
LEFTOVER_221_KEPT=false
LEFTOVER_225_KEPT=false
LEFTOVER_223_KEPT=false
LEFTOVER_222_KEPT=false
LEFTOVER_216_KEPT=false
LEFTOVER_220_KEPT=false
LEFTOVER_215_KEPT=false
LEFTOVER_209_KEPT=false
LEFTOVER_195_KEPT=false
for required_row in $REQUIRED_LEFTOVER_ROWS; do
  if grep -Fq "[${required_row}](./${required_row}.md)" "$README"; then
    case "$required_row" in
      g9-policysnapshot-invalidate) LEFTOVER_83_KEPT=true ;;
      g9-failover-remainder) LEFTOVER_121_KEPT=true ;;
      g9-redis-down-invalidate-honesty) LEFTOVER_174_KEPT=true ;;
      g9-redis-down-invalidate-remainder) LEFTOVER_197_KEPT=true ;;
      g3-outbox-consume-idempotent-honesty) LEFTOVER_226_KEPT=true ;;
      g10-leftover-honesty-remainder) LEFTOVER_228_KEPT=true ;;
      g9-live-multi-jvm-replica-remainder-honesty) LEFTOVER_229_KEPT=true ;;
      g4-hot-purge-staging-leftover-honesty) LEFTOVER_230_KEPT=true ;;
      g7-withdraw-browser-evidence-honesty) LEFTOVER_212_KEPT=true ;;
      g2-h5-every-page-form-write-remainder-honesty) LEFTOVER_232_KEPT=true ;;
      g2-report-physical-dump-honesty) LEFTOVER_221_KEPT=true ;;
      g6-recovery-port-dry-run-remainder-honesty) LEFTOVER_225_KEPT=true ;;
      s0-secret-rotate-getenv-remainder-honesty) LEFTOVER_223_KEPT=true ;;
      g4-pkce-spa-verifier-remainder-honesty) LEFTOVER_222_KEPT=true ;;
      g17-separation-leftover-honesty) LEFTOVER_216_KEPT=true ;;
      g15-wx-dt-native-bridge-remainder) LEFTOVER_220_KEPT=true ;;
      g1-queryast-export-honesty) LEFTOVER_215_KEPT=true ;;
      g9-sentinel-cluster-remainder-honesty) LEFTOVER_209_KEPT=true ;;
      g3-outbox-consume-crash-honesty) LEFTOVER_195_KEPT=true ;;
    esac
  else
    LEFTOVER_ROWS_KEPT=false
    MISSING_ROWS="${MISSING_ROWS} ${required_row}"
  fi
done
[ "$LEFTOVER_ROWS_KEPT" = "true" ] || fail "docs/README.md dropped leftover row(s):$MISSING_ROWS"
[ "$LEFTOVER_83_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-policysnapshot-invalidate (#83)"
[ "$LEFTOVER_121_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-failover-remainder (#121)"
[ "$LEFTOVER_174_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-redis-down-invalidate-honesty (#174)"
[ "$LEFTOVER_197_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-redis-down-invalidate-remainder (#197)"
[ "$LEFTOVER_226_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g3-outbox-consume-idempotent-honesty (#226)"
[ "$LEFTOVER_228_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-leftover-honesty-remainder (#228)"
[ "$LEFTOVER_229_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-live-multi-jvm-replica-remainder-honesty (#229)"
[ "$LEFTOVER_230_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-hot-purge-staging-leftover-honesty (#230)"
[ "$LEFTOVER_212_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g7-withdraw-browser-evidence-honesty (#212)"
[ "$LEFTOVER_232_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-h5-every-page-form-write-remainder-honesty (#232)"
if grep -Eiq '产品 G9 已关闭|product g9 closed|g9_close=CLOSED' "$README"; then
  fail "docs/README.md must not claim product G9 closed"
fi

REDIS_PRESENT=false
REDIS_ROLE=""
DOWN_REASON="no Redis listener on 127.0.0.1:6379; dead-client harness PASS is not live Redis-down; live invalidate not observed; Docker forbidden; this leftover remainder honesty does not start/stop Redis and does not re-run #83/#121/#174/#197"
INVALIDATE_REASON="$DOWN_REASON"

if command -v redis-cli >/dev/null 2>&1; then
  if redis-cli -h 127.0.0.1 -p 6379 PING 2>/dev/null | grep -qx PONG; then
    REDIS_PRESENT=true
    REDIS_ROLE="$(redis-cli -h 127.0.0.1 -p 6379 INFO replication 2>/dev/null | awk -F: '/^role:/{print $2}' | tr -d '\r')"
    DOWN_REASON="Redis is listening on 127.0.0.1:6379 (role=${REDIS_ROLE:-unknown}) but a live Redis process-down was not observed; dead-client harness PASS is not live Redis-down; Docker forbidden; this leftover does not re-run #83/#121/#174/#197"
    INVALIDATE_REASON="Redis is listening on 127.0.0.1:6379 but a live PUBLISH/SUBSCRIBE invalidate walk was not observed; RedisInvalidationTest skip / #174/#197 inventory is not a live invalidate PASS; this leftover does not re-run #174/#197"
  fi
elif port_open 127.0.0.1 6379; then
  REDIS_PRESENT=true
  DOWN_REASON="Redis listener present on 127.0.0.1:6379 but a live Redis process-down was not observed; dead-client harness PASS is not live Redis-down; Docker forbidden; this leftover does not re-run #83/#121/#174/#197"
  INVALIDATE_REASON="Redis listener present on 127.0.0.1:6379 but a live PUBLISH/SUBSCRIBE invalidate walk was not observed; this leftover does not re-run #174/#197"
fi

# Honesty leftover remainder never fabricates a live walk. #83/#121/#174/#197 scripts are not re-run.
LIVE_REDIS_DOWN=SKIP
REDIS_DOWN_OBSERVED=false
LIVE_INVALIDATE=SKIP
LIVE_INVALIDATE_OBSERVED=false

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
  kv leftover_83_cells_honest PASS
  kv leftover_121_cells_honest PASS
  kv leftover_174_cells_honest PASS
  kv leftover_197_cells_honest PASS
  kv leftover_174_honesty_gate PASS
  kv leftover_197_honesty_gate PASS
  kv leftover_rows_kept "$LEFTOVER_ROWS_KEPT"
  kv leftover_rows "$LEFTOVER_ROWS_CSV"
  kv leftover_83_kept "$LEFTOVER_83_KEPT"
  kv leftover_121_kept "$LEFTOVER_121_KEPT"
  kv leftover_174_kept "$LEFTOVER_174_KEPT"
  kv leftover_197_kept "$LEFTOVER_197_KEPT"
  kv leftover_226_kept "$LEFTOVER_226_KEPT"
  kv leftover_228_kept "$LEFTOVER_228_KEPT"
  kv leftover_229_kept "$LEFTOVER_229_KEPT"
  kv leftover_230_kept "$LEFTOVER_230_KEPT"
  kv leftover_212_kept "$LEFTOVER_212_KEPT"
  kv leftover_232_kept "$LEFTOVER_232_KEPT"
  kv leftover_221_kept "$LEFTOVER_221_KEPT"
  kv leftover_225_kept "$LEFTOVER_225_KEPT"
  kv leftover_223_kept "$LEFTOVER_223_KEPT"
  kv leftover_222_kept "$LEFTOVER_222_KEPT"
  kv leftover_216_kept "$LEFTOVER_216_KEPT"
  kv leftover_220_kept "$LEFTOVER_220_KEPT"
  kv leftover_215_kept "$LEFTOVER_215_KEPT"
  kv leftover_209_kept "$LEFTOVER_209_KEPT"
  kv leftover_195_kept "$LEFTOVER_195_KEPT"
  kv honesty_197_rerun false
  kv honesty_174_rerun false
  kv dead_client_harness "$DEAD_CLIENT_HARNESS"
  kv simulated_dead_client_port 1
  kv dead_client_is_not_live_down true
  kv redis_present "$REDIS_PRESENT"
  kv redis_role "$REDIS_ROLE"
  kv redis_down_observed "$REDIS_DOWN_OBSERVED"
  kv live_redis_down "$LIVE_REDIS_DOWN"
  kv live_redis_down_skip_reason "$DOWN_REASON"
  kv live_invalidate_observed "$LIVE_INVALIDATE_OBSERVED"
  kv live_invalidate "$LIVE_INVALIDATE"
  kv live_invalidate_skip_reason "$INVALIDATE_REASON"
  kv leftover_197_redone false
  kv leftover_174_redone false
  kv leftover_83_redone false
  kv leftover_121_redone false
  kv g9_83_100_121_128_148_161_174_197_redone false
  kv g9_close NOT_CLAIMED
  kv g9_close_reason "Honesty leftover remainder beyond #197/#174/#83/#121. Dead-client harness stays PASS. Parent leftover fixtures stay PASS. Live Redis-down / live invalidate SKIP when unobserved. Not product G9 close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv policy_snapshot_redesigned false
  kv p0_m0_redone false
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "# G9 Redis-down / PolicySnapshot invalidate remainder honesty beyond #197/#174/#83/#121"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| leftover #83 cells still honest | PASS |"
  echo "| leftover #121 cells still honest | PASS |"
  echo "| leftover #174 cells still honest | PASS |"
  echo "| leftover #197 cells still honest | PASS |"
  echo "| #174 leftover honesty fixtures | PASS |"
  echo "| #197 leftover remainder fixtures | PASS |"
  echo "| dead-client harness (port 1) | PASS |"
  echo "| live Redis-down | SKIP |"
  echo "| live invalidate | SKIP |"
  echo "| dead client port 1 is live Redis-down | false |"
  echo "| leftover rows kept | $LEFTOVER_ROWS_CSV |"
  echo "| #83/#121/#174/#197 redo | false |"
  echo "| product G9 close | NOT_CLAIMED |"
  echo
  echo "Redis-down reason: $DOWN_REASON"
  echo "Invalidate reason: $INVALIDATE_REASON"
} >"$MATRIX"

verify_report "$REPORT"
log "wrote $OUT/report.env"

SANITIZED="${ROOT}/docs/engineering/g9-redis-down-invalidate-remainder-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

echo
echo "PASS: leftover remainder honesty recorded (#83/#121/#174/#197 cells honest; live Redis-down SKIP; live invalidate SKIP)"
echo "See docs/g9-redis-down-invalidate-remainder-honesty.md"
echo "Keep sibling leftovers: docs/g9-redis-down-invalidate-remainder.md, docs/g9-redis-down-invalidate-honesty.md, docs/g9-policysnapshot-invalidate.md, docs/g9-failover-remainder.md"
echo "Keep #226 g3-outbox-consume-idempotent-honesty, #228 g10-leftover-honesty-remainder, #229 g9-live-multi-jvm-replica-remainder-honesty, #230 g4-hot-purge-staging-leftover-honesty, #212 g7-withdraw-browser-evidence-honesty, #232 g2-h5-every-page-form-write-remainder-honesty"
echo "Not claimed: product G9 close / live Redis process-down / live invalidate / #83 / #121 / #174 / #197"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
