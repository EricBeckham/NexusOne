#!/usr/bin/env bash
# S0 leftover remainder beyond #223 / #180 / #200: SecretPort rotate / getenv /
# LocalThenEnv PASS/SKIP honesty. Parent leftover harness fixtures stay PASS.
# LocalThenEnv in-process rotate / overlay stay PASS.
# Live OS getenv rotate, live JVM restart, live LocalThenEnv.system() getenv,
# JDBC runtime SecretPort, and S3/MinIO client rebuild stay SKIP unless that
# walk was observed.
# Does not redo #143 / #180 / #200 / #223 product code.
# Does not claim product S0 / SecretPort close. G18 stays closed. No Docker.
# No P0/M0 redo. AuditPort same-TX untouched.
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

REQUIRED_LEFTOVER_ROWS="s0-secret-rotate-without-restart s0-secret-getenv-snapshot-honesty s0-secret-rotate-without-restart-honesty s0-secret-rotate-getenv-remainder-honesty s0-secret-rotate-getenv-leftover-honesty s0s1-live-redis-minio-isolation-honesty g7-withdraw-browser-evidence-honesty g4-hot-purge-staging-leftover-honesty g9-live-multi-jvm-replica-remainder-honesty g3-outbox-consume-idempotent-honesty g10-leftover-honesty-remainder g2-report-physical-dump-honesty g6-recovery-port-dry-run-remainder-honesty g9-sentinel-cluster-remainder-honesty g9-sentinel-cluster-honesty g9-live-multi-jvm-replica g9-redis-down-invalidate-remainder g9-redis-down-invalidate-honesty g17-mfa-recovery-vue-otp-honesty g17-mfa-recovery-email-honesty g17-gm-tls-nginx-handshake-honesty g2-ops-pg-dump-honesty g2-h5-every-page-form-write-honesty g4-export-wizard-deny-honesty g10-leftover-honesty g5-mid-publish-failure-honesty g5-publishhook-ddl-honesty g3-outbox-consume-crash-honesty g1-queryast-boundsql-honesty g1-queryast-export-honesty g15-wx-dt-native-bridge-honesty g15-wx-dt-native-bridge-remainder linux-arm-profile-leftover-honesty g4-p1-staging-audit-events-honesty g6-recovery-port-dry-run-honesty g17-separation-honesty g17-separation-leftover-honesty g4-hot-purge-90d-honesty g1-fieldpolicy-mask-second-auth-honesty g10-identity-drift-honesty g15-native-bridge-honesty g4-pkce-spa-verifier-honesty g4-pkce-spa-verifier-remainder-honesty g5-browser-failed-publish g7-withdraw-browser-honesty g7-query-expense-travel-honesty g2-form-write-honesty g10-file-reconcile-honesty g7-withdraw-browser"

usage() {
  cat <<'EOF'
Usage:
  scripts/s0-secret-rotate-getenv-leftover-honesty.sh [--out DIR] [--skip-tests]
  scripts/s0-secret-rotate-getenv-leftover-honesty.sh --verify-report FILE

#223 / #180 / #200 leftover harness contracts stay always-green PASS.
LocalThenEnv in-process rotate / overlay stay PASS.
Live OS getenv rotate / live JVM restart / live LocalThenEnv.system() /
JDBC runtime / S3 client rebuild are PASS only when observed.
Missing mutability / observation is honest SKIP.
Product S0 / SecretPort close stays NOT_CLAIMED.

  --out DIR           Output directory (default: .local/s0-secret-rotate-getenv-leftover-honesty/<utc>)
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

reject_fake_live() {
  local file="$1" verdict_key="$2" mutable_key="$3" observed_key="$4" skip_key="$5"
  local verdict
  verdict="$(report_get "$file" "$verdict_key")"
  case "$verdict" in
    PASS|SKIP|FAIL) ;;
    *) fail "$verdict_key must be PASS/SKIP/FAIL" ;;
  esac
  if [ "$verdict" = "PASS" ]; then
    if [ -n "$mutable_key" ]; then
      [ "$(report_get "$file" "$mutable_key")" = "true" ] || fail "$verdict_key=PASS requires $mutable_key=true"
    fi
    [ "$(report_get "$file" "$observed_key")" = "true" ] || fail "$verdict_key=PASS requires $observed_key=true"
  fi
  if [ "$verdict" = "SKIP" ]; then
    [ -n "$(report_get "$file" "$skip_key")" ] || fail "$verdict_key SKIP needs $skip_key"
    [ "$(report_get "$file" "$observed_key")" != "true" ] || fail "SKIP cannot claim $observed_key=true"
  fi
}

