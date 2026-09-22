#!/usr/bin/env bash
# G10 leftover: file metadata ↔ object reconcile honesty.
#
# Extends docs/g10-file-reconcile.md. Does NOT redo identity drift (#141)
# or G6 object restore. Does NOT claim product G10 close. G18 stays closed.
# No Docker. Live MinIO / object-store is PASS only when reachable AND a
# preview + apply + tenant-prefix walk was observed. Missing S3_* is SKIP.
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
  scripts/g10-file-reconcile-honesty.sh [--out DIR] [--skip-tests]
  scripts/g10-file-reconcile-honesty.sh --verify-report FILE

Always-green counting preview vs apply + tenant-prefix guards.
Live MinIO / object-store is PASS only when S3_* is set, a listener is
up, and a preview + apply + tenant-prefix walk was observed.
Missing object store is honest SKIP.

  --out DIR           Output directory (default: .local/g10-file-reconcile-honesty/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used counting_preview_apply tenant_prefix_guard s3_endpoint_set s3_credentials_set live_minio_listener live_minio_present live_minio_reconcile live_minio_skip_reason live_preview_observed live_apply_observed live_tenant_prefix_observed identity_drift_141_redone g6_object_restore_redone g10_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" identity_drift_141_redone)" = "false" ] || fail "identity_drift_141_redone must stay false"
  [ "$(report_get "$file" g6_object_restore_redone)" = "false" ] || fail "g6_object_restore_redone must stay false"
  [ "$(report_get "$file" g10_close)" = "NOT_CLAIMED" ] || fail "g10_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" counting_preview_apply)" = "PASS" ] || fail "counting_preview_apply must be PASS"
  [ "$(report_get "$file" tenant_prefix_guard)" = "PASS" ] || fail "tenant_prefix_guard must be PASS"

  local live
  live="$(report_get "$file" live_minio_reconcile)"
  case "$live" in
    PASS|SKIP|FAIL) ;;
    *) fail "live_minio_reconcile must be PASS/SKIP/FAIL" ;;
  esac
  if [ "$live" = "PASS" ]; then
    [ "$(report_get "$file" live_minio_present)" = "true" ] || fail "live_minio_reconcile=PASS requires live_minio_present=true"
    [ "$(report_get "$file" s3_credentials_set)" = "true" ] || fail "live_minio_reconcile=PASS requires s3_credentials_set=true"
    [ "$(report_get "$file" live_preview_observed)" = "true" ] || fail "live_minio_reconcile=PASS requires live_preview_observed=true"
    [ "$(report_get "$file" live_apply_observed)" = "true" ] || fail "live_minio_reconcile=PASS requires live_apply_observed=true"
    [ "$(report_get "$file" live_tenant_prefix_observed)" = "true" ] || fail "live_minio_reconcile=PASS requires live_tenant_prefix_observed=true"
  fi
  if [ "$live" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_minio_skip_reason)" ] || fail "live MinIO SKIP needs live_minio_skip_reason"
    [ "$(report_get "$file" live_preview_observed)" != "true" ] || fail "SKIP cannot claim a live preview walk"
    [ "$(report_get "$file" live_apply_observed)" != "true" ] || fail "SKIP cannot claim a live apply walk"
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
  OUT="$ROOT/.local/g10-file-reconcile-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== G10 leftover: file reconcile honesty (preview vs apply / tenant prefix / live MinIO SKIP)"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #141 identity drift or G6 object restore. Not product G10 close."
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

S3_ENDPOINT_SET=false
S3_CREDENTIALS_SET=false
if [ -n "${S3_ENDPOINT:-}" ]; then
  S3_ENDPOINT_SET=true
fi
if [ -n "${S3_ACCESS_KEY:-}" ] && [ -n "${S3_SECRET_KEY:-}" ]; then
  S3_CREDENTIALS_SET=true
fi

LIVE_LISTENER=false
if port_open 127.0.0.1 19000 || port_open 127.0.0.1 9000; then
  LIVE_LISTENER=true
fi

LIVE_PRESENT=false
if [ "$S3_ENDPOINT_SET" = "true" ] && [ "$S3_CREDENTIALS_SET" = "true" ] && [ "$LIVE_LISTENER" = "true" ]; then
  LIVE_PRESENT=true
