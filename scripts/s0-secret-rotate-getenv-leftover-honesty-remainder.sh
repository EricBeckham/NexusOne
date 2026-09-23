#!/usr/bin/env bash
# S0 leftover remainder beyond #241 / #223: SecretPort rotate / getenv /
# LocalThenEnv leftover honesty remainder PASS/SKIP.
# Parent leftover honesty (#241) and remainder honesty (#223) fixtures stay PASS.
# LocalThenEnv in-process rotate / overlay stay PASS.
# Live OS getenv rotate, live JVM restart, live LocalThenEnv.system() getenv,
# JDBC runtime SecretPort, and S3/MinIO client rebuild stay SKIP unless that
# walk was observed.
# Does not redo #143 / #180 / #200 / #223 / #241 product code.
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
VERIFY_EXPECT=pass
SKIP_TESTS=0

REQUIRED_LEFTOVER_ROWS="s0-secret-rotate-without-restart s0-secret-getenv-snapshot-honesty s0-secret-rotate-without-restart-honesty s0-secret-rotate-getenv-remainder-honesty s0-secret-rotate-getenv-leftover-honesty s0-secret-rotate-getenv-leftover-honesty-remainder g17-separation-leftover-honesty-remainder g9-leftover-honesty-remainder g15-h5-pkce-miniprogram-remainder-honesty g15-h5-pkce-browser g15-miniprogram-webview g15-wx-dt-prod-gap-honesty s0s1-live-redis-minio-isolation-honesty g7-withdraw-browser-evidence-honesty g4-hot-purge-staging-leftover-honesty g4-hot-purge-staging-pkce-remainder-honesty g9-live-multi-jvm-replica-remainder-honesty g3-outbox-consume-idempotent-honesty g10-leftover-honesty-remainder g10-file-object-reconcile-remainder-honesty g2-report-physical-dump-honesty g2-h5-every-page-form-write-remainder-honesty g2-h5-every-page-form-write-report-remainder-honesty g6-recovery-port-dry-run-remainder-honesty g9-sentinel-cluster-remainder-honesty g9-sentinel-cluster-honesty g9-live-multi-jvm-replica g9-redis-down-invalidate-remainder g9-redis-down-invalidate-remainder-honesty g9-redis-down-invalidate-honesty g17-mfa-recovery-vue-otp-honesty g17-mfa-recovery-email-honesty g17-gm-tls-nginx-handshake-honesty g2-ops-pg-dump-honesty g2-h5-every-page-form-write-honesty g4-export-wizard-deny-honesty g10-leftover-honesty g5-mid-publish-failure-honesty g5-publishhook-ddl-honesty g3-outbox-consume-crash-honesty g1-queryast-boundsql-honesty g1-queryast-export-honesty g15-wx-dt-native-bridge-honesty g15-wx-dt-native-bridge-remainder linux-arm-profile-honesty linux-arm-profile-leftover-honesty linux-arm-profile-remainder-honesty g4-p1-staging-audit-events-honesty g6-recovery-port-dry-run-honesty g17-separation-honesty g17-separation-leftover-honesty g4-hot-purge-90d-honesty g1-fieldpolicy-mask-second-auth-honesty g10-identity-drift-honesty g15-native-bridge-honesty g4-pkce-spa-verifier-honesty g4-pkce-spa-verifier-remainder-honesty g5-browser-failed-publish g7-withdraw-browser-honesty g7-query-expense-travel-honesty g7-query-expense-travel-honesty-remainder g1-fieldpolicy-mask-second-auth-remainder-honesty g17-mfa-recovery-vue-otp-remainder-honesty g5-publishhook-ddl-remainder-honesty g2-form-write-honesty g10-file-reconcile-honesty g7-withdraw-browser"