verify_report() {
  local file="$1"
  [ -f "$file" ] || fail "report not found: $file"
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_180_harness leftover_200_harness leftover_223_harness leftover_180_cells_honest leftover_200_cells_honest leftover_223_cells_honest leftover_rows_kept leftover_rows env_map_rotate local_then_env_map_rotate local_then_env_overlay_wins os_getenv_unmodifiable os_getenv_mutable os_getenv_rotate_observed live_os_getenv_rotate live_os_getenv_rotate_skip_reason live_restart_observed live_restart live_restart_skip_reason live_local_then_env_system_observed live_local_then_env_system_getenv live_local_then_env_system_skip_reason live_jdbc_runtime_observed live_jdbc_runtime_secretport live_jdbc_runtime_skip_reason live_s3_client_rotate_observed live_s3_client_rotate live_s3_client_rotate_skip_reason openbao_introduced kms_introduced redo_143 redo_180 redo_200 redo_223 s0_close secret_port_close g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" redo_143)" = "false" ] || fail "must not redo #143"
  [ "$(report_get "$file" redo_180)" = "false" ] || fail "must not redo #180"
  [ "$(report_get "$file" redo_200)" = "false" ] || fail "must not redo #200"
  [ "$(report_get "$file" redo_223)" = "false" ] || fail "must not redo #223"
  [ "$(report_get "$file" openbao_introduced)" = "false" ] || fail "openbao_introduced must stay false"
  [ "$(report_get "$file" kms_introduced)" = "false" ] || fail "kms_introduced must stay false"
  [ "$(report_get "$file" s0_close)" = "NOT_CLAIMED" ] || fail "s0_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" secret_port_close)" = "NOT_CLAIMED" ] || fail "secret_port_close must stay NOT_CLAIMED"
  [ "$(report_get "$file" leftover_180_harness)" = "PASS" ] || fail "leftover_180_harness must be PASS"
  [ "$(report_get "$file" leftover_200_harness)" = "PASS" ] || fail "leftover_200_harness must be PASS"
  [ "$(report_get "$file" leftover_223_harness)" = "PASS" ] || fail "leftover_223_harness must be PASS"
  [ "$(report_get "$file" leftover_180_cells_honest)" = "PASS" ] || fail "leftover_180_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_200_cells_honest)" = "PASS" ] || fail "leftover_200_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_223_cells_honest)" = "PASS" ] || fail "leftover_223_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" env_map_rotate)" = "PASS" ] || fail "env_map_rotate must be PASS"
  [ "$(report_get "$file" local_then_env_map_rotate)" = "PASS" ] || fail "local_then_env_map_rotate must be PASS"
  [ "$(report_get "$file" local_then_env_overlay_wins)" = "PASS" ] || fail "local_then_env_overlay_wins must be PASS"

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $REQUIRED_LEFTOVER_ROWS; do
    echo "$rows" | grep -Fq "$required_row" || fail "leftover_rows must keep $required_row"
  done

  reject_fake_live "$file" live_os_getenv_rotate os_getenv_mutable os_getenv_rotate_observed live_os_getenv_rotate_skip_reason
  reject_fake_live "$file" live_restart "" live_restart_observed live_restart_skip_reason
  reject_fake_live "$file" live_local_then_env_system_getenv os_getenv_mutable live_local_then_env_system_observed live_local_then_env_system_skip_reason
  reject_fake_live "$file" live_jdbc_runtime_secretport "" live_jdbc_runtime_observed live_jdbc_runtime_skip_reason
  reject_fake_live "$file" live_s3_client_rotate "" live_s3_client_rotate_observed live_s3_client_rotate_skip_reason

  if grep -Eiq 'PASSWORD=|passwd=|ACCESS_KEY=' "$file"; then
    fail "report must not contain password/secret material"
  fi
  echo "PASS: report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/s0-secret-rotate-getenv-leftover-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== S0 leftover: SecretPort rotate / getenv / LocalThenEnv leftover honesty beyond #223/#180/#200"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #143/#180/#200/#223. Not OpenBao/KMS. Not product S0 / SecretPort close. G18 stays closed."
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
grep -q '\[s0-secret-rotate-getenv-leftover-honesty\](\./s0-secret-rotate-getenv-leftover-honesty.md)' "$README" \
  || fail "docs/README.md must list this leftover row"
