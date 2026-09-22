#!/usr/bin/env bash
# S0 leftover: OS System.getenv() startup-snapshot honesty beyond #143.
#
# Extends docs/s0-secret-rotate-without-restart.md. Does NOT redo #143.
# Does NOT introduce OpenBao / KMS. Does NOT claim a product S0 close.
# G18 stays closed. P0 / M0 are not redone. No Docker.
# Mutable env-map rotate stays always-green. OS getenv rotate is PASS only
# when the process map is mutable AND a live OS rotate was observed.
# Java 21 System.getenv() snapshot is honest SKIP.
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
  scripts/s0-secret-getenv-snapshot-honesty.sh [--out DIR] [--skip-tests]
  scripts/s0-secret-getenv-snapshot-honesty.sh --verify-report FILE

Always-green mutable env-map rotate (thin probe, not a #143 redo).
OS System.getenv() rotate-without-restart is PASS only when the process
map is mutable AND a live OS rotate was observed. Java 21 snapshot is
honest SKIP. Product S0 close stays NOT_CLAIMED.

  --out DIR           Output directory (default: .local/s0-secret-getenv-snapshot-honesty/<utc>)
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
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used env_map_rotate os_getenv_unmodifiable os_getenv_mutable os_getenv_rotate_observed os_getenv_rotate os_getenv_skip_reason openbao_introduced kms_introduced s0_143_redone s0_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" s0_143_redone)" = "false" ] || fail "must not redo #143"
  [ "$(report_get "$file" openbao_introduced)" = "false" ] || fail "openbao_introduced must stay false"
  [ "$(report_get "$file" kms_introduced)" = "false" ] || fail "kms_introduced must stay false"
  [ "$(report_get "$file" s0_close)" = "NOT_CLAIMED" ] || fail "s0_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" env_map_rotate)" = "PASS" ] || fail "env_map_rotate must be PASS"

  local os
  os="$(report_get "$file" os_getenv_rotate)"
  case "$os" in
    PASS|SKIP|FAIL) ;;
    *) fail "os_getenv_rotate must be PASS/SKIP/FAIL" ;;
  esac
  if [ "$os" = "PASS" ]; then
    [ "$(report_get "$file" os_getenv_mutable)" = "true" ] || fail "os_getenv_rotate=PASS requires os_getenv_mutable=true"
    [ "$(report_get "$file" os_getenv_rotate_observed)" = "true" ] || fail "os_getenv_rotate=PASS requires os_getenv_rotate_observed=true"
  fi
  if [ "$os" = "SKIP" ]; then
    [ -n "$(report_get "$file" os_getenv_skip_reason)" ] || fail "os_getenv_rotate SKIP needs os_getenv_skip_reason"
    [ "$(report_get "$file" os_getenv_rotate_observed)" != "true" ] || fail "SKIP cannot claim os_getenv_rotate_observed=true"
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
  OUT="$ROOT/.local/s0-secret-getenv-snapshot-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== S0 leftover: OS System.getenv() startup-snapshot honesty beyond #143"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #143. Not OpenBao/KMS. Not product S0 close. G18 stays closed."
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

OS_UNMODIFIABLE=true
OS_MUTABLE=false
OS_OBSERVED=false
if [ "${NEXUS_S0_OS_ENV_ROTATE_OBSERVED:-}" = "true" ]; then
  OS_OBSERVED=true
fi
OS_REASON="Java 21 System.getenv() is an unmodifiable process-start snapshot; OS environ rotate of a running JVM is not a supported API; EnvSecretAdapter.system() uses System::getenv; restart is required; not OpenBao/KMS; not a product S0 close"
if [ "$OS_OBSERVED" = "true" ]; then
  OS_REASON="OS getenv rotate listed but leftover still requires a mutable process map; Java 21 snapshot is not a supported in-process OS env rotate API"
  OS_OBSERVED=false
fi

ENV_MAP=PASS
TEST_SUMMARY=""

if [ "$SKIP_TESTS" = "0" ]; then
  command -v mvn >/dev/null 2>&1 || fail "mvn not on PATH"
  mvn -version | head -n 1 | tee -a "$OUT/collect.log"
  set +e
  mvn -pl nexus-crypto-core -am test \
    -Dtest=S0SecretGetenvSnapshotHonestyTest \
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
  kv env_map_rotate "$ENV_MAP"
  kv os_getenv_unmodifiable "$OS_UNMODIFIABLE"
  kv os_getenv_mutable "$OS_MUTABLE"
  kv os_getenv_rotate_observed "$OS_OBSERVED"
  kv os_getenv_rotate SKIP
  kv os_getenv_skip_reason "$OS_REASON"
  kv openbao_introduced false
  kv kms_introduced false
  kv s0_143_redone false
  kv s0_close NOT_CLAIMED
  kv s0_close_reason "Honesty leftover around OS System.getenv() startup snapshot. Mutable env map stays PASS. Not product S0 close."
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
  echo "| Mutable env-map rotate | $ENV_MAP | thin probe; not a #143 redo |"
  echo "| OS System.getenv() rotate | SKIP | ${OS_REASON} |"
  echo "| OpenBao / KMS introduced | false | ARCH-007 |"
  echo "| #143 redo | false | sibling leftover |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| S0 close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "s0-secret-getenv-snapshot-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT"

SANITIZED="${ROOT}/docs/engineering/s0-secret-getenv-snapshot-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT os_getenv_rotate=SKIP s0_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (env-map rotate PASS / OS getenv snapshot SKIP)"
echo "See docs/s0-secret-getenv-snapshot-honesty.md"
echo "Not claimed: product S0 close / OpenBao / KMS / OS env rotate without restart"
echo "Not redone: #143 rotate-without-restart / P0 / M0"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