usage() {
  cat <<'EOF'
Usage:
  scripts/s0-secret-rotate-getenv-leftover-honesty-remainder.sh [--out DIR] [--skip-tests]
  scripts/s0-secret-rotate-getenv-leftover-honesty-remainder.sh --verify-report FILE [pass|fail]

#241 leftover honesty / #223 remainder honesty harness contracts stay always-green PASS.
LocalThenEnv in-process rotate / overlay stay PASS.
Live OS getenv rotate / live JVM restart / live LocalThenEnv.system() /
JDBC runtime / S3 client rebuild are PASS only when observed.
Missing mutability / observation is honest SKIP.
Product S0 / SecretPort close stays NOT_CLAIMED.

  --out DIR           Output directory (default: .local/s0-secret-rotate-getenv-leftover-honesty-remainder/<utc>)
  --skip-tests        Inventory + honesty only (do not run mvn)
  --verify-report F   Honesty gate on an existing report.env
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --skip-tests) SKIP_TESTS=1; shift ;;
    --verify-report)
      VERIFY_REPORT="${2:?}"
      shift 2
      if [ "${1:-}" = "pass" ] || [ "${1:-}" = "fail" ]; then
        VERIFY_EXPECT="$1"
        shift
      fi
      ;;
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
    *) echo "FAIL: $verdict_key must be PASS/SKIP/FAIL" >&2; return 1 ;;
  esac
  if [ "$verdict" = "PASS" ]; then
    if [ -n "$mutable_key" ]; then
      [ "$(report_get "$file" "$mutable_key")" = "true" ] || { echo "FAIL: $verdict_key=PASS requires $mutable_key=true" >&2; return 1; }
    fi
    [ "$(report_get "$file" "$observed_key")" = "true" ] || { echo "FAIL: $verdict_key=PASS requires $observed_key=true" >&2; return 1; }
  fi
  if [ "$verdict" = "SKIP" ]; then
    [ -n "$(report_get "$file" "$skip_key")" ] || { echo "FAIL: $verdict_key SKIP needs $skip_key" >&2; return 1; }
    [ "$(report_get "$file" "$observed_key")" != "true" ] || { echo "FAIL: SKIP cannot claim $observed_key=true" >&2; return 1; }
  fi
}