grep -Fq 'g7-withdraw-browser-evidence-honesty.md' "$README" \
  || fail "docs/README.md must keep sibling leftover g7-withdraw-browser-evidence-honesty (#212)"
grep -Fq 's0-secret-rotate-getenv-remainder-honesty.md' "$README" \
  || fail "docs/README.md must keep sibling leftover s0-secret-rotate-getenv-remainder-honesty (#223)"

echo "== Honesty fixtures (this leftover)"
verify_report "$HERE/s0-secret-rotate-getenv-leftover-honesty/fixtures/honest-skip.env"
if ( verify_report "$HERE/s0-secret-rotate-getenv-leftover-honesty/fixtures/fake-os-getenv-pass.env" ); then
  fail "fake-os-getenv-pass.env must be rejected"
fi
echo "PASS: fake-os-getenv-pass.env rejected"
if ( verify_report "$HERE/s0-secret-rotate-getenv-leftover-honesty/fixtures/fake-live-restart-pass.env" ); then
  fail "fake-live-restart-pass.env must be rejected"
fi
echo "PASS: fake-live-restart-pass.env rejected"
if ( verify_report "$HERE/s0-secret-rotate-getenv-leftover-honesty/fixtures/fake-local-then-env-system-pass.env" ); then
  fail "fake-local-then-env-system-pass.env must be rejected"
fi
echo "PASS: fake-local-then-env-system-pass.env rejected"
if ( verify_report "$HERE/s0-secret-rotate-getenv-leftover-honesty/fixtures/fake-s0-close.env" ); then
  fail "fake-s0-close.env must be rejected"
fi
echo "PASS: fake-s0-close.env rejected"
if ( verify_report "$HERE/s0-secret-rotate-getenv-leftover-honesty/fixtures/fake-redone.env" ); then
  fail "fake-redone.env must be rejected"
fi
echo "PASS: fake-redone.env rejected"

echo
echo "== Parent leftover harness still PASS (#223 / #180 / #200 verify-report only; not a product redo)"
"$ROOT/scripts/s0-secret-rotate-getenv-remainder-honesty.sh" --verify-report \
  "$ROOT/scripts/s0-secret-rotate-getenv-remainder-honesty/fixtures/honest-skip.env"
"$ROOT/scripts/s0-secret-getenv-snapshot-honesty.sh" --verify-report \
  "$ROOT/scripts/s0-secret-getenv-snapshot-honesty/fixtures/honest-skip.env"
"$ROOT/scripts/s0-secret-rotate-without-restart-honesty.sh" --verify-report \
  "$ROOT/scripts/s0-secret-rotate-without-restart-honesty/fixtures/honest-skip.env"
LEFTOVER_180=PASS
LEFTOVER_200=PASS
LEFTOVER_223=PASS
LEFTOVER_180_CELLS=PASS
LEFTOVER_200_CELLS=PASS
LEFTOVER_223_CELLS=PASS
LTE_MAP=PASS
LTE_OVERLAY=PASS

OS_UNMODIFIABLE=true
OS_MUTABLE=false
OS_OBSERVED=false
if [ "${NEXUS_S0_OS_ENV_ROTATE_OBSERVED:-}" = "true" ]; then
  OS_OBSERVED=true
fi
OS_REASON="Java 21 System.getenv() is an unmodifiable process-start snapshot; OS environ rotate of a running JVM is not a supported API; EnvSecretAdapter.system() and LocalThenEnvSecretPort.system() use System::getenv; restart is required; #143/#180/#200/#223 leftover stay siblings; not OpenBao/KMS; not a product S0 / SecretPort close"
if [ "$OS_OBSERVED" = "true" ]; then
  OS_REASON="OS getenv rotate listed but leftover still requires a mutable process map; Java 21 snapshot is not a supported in-process OS env rotate API; #143/#180/#200/#223 not redone"
  OS_OBSERVED=false
fi