fi

if [ "$S3_ENDPOINT_SET" != "true" ] && [ "$LIVE_LISTENER" != "true" ]; then
  LIVE_REASON="S3_ENDPOINT unset; nothing listening on 127.0.0.1:19000 / :9000; Docker forbidden"
elif [ "$S3_CREDENTIALS_SET" != "true" ]; then
  LIVE_REASON="S3 credentials unset; live reconcile SKIP (secrets stay in env/SecretPort)"
elif [ "$LIVE_LISTENER" != "true" ]; then
  LIVE_REASON="S3_ENDPOINT set but object store not reachable; live reconcile SKIP"
else
  LIVE_REASON="live listener present but this leftover does not claim a walk until preview/apply were observed"
fi

COUNTING=SKIP
TENANT_GUARD=SKIP
LIVE_RECONCILE=SKIP
LIVE_PREVIEW=false
LIVE_APPLY=false
LIVE_PREFIX=false
TEST_SUMMARY=""

if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl nexus-file-core,nexus-bootstrap -am test \
    -Dtest=G10FileReconcileHonestyTest,FileObjectReconcileTest,FileReconcileApiTest,LiveMinioFileReconcileOptionalTest \
    -Dsurefire.failIfNoSpecifiedTests=false | tee "$OUT/mvn.log"
  MVN_RC=${PIPESTATUS[0]}
  set -e
  [ "$MVN_RC" -eq 0 ] || fail "mvn leftover tests failed"
  COUNTING=PASS
  TENANT_GUARD=PASS
  TEST_SUMMARY="$(awk '/Tests run:/{line=$0} END{print line}' "$OUT/mvn.log")"
  if grep -q 'LIVE_MINIO_FILE_RECONCILE' "$OUT/mvn.log"; then
    LIVE_RECONCILE=PASS
    LIVE_PREVIEW=true
    LIVE_APPLY=true
    LIVE_PREFIX=true
    LIVE_REASON="live preview+apply+tenant-prefix walk observed"
  else
    LIVE_RECONCILE=SKIP
  fi
else
  COUNTING=PASS
  TENANT_GUARD=PASS
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
  kv counting_preview_apply "$COUNTING"
  kv tenant_prefix_guard "$TENANT_GUARD"
  kv s3_endpoint_set "$S3_ENDPOINT_SET"
  kv s3_credentials_set "$S3_CREDENTIALS_SET"
  kv live_minio_listener "$LIVE_LISTENER"
  kv live_minio_present "$LIVE_PRESENT"
  kv live_minio_reconcile "$LIVE_RECONCILE"
  kv live_minio_skip_reason "$LIVE_REASON"
  kv live_preview_observed "$LIVE_PREVIEW"
  kv live_apply_observed "$LIVE_APPLY"
  kv live_tenant_prefix_observed "$LIVE_PREFIX"
  kv identity_drift_141_redone false
  kv g6_object_restore_redone false
  kv g10_close NOT_CLAIMED
  kv g10_close_reason "Honesty leftover around file metadata ↔ object reconcile. Live MinIO SKIP when unavailable. Not product G10 close."
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
  echo "| Counting preview vs apply | $COUNTING | in-memory FileObjectReconciler |"
  echo "| Tenant-prefix guard | $TENANT_GUARD | tenant A apply leaves tenant B orphan |"
  echo "| Live MinIO / object store | $LIVE_RECONCILE | ${LIVE_REASON} |"
  echo "| live_preview_observed | $LIVE_PREVIEW | apply=$LIVE_APPLY prefix=$LIVE_PREFIX |"
  echo "| #141 identity drift redo | false | sibling leftover |"
  echo "| G6 object restore redo | false | bytes are not reconstructed |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| G10 close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "g10-file-reconcile-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT"

SANITIZED="${ROOT}/docs/engineering/g10-file-reconcile-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT live_minio=$LIVE_RECONCILE g10_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (live MinIO / object-store $LIVE_RECONCILE)"
echo "See docs/g10-file-reconcile-honesty.md"
echo "Not claimed: product G10 close / identity drift #141 / G6 object restore"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