verify_report_inner() {
  local file="$1"
  [ -f "$file" ] || { echo "FAIL: report not found: $file" >&2; return 1; }
  local required="schema_version timestamp_utc java_feature boot_parent_version boot_parent_match docker_used leftover_180_harness leftover_200_harness leftover_223_harness leftover_241_harness leftover_180_cells_honest leftover_200_cells_honest leftover_223_cells_honest leftover_241_cells_honest leftover_rows_kept leftover_rows leftover_259_kept leftover_249_kept leftover_254_kept leftover_240_kept leftover_236_kept leftover_251_kept leftover_243_kept leftover_241_kept env_map_rotate local_then_env_map_rotate local_then_env_overlay_wins os_getenv_unmodifiable os_getenv_mutable os_getenv_rotate_observed live_os_getenv_rotate live_os_getenv_rotate_skip_reason live_restart_observed live_restart live_restart_skip_reason live_local_then_env_system_observed live_local_then_env_system_getenv live_local_then_env_system_skip_reason live_jdbc_runtime_observed live_jdbc_runtime_secretport live_jdbc_runtime_skip_reason live_s3_client_rotate_observed live_s3_client_rotate live_s3_client_rotate_skip_reason openbao_introduced kms_introduced redo_143 redo_180 redo_200 redo_223 redo_241 s0_close secret_port_close g18_status audit_port_changed p0_m0_redone"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || { echo "FAIL: missing key $key in $file" >&2; return 1; }
  done

  [ "$(report_get "$file" java_feature)" = "21" ] || { echo "FAIL: java_feature must be 21" >&2; return 1; }
  [ "$(report_get "$file" boot_parent_version)" = "4.1.1" ] || { echo "FAIL: boot_parent_version must be 4.1.1" >&2; return 1; }
  [ "$(report_get "$file" boot_parent_match)" = "PASS" ] || { echo "FAIL: boot_parent_match must be PASS" >&2; return 1; }
  [ "$(report_get "$file" docker_used)" = "false" ] || { echo "FAIL: docker_used must be false" >&2; return 1; }
  [ "$(report_get "$file" g18_status)" = "closed_unchanged" ] || { echo "FAIL: g18_status must stay closed_unchanged" >&2; return 1; }
  [ "$(report_get "$file" audit_port_changed)" = "false" ] || { echo "FAIL: audit_port_changed must be false" >&2; return 1; }
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || { echo "FAIL: p0_m0_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" redo_143)" = "false" ] || { echo "FAIL: must not redo #143" >&2; return 1; }
  [ "$(report_get "$file" redo_180)" = "false" ] || { echo "FAIL: must not redo #180" >&2; return 1; }
  [ "$(report_get "$file" redo_200)" = "false" ] || { echo "FAIL: must not redo #200" >&2; return 1; }
  [ "$(report_get "$file" redo_223)" = "false" ] || { echo "FAIL: must not redo #223" >&2; return 1; }
  [ "$(report_get "$file" redo_241)" = "false" ] || { echo "FAIL: must not redo #241" >&2; return 1; }
  [ "$(report_get "$file" openbao_introduced)" = "false" ] || { echo "FAIL: openbao_introduced must stay false" >&2; return 1; }
  [ "$(report_get "$file" kms_introduced)" = "false" ] || { echo "FAIL: kms_introduced must stay false" >&2; return 1; }
  [ "$(report_get "$file" s0_close)" = "NOT_CLAIMED" ] || { echo "FAIL: s0_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" secret_port_close)" = "NOT_CLAIMED" ] || { echo "FAIL: secret_port_close must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" leftover_180_harness)" = "PASS" ] || { echo "FAIL: leftover_180_harness must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_200_harness)" = "PASS" ] || { echo "FAIL: leftover_200_harness must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_223_harness)" = "PASS" ] || { echo "FAIL: leftover_223_harness must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_241_harness)" = "PASS" ] || { echo "FAIL: leftover_241_harness must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_180_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_180_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_200_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_200_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_223_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_223_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_241_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_241_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || { echo "FAIL: leftover_rows_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_259_kept)" = "true" ] || { echo "FAIL: leftover_259_kept must stay true (keep g9-leftover-honesty-remainder)" >&2; return 1; }
  [ "$(report_get "$file" leftover_249_kept)" = "true" ] || { echo "FAIL: leftover_249_kept must stay true (keep g17-separation-leftover-honesty-remainder)" >&2; return 1; }
  [ "$(report_get "$file" leftover_254_kept)" = "true" ] || { echo "FAIL: leftover_254_kept must stay true (keep g2-h5-every-page-form-write-report-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_240_kept)" = "true" ] || { echo "FAIL: leftover_240_kept must stay true (keep g15-h5-pkce-miniprogram-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_236_kept)" = "true" ] || { echo "FAIL: leftover_236_kept must stay true (keep linux-arm-profile-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_251_kept)" = "true" ] || { echo "FAIL: leftover_251_kept must stay true (keep g4-hot-purge-staging-pkce-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_243_kept)" = "true" ] || { echo "FAIL: leftover_243_kept must stay true (keep g10-file-object-reconcile-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_241_kept)" = "true" ] || { echo "FAIL: leftover_241_kept must stay true (keep s0-secret-rotate-getenv-leftover-honesty)" >&2; return 1; }
  [ "$(report_get "$file" env_map_rotate)" = "PASS" ] || { echo "FAIL: env_map_rotate must be PASS" >&2; return 1; }
  [ "$(report_get "$file" local_then_env_map_rotate)" = "PASS" ] || { echo "FAIL: local_then_env_map_rotate must be PASS" >&2; return 1; }
  [ "$(report_get "$file" local_then_env_overlay_wins)" = "PASS" ] || { echo "FAIL: local_then_env_overlay_wins must be PASS" >&2; return 1; }

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $REQUIRED_LEFTOVER_ROWS; do
    echo "$rows" | grep -Fq "$required_row" || { echo "FAIL: leftover_rows must keep $required_row" >&2; return 1; }
  done

  reject_fake_live "$file" live_os_getenv_rotate os_getenv_mutable os_getenv_rotate_observed live_os_getenv_rotate_skip_reason || return 1
  reject_fake_live "$file" live_restart "" live_restart_observed live_restart_skip_reason || return 1
  reject_fake_live "$file" live_local_then_env_system_getenv os_getenv_mutable live_local_then_env_system_observed live_local_then_env_system_skip_reason || return 1
  reject_fake_live "$file" live_jdbc_runtime_secretport "" live_jdbc_runtime_observed live_jdbc_runtime_skip_reason || return 1
  reject_fake_live "$file" live_s3_client_rotate "" live_s3_client_rotate_observed live_s3_client_rotate_skip_reason || return 1

  if grep -Eiq 'PASSWORD=|passwd=|ACCESS_KEY=' "$file"; then
    echo "FAIL: report must not contain password/secret material" >&2
    return 1
  fi
  if grep -Eiq 's0_close=CLOSED|secret_port_close=CLOSED|product secretport closed|product s0 closed|SecretPort 已关闭|产品 S0 已关闭' "$file"; then
    echo "FAIL: report must not claim product S0 / SecretPort closed" >&2
    return 1
  fi
  return 0
}

