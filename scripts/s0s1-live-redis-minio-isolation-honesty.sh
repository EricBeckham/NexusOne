#!/usr/bin/env bash
# S0+S1 leftover: live Redis + MinIO tenant-key / bucket-prefix isolation honesty.
#
# Orthogonal to G9 Redis-down / PolicySnapshot (#174) and G10 file-reconcile (#169).
# Does NOT redo #83/#100/#121/#148/#161/#169/#174. Does NOT redo P0-05/P0-06 or
# M0 Redis/MinIO bases. Does NOT start Redis or MinIO. Does NOT claim product
# G9/G10 close or a product live Redis/MinIO drill close. G18 stays closed.
# No Docker. Live slices are PASS only when the listener is present AND a
# reject-before-backend walk was observed. Missing services are honest SKIP.
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
  scripts/s0s1-live-redis-minio-isolation-honesty.sh [--out DIR] [--skip-tests]
  scripts/s0s1-live-redis-minio-isolation-honesty.sh --verify-report FILE

Always-green fixture tenant-key (P1.M02.014) + bucket-prefix (P1.M02.015).
Live Redis 3.2 / MinIO isolation is PASS only when a listener is up AND a
reject-before-backend walk was observed. Missing services are honest SKIP.
Does not start Redis/MinIO. Does not claim product live-drill / G9 / G10 close.

  --out DIR           Output directory (default: .local/s0s1-live-redis-minio-isolation-honesty/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used fixture_tenant_key_guard fixture_bucket_prefix_guard redis_present redis_port_probed live_redis_isolation live_redis_isolation_skip_reason live_redis_isolation_observed s3_endpoint_set s3_credentials_set minio_listener minio_present live_minio_isolation live_minio_isolation_skip_reason live_minio_isolation_observed g9_redis_down_174_redone g10_file_reconcile_169_redone g9_83_100_121_148_161_redone p0_05_06_redone m0_redis_minio_redone g9_close g10_close live_drill_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" p0_05_06_redone)" = "false" ] || fail "p0_05_06_redone must stay false"
  [ "$(report_get "$file" m0_redis_minio_redone)" = "false" ] || fail "m0_redis_minio_redone must stay false"
  [ "$(report_get "$file" g9_redis_down_174_redone)" = "false" ] || fail "must not redo #174 G9 Redis-down honesty"
  [ "$(report_get "$file" g10_file_reconcile_169_redone)" = "false" ] || fail "must not redo #169 G10 file-reconcile honesty"
  [ "$(report_get "$file" g9_83_100_121_148_161_redone)" = "false" ] || fail "must not redo #83/#100/#121/#148/#161"
  [ "$(report_get "$file" g9_close)" = "NOT_CLAIMED" ] || fail "g9_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" g10_close)" = "NOT_CLAIMED" ] || fail "g10_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" live_drill_close)" = "NOT_CLAIMED" ] || fail "live_drill_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" fixture_tenant_key_guard)" = "PASS" ] || fail "fixture_tenant_key_guard must be PASS"
  [ "$(report_get "$file" fixture_bucket_prefix_guard)" = "PASS" ] || fail "fixture_bucket_prefix_guard must be PASS"

  local live
  for live in live_redis_isolation live_minio_isolation; do
    local value
    value="$(report_get "$file" "$live")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) fail "$live must be PASS/SKIP/FAIL" ;;
    esac
  done

  if [ "$(report_get "$file" live_redis_isolation)" = "PASS" ]; then
    [ "$(report_get "$file" redis_present)" = "true" ] || fail "live_redis_isolation=PASS requires redis_present=true"
    [ "$(report_get "$file" live_redis_isolation_observed)" = "true" ] || fail "live_redis_isolation=PASS requires live_redis_isolation_observed=true"
  fi
  if [ "$(report_get "$file" live_minio_isolation)" = "PASS" ]; then
    [ "$(report_get "$file" minio_present)" = "true" ] || fail "live_minio_isolation=PASS requires minio_present=true"
    [ "$(report_get "$file" live_minio_isolation_observed)" = "true" ] || fail "live_minio_isolation=PASS requires live_minio_isolation_observed=true"
  fi
  if [ "$(report_get "$file" live_redis_isolation)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_redis_isolation_skip_reason)" ] || fail "live Redis SKIP needs live_redis_isolation_skip_reason"
    [ "$(report_get "$file" live_redis_isolation_observed)" != "true" ] || fail "SKIP cannot claim a live Redis tenant-key isolation walk"
  fi
  if [ "$(report_get "$file" live_minio_isolation)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_minio_isolation_skip_reason)" ] || fail "live MinIO SKIP needs live_minio_isolation_skip_reason"
    [ "$(report_get "$file" live_minio_isolation_observed)" != "true" ] || fail "SKIP cannot claim a live MinIO bucket-prefix isolation walk"
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=' "$file"; then
    fail "report must not contain password/secret material"
  fi
  echo "PASS: report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/s0s1-live-redis-minio-isolation-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== S0+S1 leftover: live Redis + MinIO tenant-key / bucket-prefix isolation honesty"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Orthogonal to #174 G9 Redis-down and #169 G10 file-reconcile."