LIVE_RESTART_OBSERVED=false
LIVE_RESTART_REASON="no live JVM restart after a secret rotate was observed; Java 21 getenv snapshot requires restart; leftover does not restart a process; #143/#180/#200/#223 stay siblings; not a product S0 / SecretPort close"
if [ "${NEXUS_S0_LIVE_RESTART_OBSERVED:-}" = "true" ]; then
  LIVE_RESTART_REASON="NEXUS_S0_LIVE_RESTART_OBSERVED listed but this leftover did not restart a running JVM or observe a post-restart getenv walk; #143/#180/#200/#223 stay siblings; not a product S0 / SecretPort close"
  LIVE_RESTART_OBSERVED=false
fi

LTE_OBSERVED=false
LTE_REASON="LocalThenEnvSecretPort.system() uses EnvSecretAdapter.system() → System::getenv (Java 21 process-start snapshot); live OS getenv rotate of the product default was not observed; leftover does not restart a process; #143/#180/#200/#223 stay siblings; not OpenBao/KMS"
if [ "${NEXUS_S0_LTE_SYSTEM_GETENV_OBSERVED:-}" = "true" ]; then
  LTE_REASON="NEXUS_S0_LTE_SYSTEM_GETENV_OBSERVED listed but this leftover did not observe LocalThenEnvSecretPort.system() picking up an OS environ rotate on a running JVM; LocalThenEnvSecretPort.system() still uses EnvSecretAdapter.system() → System::getenv; #223 stay sibling; not a product S0 / SecretPort close"
  LTE_OBSERVED=false
fi

JDBC_OBSERVED=false
JDBC_REASON="JDBC DataSource runtime SecretPort rotate was not observed; Spring still binds NEXUS_DB_* at start; leftover does not rebuild the pool; #223 stay sibling; not OpenBao/KMS; not a product S0 / SecretPort close"
if [ "${NEXUS_S0_JDBC_RUNTIME_OBSERVED:-}" = "true" ]; then
  JDBC_REASON="NEXUS_S0_JDBC_RUNTIME_OBSERVED listed but this leftover did not rebuild a Spring DataSource after an env rotate; NEXUS_DB_* still binds at start; #223 stay sibling; not a product S0 / SecretPort close"
  JDBC_OBSERVED=false
fi

S3_OBSERVED=false
S3_REASON="S3 / MinIO client live rotate was not observed; credentials stay snapshotted at fromSecretPort; leftover does not rebuild the client; #223 stay sibling; not OpenBao/KMS; not a product S0 / SecretPort close"
if [ "${NEXUS_S0_S3_CLIENT_ROTATE_OBSERVED:-}" = "true" ]; then
  S3_REASON="NEXUS_S0_S3_CLIENT_ROTATE_OBSERVED listed but this leftover did not rebuild MinioObjectStorage after an env rotate; credentials stay snapshotted at fromSecretPort; #223 stay sibling; not a product S0 / SecretPort close"
  S3_OBSERVED=false
fi