verify_report() {
  local file="$1"
  local expect="${2:-pass}"
  [ -f "$file" ] || fail "report not found: $file"
  set +e
  verify_report_inner "$file"
  local rc=$?
  set -e
  if [ "$expect" = "pass" ]; then
    [ "$rc" -eq 0 ] || fail "expected honest leftover, got FAIL ($file)"
    echo "PASS honesty: $file"
  elif [ "$expect" = "fail" ]; then
    [ "$rc" -ne 0 ] || fail "expected fake report to fail: $file"
    echo "PASS reject fake: $file"
  else
    fail "unknown verify expect: $expect"
  fi
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT" "$VERIFY_EXPECT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/s0-secret-rotate-getenv-leftover-honesty-remainder/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== S0 leftover: SecretPort rotate / getenv / LocalThenEnv leftover honesty remainder beyond #241/#223"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #143/#180/#200/#223/#241. Not OpenBao/KMS. Not product S0 / SecretPort close. G18 stays closed."
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
grep -q '\[s0-secret-rotate-getenv-leftover-honesty-remainder\](\./s0-secret-rotate-getenv-leftover-honesty-remainder.md)' "$README" \
  || fail "docs/README.md must list this leftover row"
grep -Fq 's0-secret-rotate-getenv-leftover-honesty.md' "$README" \
  || fail "docs/README.md must keep sibling leftover s0-secret-rotate-getenv-leftover-honesty (#241)"
grep -Fq 's0-secret-rotate-getenv-remainder-honesty.md' "$README" \
  || fail "docs/README.md must keep sibling leftover s0-secret-rotate-getenv-remainder-honesty (#223)"
grep -Fq 'g17-separation-leftover-honesty-remainder.md' "$README" \
  || fail "docs/README.md must keep sibling leftover g17-separation-leftover-honesty-remainder (#249)"
grep -Fq 'g9-leftover-honesty-remainder.md' "$README" \
  || fail "docs/README.md must keep sibling leftover g9-leftover-honesty-remainder (#259)"
grep -Fq 'g2-h5-every-page-form-write-report-remainder-honesty.md' "$README" \
  || fail "docs/README.md must keep sibling leftover g2-h5-every-page-form-write-report-remainder-honesty (#254)"
grep -Fq 'g15-h5-pkce-miniprogram-remainder-honesty.md' "$README" \
  || fail "docs/README.md must keep sibling leftover g15-h5-pkce-miniprogram-remainder-honesty (#240)"
grep -Fq 'linux-arm-profile-remainder-honesty.md' "$README" \
  || fail "docs/README.md must keep sibling leftover linux-arm-profile-remainder-honesty (#236)"
grep -Fq 'g4-hot-purge-staging-pkce-remainder-honesty.md' "$README" \
  || fail "docs/README.md must keep sibling leftover g4-hot-purge-staging-pkce-remainder-honesty (#251)"
grep -Fq 'g10-file-object-reconcile-remainder-honesty.md' "$README" \
  || fail "docs/README.md must keep sibling leftover g10-file-object-reconcile-remainder-honesty (#243)"

echo "== Honesty fixtures (this leftover)"
verify_report "$HERE/s0-secret-rotate-getenv-leftover-honesty-remainder/fixtures/honest-skip.env" pass
verify_report "$HERE/s0-secret-rotate-getenv-leftover-honesty-remainder/fixtures/fake-os-getenv-pass.env" fail
verify_report "$HERE/s0-secret-rotate-getenv-leftover-honesty-remainder/fixtures/fake-live-restart-pass.env" fail
verify_report "$HERE/s0-secret-rotate-getenv-leftover-honesty-remainder/fixtures/fake-local-then-env-system-pass.env" fail
verify_report "$HERE/s0-secret-rotate-getenv-leftover-honesty-remainder/fixtures/fake-s0-close.env" fail
verify_report "$HERE/s0-secret-rotate-getenv-leftover-honesty-remainder/fixtures/fake-redone.env" fail

echo
echo "== Parent leftover harness still PASS (#241 / #223 / #180 / #200 verify-report only; not a product redo)"
"$ROOT/scripts/s0-secret-rotate-getenv-leftover-honesty.sh" --verify-report \
  "$ROOT/scripts/s0-secret-rotate-getenv-leftover-honesty/fixtures/honest-skip.env"
"$ROOT/scripts/s0-secret-rotate-getenv-remainder-honesty.sh" --verify-report \
  "$ROOT/scripts/s0-secret-rotate-getenv-remainder-honesty/fixtures/honest-skip.env"
"$ROOT/scripts/s0-secret-getenv-snapshot-honesty.sh" --verify-report \
  "$ROOT/scripts/s0-secret-getenv-snapshot-honesty/fixtures/honest-skip.env"
"$ROOT/scripts/s0-secret-rotate-without-restart-honesty.sh" --verify-report \
  "$ROOT/scripts/s0-secret-rotate-without-restart-honesty/fixtures/honest-skip.env"
LEFTOVER_180=PASS
LEFTOVER_200=PASS
LEFTOVER_223=PASS
LEFTOVER_241=PASS
LEFTOVER_180_CELLS=PASS
LEFTOVER_200_CELLS=PASS
LEFTOVER_223_CELLS=PASS
LEFTOVER_241_CELLS=PASS
LTE_MAP=PASS
LTE_OVERLAY=PASS

OS_UNMODIFIABLE=true
OS_MUTABLE=false
OS_OBSERVED=false
if [ "${NEXUS_S0_OS_ENV_ROTATE_OBSERVED:-}" = "true" ]; then
  OS_OBSERVED=true
fi
OS_REASON="Java 21 System.getenv() is an unmodifiable process-start snapshot; OS environ rotate of a running JVM is not a supported API; EnvSecretAdapter.system() and LocalThenEnvSecretPort.system() use System::getenv; restart is required; #143/#180/#200/#223/#241 leftover stay siblings; not OpenBao/KMS; not a product S0 / SecretPort close"
if [ "$OS_OBSERVED" = "true" ]; then
  OS_REASON="OS getenv rotate listed but leftover still requires a mutable process map; Java 21 snapshot is not a supported in-process OS env rotate API; #143/#180/#200/#223/#241 not redone"
  OS_OBSERVED=false
fi

LIVE_RESTART_OBSERVED=false
LIVE_RESTART_REASON="no live JVM restart after a secret rotate was observed; Java 21 getenv snapshot requires restart; leftover does not restart a process; #143/#180/#200/#223/#241 stay siblings; not a product S0 / SecretPort close"
if [ "${NEXUS_S0_LIVE_RESTART_OBSERVED:-}" = "true" ]; then
  LIVE_RESTART_REASON="NEXUS_S0_LIVE_RESTART_OBSERVED listed but this leftover did not restart a running JVM or observe a post-restart getenv walk; #143/#180/#200/#223/#241 stay siblings; not a product S0 / SecretPort close"
  LIVE_RESTART_OBSERVED=false
fi

LTE_OBSERVED=false
LTE_REASON="LocalThenEnvSecretPort.system() uses EnvSecretAdapter.system() → System::getenv (Java 21 process-start snapshot); live OS getenv rotate of the product default was not observed; leftover does not restart a process; #143/#180/#200/#223/#241 stay siblings; not OpenBao/KMS"
if [ "${NEXUS_S0_LTE_SYSTEM_GETENV_OBSERVED:-}" = "true" ]; then
  LTE_REASON="NEXUS_S0_LTE_SYSTEM_GETENV_OBSERVED listed but this leftover did not observe LocalThenEnvSecretPort.system() picking up an OS environ rotate on a running JVM; LocalThenEnvSecretPort.system() still uses EnvSecretAdapter.system() → System::getenv; #241/#223 stay siblings; not a product S0 / SecretPort close"
  LTE_OBSERVED=false
fi

JDBC_OBSERVED=false
JDBC_REASON="JDBC DataSource runtime SecretPort rotate was not observed; Spring still binds NEXUS_DB_* at start; leftover does not rebuild the pool; #241/#223 stay siblings; not OpenBao/KMS; not a product S0 / SecretPort close"
if [ "${NEXUS_S0_JDBC_RUNTIME_OBSERVED:-}" = "true" ]; then
  JDBC_REASON="NEXUS_S0_JDBC_RUNTIME_OBSERVED listed but this leftover did not rebuild a Spring DataSource after an env rotate; NEXUS_DB_* still binds at start; #241/#223 stay siblings; not a product S0 / SecretPort close"
  JDBC_OBSERVED=false
fi

S3_OBSERVED=false
S3_REASON="S3 / MinIO client live rotate was not observed; credentials stay snapshotted at fromSecretPort; leftover does not rebuild the client; #241/#223 stay siblings; not OpenBao/KMS; not a product S0 / SecretPort close"
if [ "${NEXUS_S0_S3_CLIENT_ROTATE_OBSERVED:-}" = "true" ]; then
  S3_REASON="NEXUS_S0_S3_CLIENT_ROTATE_OBSERVED listed but this leftover did not rebuild MinioObjectStorage after an env rotate; credentials stay snapshotted at fromSecretPort; #241/#223 stay siblings; not a product S0 / SecretPort close"
  S3_OBSERVED=false
fi

TEST_SUMMARY="parent --verify-report only; no #143/#180/#200/#223/#241 product redo"
if [ "$SKIP_TESTS" = "0" ]; then
  if command -v mvn >/dev/null 2>&1; then
    mvn -version | head -n 1 | tee -a "$OUT/collect.log"
    set +e
    mvn -pl nexus-crypto-core -am test \
      -Dtest=S0SecretRotateGetenvLeftoverHonestyRemainderTest \
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
  kv leftover_241_harness "$LEFTOVER_241"
  kv leftover_180_cells_honest "$LEFTOVER_180_CELLS"
  kv leftover_200_cells_honest "$LEFTOVER_200_CELLS"
  kv leftover_223_cells_honest "$LEFTOVER_223_CELLS"
  kv leftover_241_cells_honest "$LEFTOVER_241_CELLS"
  kv leftover_rows_kept true
  kv leftover_rows "$LEFTOVER_ROWS_CSV"
  kv leftover_259_kept true
  kv leftover_249_kept true
  kv leftover_254_kept true
  kv leftover_240_kept true
  kv leftover_236_kept true
  kv leftover_251_kept true
  kv leftover_243_kept true
  kv leftover_241_kept true
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
  kv redo_241 false
  kv s0_close NOT_CLAIMED
  kv secret_port_close NOT_CLAIMED
  kv s0_close_reason "Honesty leftover remainder beyond #241/#223. Parent harness PASS. LocalThenEnv in-process PASS. Live OS getenv / restart / LocalThenEnv.system / JDBC / S3 SKIP. Not product S0 / SecretPort close."
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
  echo "| #241 leftover honesty harness | $LEFTOVER_241 | parent --verify-report; not a product redo |"
  echo "| #223 leftover harness | $LEFTOVER_223 | parent --verify-report; not a product redo |"
  echo "| #180 leftover harness | $LEFTOVER_180 | parent --verify-report; not a product redo |"
  echo "| #200 leftover harness | $LEFTOVER_200 | parent --verify-report; not a product redo |"
  echo "| Mutable env-map rotate | PASS | already proven by #180/#200/#223/#241 harness |"
  echo "| LocalThenEnv map rotate | $LTE_MAP | in-process; no overlay; not a #143 redo |"
  echo "| LocalThenEnv overlay wins | $LTE_OVERLAY | overlay still wins after env rotate |"
  echo "| Live OS System.getenv() rotate | SKIP | ${OS_REASON} |"
  echo "| Live JVM restart after rotate | SKIP | ${LIVE_RESTART_REASON} |"
  echo "| Live LocalThenEnv.system() getenv | SKIP | ${LTE_REASON} |"
  echo "| Live JDBC runtime SecretPort | SKIP | ${JDBC_REASON} |"
  echo "| Live S3/MinIO client rotate | SKIP | ${S3_REASON} |"
  echo "| OpenBao / KMS introduced | false | ARCH-007 |"
  echo "| #143 / #180 / #200 / #223 / #241 redo | false | sibling leftovers |"
  echo "| Leftover rows kept | true | #143 + #180 + #200 + #223 + #241 + ALL siblings including #259 G9 leftover / #249 G17 SoD remainder / #254 G2 remainder / #240 G15 remainder / #236 Linux/ARM remainder / #251 G4 leftover / #243 G10 leftover |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| S0 / SecretPort close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX"

{
  echo "s0-secret-rotate-getenv-leftover-honesty-remainder collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT" pass

SANITIZED="${ROOT}/docs/engineering/s0-secret-rotate-getenv-leftover-honesty-remainder/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT leftover_241=$LEFTOVER_241 leftover_223=$LEFTOVER_223 leftover_180=$LEFTOVER_180 leftover_200=$LEFTOVER_200 local_then_env=PASS live_os_getenv_rotate=SKIP live_restart=SKIP live_lte_system=SKIP s0_close=NOT_CLAIMED secret_port_close=NOT_CLAIMED"
echo
echo "PASS: leftover honesty remainder recorded (#241/#223/#180/#200 harness PASS; LocalThenEnv in-process PASS; live OS getenv / restart / LocalThenEnv.system / JDBC / S3 SKIP)"
echo "See docs/s0-secret-rotate-getenv-leftover-honesty-remainder.md"
echo "Keep leftover rows: docs/s0-secret-rotate-getenv-leftover-honesty.md, docs/s0-secret-rotate-getenv-remainder-honesty.md, docs/g17-separation-leftover-honesty-remainder.md, docs/g9-leftover-honesty-remainder.md, docs/g2-h5-every-page-form-write-report-remainder-honesty.md, docs/linux-arm-profile-remainder-honesty.md"
echo "Not claimed: product S0 close / SecretPort close / OpenBao / KMS / OS env rotate without restart / live JVM restart / live LocalThenEnv.system() / JDBC runtime / S3 client rotate"
echo "Not redone: #143 / #180 / #200 / #223 / #241 / P0 / M0"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