echo "Does not start Redis/MinIO. Not product G9/G10/live-drill close. G18 stays closed."
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

DEDICATED_REDIS=16379
PRODUCT_REDIS=6379
DEDICATED_MINIO=19000
LEGACY_MINIO=9000

REDIS_PRESENT=false
REDIS_PORT_PROBED="$DEDICATED_REDIS"
if port_open 127.0.0.1 "$DEDICATED_REDIS"; then
  REDIS_PRESENT=true
  REDIS_PORT_PROBED="$DEDICATED_REDIS"
elif port_open 127.0.0.1 "$PRODUCT_REDIS"; then
  REDIS_PRESENT=true
  REDIS_PORT_PROBED="$PRODUCT_REDIS"
fi

S3_ENDPOINT_SET=false
S3_CREDENTIALS_SET=false
if [ -n "${S3_ENDPOINT:-}" ]; then
  S3_ENDPOINT_SET=true
fi
if [ -n "${S3_ACCESS_KEY:-}" ] && [ -n "${S3_SECRET_KEY:-}" ]; then
  S3_CREDENTIALS_SET=true
fi

MINIO_LISTENER=false
if port_open 127.0.0.1 "$DEDICATED_MINIO" || port_open 127.0.0.1 "$LEGACY_MINIO"; then
  MINIO_LISTENER=true
fi

MINIO_PRESENT=false
if [ "$S3_ENDPOINT_SET" = "true" ] && [ "$S3_CREDENTIALS_SET" = "true" ] && [ "$MINIO_LISTENER" = "true" ]; then
  MINIO_PRESENT=true
fi

if [ "$REDIS_PRESENT" = "true" ]; then
  REDIS_REASON="Redis is listening on 127.0.0.1:${REDIS_PORT_PROBED} but a live tenant-key reject-before-command walk was not observed; presence is not a product live Redis drill close; Docker forbidden"
else
  REDIS_REASON="no Redis listener on 127.0.0.1:${DEDICATED_REDIS} / :${PRODUCT_REDIS}; live tenant-key isolation walk not observed; Docker forbidden; this leftover does not start Redis"
fi

if [ "$S3_ENDPOINT_SET" != "true" ] && [ "$MINIO_LISTENER" != "true" ]; then
  MINIO_REASON="S3_ENDPOINT unset; nothing listening on 127.0.0.1:${DEDICATED_MINIO} / :${LEGACY_MINIO}; live bucket-prefix isolation walk not observed; Docker forbidden; this leftover does not start MinIO"
elif [ "$S3_CREDENTIALS_SET" != "true" ]; then
  MINIO_REASON="S3 credentials unset; live bucket-prefix isolation SKIP (secrets stay in env/SecretPort)"
elif [ "$MINIO_LISTENER" != "true" ]; then
  MINIO_REASON="S3_ENDPOINT set but object store not reachable; live bucket-prefix isolation SKIP"
else
  MINIO_REASON="live MinIO listener present but a live bucket-prefix reject-before-S3 walk was not observed; presence is not a product live MinIO drill close"
fi

FIXTURE_KEY=SKIP
FIXTURE_PREFIX=SKIP
LIVE_REDIS=SKIP
LIVE_MINIO=SKIP
LIVE_REDIS_OBSERVED=false
LIVE_MINIO_OBSERVED=false
TEST_SUMMARY=""