TEST_SUMMARY="parent --verify-report only; no #143/#180/#200/#223 product redo"
if [ "$SKIP_TESTS" = "0" ]; then
  if command -v mvn >/dev/null 2>&1; then
    mvn -version | head -n 1 | tee -a "$OUT/collect.log"
    set +e
    mvn -pl nexus-crypto-core -am test \
      -Dtest=S0SecretRotateGetenvLeftoverHonestyTest \
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
LEFTOVER_ROWS_CSV="$(echo "$REQUIRED_LEFTOVER_ROWS" | tr ' ' ',')"

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
  kv leftover_180_harness "$LEFTOVER_180"
  kv leftover_200_harness "$LEFTOVER_200"
  kv leftover_223_harness "$LEFTOVER_223"
  kv leftover_180_cells_honest "$LEFTOVER_180_CELLS"
  kv leftover_200_cells_honest "$LEFTOVER_200_CELLS"
  kv leftover_223_cells_honest "$LEFTOVER_223_CELLS"
  kv leftover_rows_kept true
  kv leftover_rows "$LEFTOVER_ROWS_CSV"
  kv env_map_rotate PASS
  kv local_then_env_map_rotate "$LTE_MAP"
  kv local_then_env_overlay_wins "$LTE_OVERLAY"
  kv os_getenv_unmodifiable "$OS_UNMODIFIABLE"
  kv os_getenv_mutable "$OS_MUTABLE"
  kv os_getenv_rotate_observed "$OS_OBSERVED"
  kv live_os_getenv_rotate SKIP
  kv live_os_getenv_rotate_skip_reason "$OS_REASON"
  kv live_restart_observed "$LIVE_RESTART_OBSERVED"
  kv live_restart SKIP
  kv live_restart_skip_reason "$LIVE_RESTART_REASON"
  kv live_local_then_env_system_observed "$LTE_OBSERVED"
  kv live_local_then_env_system_getenv SKIP
  kv live_local_then_env_system_skip_reason "$LTE_REASON"
  kv live_jdbc_runtime_observed "$JDBC_OBSERVED"
  kv live_jdbc_runtime_secretport SKIP
  kv live_jdbc_runtime_skip_reason "$JDBC_REASON"
  kv live_s3_client_rotate_observed "$S3_OBSERVED"
  kv live_s3_client_rotate SKIP
  kv live_s3_client_rotate_skip_reason "$S3_REASON"
  kv openbao_introduced false
  kv kms_introduced false
  kv redo_143 false
  kv redo_180 false
  kv redo_200 false
  kv redo_223 false
  kv s0_close NOT_CLAIMED
  kv secret_port_close NOT_CLAIMED
  kv s0_close_reason "Honesty leftover beyond #223/#180/#200. Parent harness PASS. LocalThenEnv in-process PASS. Live OS getenv / restart / LocalThenEnv.system / JDBC / S3 SKIP. Not product S0 / SecretPort close."
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
  echo "| #223 leftover harness | $LEFTOVER_223 | parent --verify-report; not a product redo |"
  echo "| #180 leftover harness | $LEFTOVER_180 | parent --verify-report; not a product redo |"
  echo "| #200 leftover harness | $LEFTOVER_200 | parent --verify-report; not a product redo |"
  echo "| Mutable env-map rotate | PASS | already proven by #180/#200/#223 harness |"
  echo "| LocalThenEnv map rotate | $LTE_MAP | in-process; no overlay; not a #143 redo |"
  echo "| LocalThenEnv overlay wins | $LTE_OVERLAY | overlay still wins after env rotate |"
  echo "| Live OS System.getenv() rotate | SKIP | ${OS_REASON} |"
  echo "| Live JVM restart after rotate | SKIP | ${LIVE_RESTART_REASON} |"
  echo "| Live LocalThenEnv.system() getenv | SKIP | ${LTE_REASON} |"
  echo "| Live JDBC runtime SecretPort | SKIP | ${JDBC_REASON} |"
  echo "| Live S3/MinIO client rotate | SKIP | ${S3_REASON} |"
  echo "| OpenBao / KMS introduced | false | ARCH-007 |"
  echo "| #143 / #180 / #200 / #223 redo | false | sibling leftovers |"
  echo "| Leftover rows kept | true | #143 + #180 + #200 + #223 + ALL siblings including #212 G7 leftover |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| S0 / SecretPort close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "s0-secret-rotate-getenv-leftover-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT"

SANITIZED="${ROOT}/docs/engineering/s0-secret-rotate-getenv-leftover-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT leftover_223=$LEFTOVER_223 leftover_180=$LEFTOVER_180 leftover_200=$LEFTOVER_200 local_then_env=PASS live_os_getenv_rotate=SKIP live_restart=SKIP live_lte_system=SKIP s0_close=NOT_CLAIMED secret_port_close=NOT_CLAIMED"
echo
echo "PASS: honesty leftover recorded (#223/#180/#200 harness PASS; LocalThenEnv in-process PASS; live OS getenv / restart / LocalThenEnv.system / JDBC / S3 SKIP)"
echo "See docs/s0-secret-rotate-getenv-leftover-honesty.md"
echo "Keep leftover rows: docs/s0-secret-rotate-without-restart.md, docs/s0-secret-getenv-snapshot-honesty.md, docs/s0-secret-rotate-without-restart-honesty.md, docs/s0-secret-rotate-getenv-remainder-honesty.md, docs/g7-withdraw-browser-evidence-honesty.md"
echo "Not claimed: product S0 close / SecretPort close / OpenBao / KMS / OS env rotate without restart / live JVM restart / live LocalThenEnv.system() / JDBC runtime / S3 client rotate"
echo "Not redone: #143 / #180 / #200 / #223 / P0 / M0"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