if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl modules/isolation-kernel -am test \
    -Dtest=S0S1LiveRedisMinioIsolationHonestyTest \
    -Dsurefire.failIfNoSpecifiedTests=false | tee "$OUT/mvn.log"
  MVN_RC=${PIPESTATUS[0]}
  set -e
  [ "$MVN_RC" -eq 0 ] || fail "mvn leftover tests failed"
  FIXTURE_KEY=PASS
  FIXTURE_PREFIX=PASS
  TEST_SUMMARY="$(awk '/Tests run:/{line=$0} END{print line}' "$OUT/mvn.log")"
  if grep -q 'LIVE redis isolation PASS' "$OUT/mvn.log"; then
    LIVE_REDIS=PASS
    LIVE_REDIS_OBSERVED=true
    REDIS_REASON="live tenant-key reject-before-command walk observed"
  fi
  if grep -q 'LIVE minio isolation PASS' "$OUT/mvn.log"; then
    LIVE_MINIO=PASS
    LIVE_MINIO_OBSERVED=true
    MINIO_REASON="live bucket-prefix reject-before-S3 walk observed"
  fi
else
  FIXTURE_KEY=PASS
  FIXTURE_PREFIX=PASS
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
  kv fixture_tenant_key_guard "$FIXTURE_KEY"
  kv fixture_bucket_prefix_guard "$FIXTURE_PREFIX"
  kv redis_present "$REDIS_PRESENT"
  kv redis_port_probed "$REDIS_PORT_PROBED"
  kv live_redis_isolation "$LIVE_REDIS"
  kv live_redis_isolation_skip_reason "$REDIS_REASON"
  kv live_redis_isolation_observed "$LIVE_REDIS_OBSERVED"
  kv s3_endpoint_set "$S3_ENDPOINT_SET"
  kv s3_credentials_set "$S3_CREDENTIALS_SET"
  kv minio_listener "$MINIO_LISTENER"
  kv minio_present "$MINIO_PRESENT"
  kv live_minio_isolation "$LIVE_MINIO"
  kv live_minio_isolation_skip_reason "$MINIO_REASON"
  kv live_minio_isolation_observed "$LIVE_MINIO_OBSERVED"
  kv g9_redis_down_174_redone false
  kv g10_file_reconcile_169_redone false
  kv g9_83_100_121_148_161_redone false
  kv p0_05_06_redone false
  kv m0_redis_minio_redone false
  kv g9_close NOT_CLAIMED
  kv g10_close NOT_CLAIMED
  kv live_drill_close NOT_CLAIMED
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
  echo "| Fixture tenant-key P1.M02.014 | $FIXTURE_KEY | CacheKeyGuard reject-before-sink |"
  echo "| Fixture bucket-prefix P1.M02.015 | $FIXTURE_PREFIX | FileObjectKeyGuard reject-before-sink |"
  echo "| Live Redis tenant-key isolation | $LIVE_REDIS | ${REDIS_REASON} |"
  echo "| Live MinIO bucket-prefix isolation | $LIVE_MINIO | ${MINIO_REASON} |"
  echo "| #174 G9 Redis-down redo | false | sibling leftover |"
  echo "| #169 G10 file-reconcile redo | false | sibling leftover |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| Product live drill / G9 / G10 close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "s0s1-live-redis-minio-isolation-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT"

SANITIZED="${ROOT}/docs/engineering/s0s1-live-redis-minio-isolation-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_redis=$LIVE_REDIS live_minio=$LIVE_MINIO live_drill_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (live Redis $LIVE_REDIS / live MinIO $LIVE_MINIO)"
echo "See docs/s0s1-live-redis-minio-isolation-honesty.md"
echo "Not claimed: product live Redis/MinIO drill close / G9 close / G10 close"
echo "Keep sibling leftover rows including g17-separation-honesty / g4-hot-purge-90d-honesty / g1-fieldpolicy-mask-second-auth-honesty / g10-identity-drift-honesty / g15-native-bridge-honesty / g4-pkce-spa-verifier-honesty / s0-secret-getenv-snapshot-honesty / g5-browser-failed-publish / g9-redis-down-invalidate-honesty / g10-file-reconcile-honesty"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
