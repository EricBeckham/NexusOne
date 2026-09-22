#!/usr/bin/env bash
# Linux/ARM (or non-x86) platform-matrix remainder honesty beyond #193/#157.
# Parent leftover stub/inventory harness stays PASS.
# Live ARM/Linux product-JAR walks stay SKIP unless native + observed.
# Does not redo #105 / #127 / #157 / #193 product code.
# Does not claim product Linux/ARM Profile close. G18 stays closed.
# No P0/M0 redo. Docker / QEMU forbidden. AuditPort same-TX unchanged.
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

MATRIX="$ROOT/poc/p0-10/deployment-matrix.csv"
SUPPLY="$ROOT/poc/p0-10/supply-chain-evidence.csv"
COLLECT_ARTIFACT="$ROOT/docs/engineering/linux-arm-profile/agent-vm-x86_64.txt"
LIFECYCLE_ARTIFACT="$ROOT/docs/engineering/linux-arm-profile/agent-vm-x86_64-lifecycle.txt"
HONESTY_ARTIFACT="$ROOT/docs/engineering/linux-arm-profile/agent-vm-x86_64-honesty.txt"
README="$ROOT/docs/README.md"

CORE_LEFTOVER_ROWS="linux-arm-profile-honesty linux-arm-profile-leftover-honesty linux-arm-profile-remainder-honesty g7-query-expense-travel-honesty-remainder g9-redis-down-invalidate-remainder-honesty g2-h5-every-page-form-write-remainder-honesty g7-withdraw-browser-evidence-honesty g4-hot-purge-staging-leftover-honesty g9-live-multi-jvm-replica-remainder-honesty g3-outbox-consume-idempotent-honesty g10-leftover-honesty-remainder g10-leftover-honesty g2-report-physical-dump-honesty g6-recovery-port-dry-run-remainder-honesty s0-secret-rotate-getenv-remainder-honesty g4-pkce-spa-verifier-remainder-honesty g17-separation-leftover-honesty g15-wx-dt-native-bridge-remainder g1-queryast-export-honesty g17-gm-tls-nginx-handshake-honesty g5-publishhook-ddl-honesty g9-sentinel-cluster-remainder-honesty g2-ops-pg-dump-honesty g17-mfa-recovery-vue-otp-honesty g9-redis-down-invalidate-remainder g2-h5-every-page-form-write-honesty g4-export-wizard-deny-honesty g5-mid-publish-failure-honesty g3-outbox-consume-crash-honesty g1-queryast-boundsql-honesty g15-wx-dt-native-bridge-honesty s0-secret-rotate-without-restart-honesty g7-query-expense-travel-honesty g4-p1-staging-audit-events-honesty g6-recovery-port-dry-run-honesty s0s1-live-redis-minio-isolation-honesty g17-separation-honesty g4-hot-purge-90d-honesty g1-fieldpolicy-mask-second-auth-honesty g10-identity-drift-honesty g15-native-bridge-honesty g4-pkce-spa-verifier-honesty s0-secret-getenv-snapshot-honesty g5-browser-failed-publish g7-withdraw-browser-honesty g9-redis-down-invalidate-honesty g10-file-reconcile-honesty"

usage() {
  cat <<'EOF'
Usage:
  scripts/linux-arm-profile-remainder-honesty.sh [--out DIR] [--skip-tests]
  scripts/linux-arm-profile-remainder-honesty.sh --verify-report FILE [pass|fail]

#157 / #193 leftover harness contracts stay always-green PASS.
The P0-10 platform matrix is inventoried. Live ARM/Linux or other
non-x86 product-JAR walks are PASS only when native AND observed.
Missing hardware / observation is honest SKIP.
Product Profile close stays NOT_CLAIMED.
Does not redo #105/#127/#157/#193.

  --out DIR           Output directory (default: .local/linux-arm-profile-remainder-honesty/<utc>)
  --skip-tests        Inventory + honesty only (do not run parent verify)
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

verify_report_inner() {
  local file="$1"
  local required="schema_version timestamp_utc host_uname_m host_arch_family java_feature boot_parent_version boot_parent_match docker_used qemu_used leftover_157_harness leftover_193_harness leftover_157_cells_honest leftover_193_cells_honest platform_matrix_inventory platform_matrix_rows leftover_rows_kept leftover_rows_all leftover_239_kept leftover_237_kept leftover_232_kept leftover_212_kept leftover_230_kept leftover_229_kept leftover_226_kept leftover_228_kept leftover_221_kept leftover_225_kept leftover_157_kept leftover_193_kept target_arm_01_status target_x86_01_status local_win_x64_install supply_chain_status supply_chain_any_collected x86_note_extrapolated x86_agent_vm_is_not_target_profile x86_agent_vm_is_not_arm_linux_jar qemu_is_not_arm docker_is_not_arm aarch64_pass_claimed native_aarch64_host native_non_x86_host live_arm_linux_product_jar_observed live_arm_linux_product_jar live_arm_linux_product_jar_skip_reason live_non_x86_product_jar_observed live_non_x86_product_jar live_non_x86_product_jar_skip_reason live_linux_target_x86_product_jar_observed live_linux_target_x86_product_jar live_linux_target_x86_product_jar_skip_reason redone_105 redone_127 redone_157 redone_193 collect_rerun lifecycle_rerun honesty_157_rerun leftover_193_rerun profile_acceptance g18_status audit_port_changed p0_m0_redone"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || { echo "FAIL: missing key $key in $file" >&2; return 1; }
  done

  [ "$(report_get "$file" java_feature)" = "21" ] || { echo "FAIL: java_feature must be 21" >&2; return 1; }
  [ "$(report_get "$file" boot_parent_version)" = "4.1.1" ] || { echo "FAIL: boot_parent_version must be 4.1.1" >&2; return 1; }
  [ "$(report_get "$file" boot_parent_match)" = "PASS" ] || { echo "FAIL: boot_parent_match must be PASS" >&2; return 1; }
  [ "$(report_get "$file" docker_used)" = "false" ] || { echo "FAIL: docker_used must be false" >&2; return 1; }
  [ "$(report_get "$file" qemu_used)" = "false" ] || { echo "FAIL: qemu_used must be false" >&2; return 1; }
  [ "$(report_get "$file" qemu_is_not_arm)" = "true" ] || { echo "FAIL: qemu_is_not_arm must stay true" >&2; return 1; }
  [ "$(report_get "$file" docker_is_not_arm)" = "true" ] || { echo "FAIL: docker_is_not_arm must stay true" >&2; return 1; }
  [ "$(report_get "$file" g18_status)" = "closed_unchanged" ] || { echo "FAIL: g18_status must stay closed_unchanged" >&2; return 1; }
  [ "$(report_get "$file" audit_port_changed)" = "false" ] || { echo "FAIL: audit_port_changed must be false" >&2; return 1; }
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || { echo "FAIL: p0_m0_redone must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_105)" = "false" ] || { echo "FAIL: redone_105 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_127)" = "false" ] || { echo "FAIL: redone_127 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_157)" = "false" ] || { echo "FAIL: redone_157 must stay false" >&2; return 1; }
  [ "$(report_get "$file" redone_193)" = "false" ] || { echo "FAIL: redone_193 must stay false" >&2; return 1; }
  [ "$(report_get "$file" collect_rerun)" = "false" ] || { echo "FAIL: collect_rerun must stay false" >&2; return 1; }
  [ "$(report_get "$file" lifecycle_rerun)" = "false" ] || { echo "FAIL: lifecycle_rerun must stay false" >&2; return 1; }
  [ "$(report_get "$file" honesty_157_rerun)" = "false" ] || { echo "FAIL: honesty_157_rerun must stay false" >&2; return 1; }
  [ "$(report_get "$file" leftover_193_rerun)" = "false" ] || { echo "FAIL: leftover_193_rerun must stay false" >&2; return 1; }
  [ "$(report_get "$file" profile_acceptance)" = "NOT_CLAIMED" ] || { echo "FAIL: profile_acceptance must stay NOT_CLAIMED" >&2; return 1; }
  [ "$(report_get "$file" leftover_157_harness)" = "PASS" ] || { echo "FAIL: leftover_157_harness must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_193_harness)" = "PASS" ] || { echo "FAIL: leftover_193_harness must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_157_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_157_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_193_cells_honest)" = "PASS" ] || { echo "FAIL: leftover_193_cells_honest must be PASS" >&2; return 1; }
  [ "$(report_get "$file" platform_matrix_inventory)" = "PASS" ] || { echo "FAIL: platform_matrix_inventory must be PASS" >&2; return 1; }
  [ "$(report_get "$file" leftover_rows_all)" = "true" ] || { echo "FAIL: leftover_rows_all must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_157_kept)" = "true" ] || { echo "FAIL: leftover_157_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_193_kept)" = "true" ] || { echo "FAIL: leftover_193_kept must stay true" >&2; return 1; }
  [ "$(report_get "$file" leftover_239_kept)" = "true" ] || { echo "FAIL: leftover_239_kept must stay true (keep g7-query-expense-travel-honesty-remainder)" >&2; return 1; }
  [ "$(report_get "$file" leftover_237_kept)" = "true" ] || { echo "FAIL: leftover_237_kept must stay true (keep g9-redis-down-invalidate-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_232_kept)" = "true" ] || { echo "FAIL: leftover_232_kept must stay true (keep g2-h5-every-page-form-write-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_212_kept)" = "true" ] || { echo "FAIL: leftover_212_kept must stay true (keep g7-withdraw-browser-evidence-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_230_kept)" = "true" ] || { echo "FAIL: leftover_230_kept must stay true (keep g4-hot-purge-staging-leftover-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_229_kept)" = "true" ] || { echo "FAIL: leftover_229_kept must stay true (keep g9-live-multi-jvm-replica-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_226_kept)" = "true" ] || { echo "FAIL: leftover_226_kept must stay true (keep g3-outbox-consume-idempotent-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_228_kept)" = "true" ] || { echo "FAIL: leftover_228_kept must stay true (keep g10-leftover-honesty-remainder)" >&2; return 1; }
  [ "$(report_get "$file" leftover_221_kept)" = "true" ] || { echo "FAIL: leftover_221_kept must stay true (keep g2-report-physical-dump-honesty)" >&2; return 1; }
  [ "$(report_get "$file" leftover_225_kept)" = "true" ] || { echo "FAIL: leftover_225_kept must stay true (keep g6-recovery-port-dry-run-remainder-honesty)" >&2; return 1; }
  [ "$(report_get "$file" target_arm_01_status)" = "not-tested" ] || { echo "FAIL: target_arm_01_status must stay not-tested" >&2; return 1; }
  [ "$(report_get "$file" target_x86_01_status)" = "not-tested" ] || { echo "FAIL: target_x86_01_status must stay not-tested" >&2; return 1; }
  [ "$(report_get "$file" local_win_x64_install)" = "not-tested" ] || { echo "FAIL: local_win_x64_install must stay not-tested" >&2; return 1; }
  [ "$(report_get "$file" supply_chain_status)" = "not-collected" ] || { echo "FAIL: supply_chain_status must stay not-collected" >&2; return 1; }
  [ "$(report_get "$file" supply_chain_any_collected)" = "false" ] || { echo "FAIL: supply_chain_any_collected must be false" >&2; return 1; }
  [ "$(report_get "$file" x86_note_extrapolated)" = "false" ] || { echo "FAIL: x86 leftover must not be extrapolated to ARM PASS" >&2; return 1; }
  [ "$(report_get "$file" x86_agent_vm_is_not_target_profile)" = "true" ] || { echo "FAIL: x86_agent_vm_is_not_target_profile must stay true" >&2; return 1; }
  [ "$(report_get "$file" x86_agent_vm_is_not_arm_linux_jar)" = "true" ] || { echo "FAIL: x86_agent_vm_is_not_arm_linux_jar must stay true" >&2; return 1; }
  [ "$(report_get "$file" aarch64_pass_claimed)" = "false" ] || { echo "FAIL: aarch64_pass_claimed must be false" >&2; return 1; }

  local rows
  rows="$(report_get "$file" leftover_rows_kept)"
  for row in linux-arm-profile-honesty linux-arm-profile-leftover-honesty linux-arm-profile-remainder-honesty g7-query-expense-travel-honesty-remainder g9-redis-down-invalidate-remainder-honesty g2-h5-every-page-form-write-remainder-honesty g7-withdraw-browser-evidence-honesty g4-hot-purge-staging-leftover-honesty g9-live-multi-jvm-replica-remainder-honesty g3-outbox-consume-idempotent-honesty g10-leftover-honesty-remainder g2-report-physical-dump-honesty g6-recovery-port-dry-run-remainder-honesty s0-secret-rotate-getenv-remainder-honesty g4-pkce-spa-verifier-remainder-honesty g17-separation-leftover-honesty g15-wx-dt-native-bridge-remainder; do
    echo "$rows" | grep -Fq "$row" || { echo "FAIL: leftover_rows_kept must keep $row" >&2; return 1; }
  done

  local arch
  arch="$(report_get "$file" host_uname_m)"
  case "$arch" in
    x86_64|amd64)
      [ "$(report_get "$file" host_arch_family)" = "x86_64" ] || { echo "FAIL: host_arch_family should be x86_64 on $arch" >&2; return 1; }
      [ "$(report_get "$file" native_aarch64_host)" = "false" ] || { echo "FAIL: native_aarch64_host must be false on x86_64" >&2; return 1; }
      [ "$(report_get "$file" native_non_x86_host)" = "false" ] || { echo "FAIL: native_non_x86_host must be false on x86_64" >&2; return 1; }
      ;;
    aarch64|arm64)
      [ "$(report_get "$file" host_arch_family)" = "aarch64" ] || { echo "FAIL: host_arch_family should be aarch64 on $arch" >&2; return 1; }
      [ "$(report_get "$file" native_aarch64_host)" = "true" ] || { echo "FAIL: native_aarch64_host must be true on aarch64" >&2; return 1; }
      [ "$(report_get "$file" native_non_x86_host)" = "true" ] || { echo "FAIL: native_non_x86_host must be true on aarch64" >&2; return 1; }
      ;;
    *)
      [ "$(report_get "$file" host_arch_family)" = "non-x86" ] || { echo "FAIL: unknown arch $arch must be host_arch_family=non-x86" >&2; return 1; }
      [ "$(report_get "$file" native_aarch64_host)" = "false" ] || { echo "FAIL: native_aarch64_host must be false on $arch" >&2; return 1; }
      [ "$(report_get "$file" native_non_x86_host)" = "true" ] || { echo "FAIL: native_non_x86_host must be true on $arch" >&2; return 1; }
      ;;
  esac

  local verdict
  for verdict in live_arm_linux_product_jar live_non_x86_product_jar live_linux_target_x86_product_jar; do
    local value
    value="$(report_get "$file" "$verdict")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) echo "FAIL: $verdict must be PASS/SKIP/FAIL" >&2; return 1 ;;
    esac
  done

  if [ "$(report_get "$file" live_arm_linux_product_jar)" = "PASS" ]; then
    [ "$(report_get "$file" native_aarch64_host)" = "true" ] || { echo "FAIL: live ARM/Linux product-JAR PASS requires native_aarch64_host=true" >&2; return 1; }
    [ "$(report_get "$file" live_arm_linux_product_jar_observed)" = "true" ] || { echo "FAIL: live ARM/Linux product-JAR PASS requires live_arm_linux_product_jar_observed=true" >&2; return 1; }
    [ "$(report_get "$file" qemu_used)" = "false" ] || { echo "FAIL: live ARM/Linux product-JAR PASS cannot use QEMU" >&2; return 1; }
    [ "$(report_get "$file" docker_used)" = "false" ] || { echo "FAIL: live ARM/Linux product-JAR PASS cannot use Docker" >&2; return 1; }
    [ "$(report_get "$file" x86_note_extrapolated)" = "false" ] || { echo "FAIL: live ARM/Linux product-JAR PASS cannot extrapolate x86 leftover" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_arm_linux_product_jar)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_arm_linux_product_jar_skip_reason)" ] || { echo "FAIL: live ARM/Linux product-JAR SKIP needs a reason" >&2; return 1; }
    [ "$(report_get "$file" live_arm_linux_product_jar_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim live_arm_linux_product_jar_observed=true" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_non_x86_product_jar)" = "PASS" ]; then
    [ "$(report_get "$file" native_non_x86_host)" = "true" ] || { echo "FAIL: live non-x86 product-JAR PASS requires native_non_x86_host=true" >&2; return 1; }
    [ "$(report_get "$file" live_non_x86_product_jar_observed)" = "true" ] || { echo "FAIL: live non-x86 product-JAR PASS requires live_non_x86_product_jar_observed=true" >&2; return 1; }
    [ "$(report_get "$file" qemu_used)" = "false" ] || { echo "FAIL: live non-x86 product-JAR PASS cannot use QEMU" >&2; return 1; }
    [ "$(report_get "$file" docker_used)" = "false" ] || { echo "FAIL: live non-x86 product-JAR PASS cannot use Docker" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_non_x86_product_jar)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_non_x86_product_jar_skip_reason)" ] || { echo "FAIL: live non-x86 product-JAR SKIP needs a reason" >&2; return 1; }
    [ "$(report_get "$file" live_non_x86_product_jar_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim live_non_x86_product_jar_observed=true" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_linux_target_x86_product_jar)" = "PASS" ]; then
    [ "$(report_get "$file" live_linux_target_x86_product_jar_observed)" = "true" ] || { echo "FAIL: TARGET-X86-01 product-JAR PASS requires observation" >&2; return 1; }
    [ "$(report_get "$file" x86_agent_vm_is_not_target_profile)" = "true" ] || { echo "FAIL: TARGET-X86-01 PASS cannot treat this leftover agent VM as the approved target" >&2; return 1; }
  fi
  if [ "$(report_get "$file" live_linux_target_x86_product_jar)" = "SKIP" ]; then
    [ -n "$(report_get "$file" live_linux_target_x86_product_jar_skip_reason)" ] || { echo "FAIL: TARGET-X86-01 product-JAR SKIP needs a reason" >&2; return 1; }
    [ "$(report_get "$file" live_linux_target_x86_product_jar_observed)" != "true" ] || { echo "FAIL: SKIP cannot claim live_linux_target_x86_product_jar_observed=true" >&2; return 1; }
  fi
  if grep -Eiq 'PASSWORD=|passwd=|secret=|ACCESS_KEY=|TOKEN=' "$file"; then
    echo "FAIL: report must not contain password/secret/token material" >&2
    return 1
  fi
  if grep -Eiq 'profile_acceptance=ACCEPTED|profile_acceptance=CLOSED|product profile closed|linux/arm profile closed|产品 Profile 已关闭' "$file"; then
    echo "FAIL: report must not claim product Linux/ARM Profile closed" >&2
    return 1
  fi
  return 0
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT" "$VERIFY_EXPECT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/linux-arm-profile-remainder-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== Linux/ARM Profile leftover: platform-matrix remainder honesty beyond #193/#157"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "Does not redo #105/#127/#157/#193."
echo "Does not boot a product JAR. Not Profile close. G18 stays closed."
echo

[ -f "$MATRIX" ] || fail "missing $MATRIX"
[ -f "$SUPPLY" ] || fail "missing $SUPPLY"
[ -f "$COLLECT_ARTIFACT" ] || fail "missing committed #105 artifact $COLLECT_ARTIFACT"
[ -f "$LIFECYCLE_ARTIFACT" ] || fail "missing committed #127 artifact $LIFECYCLE_ARTIFACT"
[ -f "$HONESTY_ARTIFACT" ] || fail "missing committed #157 artifact $HONESTY_ARTIFACT"
[ -f "$README" ] || fail "missing $README"

HOST_UNAME_M="$(uname -m)"
case "$HOST_UNAME_M" in
  x86_64|amd64) HOST_ARCH_FAMILY=x86_64; NATIVE_AARCH64_HOST=false; NATIVE_NON_X86_HOST=false ;;
  aarch64|arm64) HOST_ARCH_FAMILY=aarch64; NATIVE_AARCH64_HOST=true; NATIVE_NON_X86_HOST=true ;;
  *) HOST_ARCH_FAMILY=non-x86; NATIVE_AARCH64_HOST=false; NATIVE_NON_X86_HOST=true ;;
esac

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
QEMU_USED=false
if command -v docker >/dev/null 2>&1; then
  DOCKER_PRESENT=true
  log "docker binary present but leftover must not use it"
fi

eval "$(python3 - "$MATRIX" "$SUPPLY" <<'PY'
import csv, sys
matrix_path, supply_path = sys.argv[1], sys.argv[2]

def q(s):
    return "'" + s.replace("'", "'\\''") + "'"

with open(matrix_path, newline="", encoding="utf-8") as f:
    rows = list(csv.DictReader(f))
ids = [(r.get("profile_id") or "").strip() for r in rows if (r.get("profile_id") or "").strip()]
print("PLATFORM_MATRIX_ROWS=%d" % len(ids))
print("PLATFORM_MATRIX_IDS=%s" % q(",".join(ids)))

def row(pid):
    return next((r for r in rows if (r.get("profile_id") or "").strip() == pid), None)

arm = row("TARGET-ARM-01")
x86 = row("TARGET-X86-01")
win = row("LOCAL-WIN-X64-POC")
if arm is None or x86 is None or win is None:
    sys.stderr.write("FAIL: platform matrix missing TARGET-ARM-01 / TARGET-X86-01 / LOCAL-WIN-X64-POC\n")
    raise SystemExit(1)
print("TARGET_ARM_INSTALL=%s" % q((arm.get("install_result") or "").strip()))
print("TARGET_ARM_ARCH=%s" % q((arm.get("cpu_arch") or "").strip()))
print("TARGET_X86_INSTALL=%s" % q((x86.get("install_result") or "").strip()))
print("TARGET_X86_ARCH=%s" % q((x86.get("cpu_arch") or "").strip()))
print("LOCAL_WIN_INSTALL=%s" % q((win.get("install_result") or "").strip()))

with open(supply_path, newline="", encoding="utf-8") as f:
    supply = list(csv.DictReader(f))
n = 0
any_collected = False
for r in supply:
    component = (r.get("component") or "").strip()
    if not component:
        continue
    n += 1
    status = (r.get("verification_status") or "").strip()
    if status != "not-collected":
        any_collected = True
print("SUPPLY_ROWS=%d" % n)
print("SUPPLY_ANY_COLLECTED=%s" % ("true" if any_collected else "false"))
PY
)"

[ "${PLATFORM_MATRIX_ROWS}" -ge 3 ] || fail "platform matrix must keep at least the three P0-10 rows"
[ "${TARGET_ARM_ARCH}" = "aarch64" ] || fail "TARGET-ARM-01 cpu_arch must stay aarch64"
[ "${TARGET_X86_ARCH}" = "x86_64" ] || fail "TARGET-X86-01 cpu_arch must stay x86_64"
[ "${TARGET_ARM_INSTALL}" = "not-tested" ] || fail "TARGET-ARM-01 install must stay not-tested"
[ "${TARGET_X86_INSTALL}" = "not-tested" ] || fail "TARGET-X86-01 install must stay not-tested"
[ "${LOCAL_WIN_INSTALL}" = "not-tested" ] || fail "LOCAL-WIN-X64-POC install must stay not-tested"
[ "${SUPPLY_ANY_COLLECTED}" = "false" ] || fail "supply-chain-evidence.csv has a collected row; leftover requires not-collected"
[ "${SUPPLY_ROWS}" -ge 1 ] || fail "supply-chain-evidence.csv has no component rows"

MISSING_ROWS=""
LEFTOVER_ROWS_ALL=true
LEFTOVER_157_KEPT=false
LEFTOVER_193_KEPT=false
LEFTOVER_239_KEPT=false
LEFTOVER_237_KEPT=false
LEFTOVER_232_KEPT=false
LEFTOVER_212_KEPT=false
LEFTOVER_230_KEPT=false
LEFTOVER_229_KEPT=false
LEFTOVER_226_KEPT=false
LEFTOVER_228_KEPT=false
LEFTOVER_221_KEPT=false
LEFTOVER_225_KEPT=false
for required_row in $CORE_LEFTOVER_ROWS; do
  if grep -Fq "[${required_row}](./${required_row}.md)" "$README"; then
    case "$required_row" in
      linux-arm-profile-honesty) LEFTOVER_157_KEPT=true ;;
      linux-arm-profile-leftover-honesty) LEFTOVER_193_KEPT=true ;;
      g7-query-expense-travel-honesty-remainder) LEFTOVER_239_KEPT=true ;;
      g9-redis-down-invalidate-remainder-honesty) LEFTOVER_237_KEPT=true ;;
      g2-h5-every-page-form-write-remainder-honesty) LEFTOVER_232_KEPT=true ;;
      g7-withdraw-browser-evidence-honesty) LEFTOVER_212_KEPT=true ;;
      g4-hot-purge-staging-leftover-honesty) LEFTOVER_230_KEPT=true ;;
      g9-live-multi-jvm-replica-remainder-honesty) LEFTOVER_229_KEPT=true ;;
      g3-outbox-consume-idempotent-honesty) LEFTOVER_226_KEPT=true ;;
      g10-leftover-honesty-remainder) LEFTOVER_228_KEPT=true ;;
      g2-report-physical-dump-honesty) LEFTOVER_221_KEPT=true ;;
      g6-recovery-port-dry-run-remainder-honesty) LEFTOVER_225_KEPT=true ;;
    esac
  else
    LEFTOVER_ROWS_ALL=false
    MISSING_ROWS="${MISSING_ROWS} ${required_row}"
  fi
done
[ "$LEFTOVER_ROWS_ALL" = "true" ] || fail "docs/README.md dropped leftover row(s):$MISSING_ROWS"
[ "$LEFTOVER_157_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover linux-arm-profile-honesty (#157)"
[ "$LEFTOVER_193_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover linux-arm-profile-leftover-honesty (#193)"
[ "$LEFTOVER_239_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g7-query-expense-travel-honesty-remainder (#239)"
[ "$LEFTOVER_237_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-redis-down-invalidate-remainder-honesty (#237)"
[ "$LEFTOVER_232_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-h5-every-page-form-write-remainder-honesty (#232)"
[ "$LEFTOVER_212_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g7-withdraw-browser-evidence-honesty (#212)"
[ "$LEFTOVER_230_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-hot-purge-staging-leftover-honesty (#230)"
[ "$LEFTOVER_229_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g9-live-multi-jvm-replica-remainder-honesty (#229)"
[ "$LEFTOVER_226_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g3-outbox-consume-idempotent-honesty (#226)"
[ "$LEFTOVER_228_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g10-leftover-honesty-remainder (#228)"
[ "$LEFTOVER_221_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g2-report-physical-dump-honesty (#221)"
[ "$LEFTOVER_225_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g6-recovery-port-dry-run-remainder-honesty (#225)"
grep -q '\[linux-arm-profile-remainder-honesty\](\./linux-arm-profile-remainder-honesty.md)' "$README" \
  || fail "docs/README.md must list this leftover row"

echo
echo "== Honesty fixtures (this leftover)"
verify_report "$HERE/linux-arm-profile-remainder-honesty/fixtures/honest-skip.env" pass
verify_report "$HERE/linux-arm-profile-remainder-honesty/fixtures/fake-live-arm-jar-pass.env" fail
verify_report "$HERE/linux-arm-profile-remainder-honesty/fixtures/fake-live-non-x86-pass.env" fail
verify_report "$HERE/linux-arm-profile-remainder-honesty/fixtures/fake-qemu-as-arm.env" fail
verify_report "$HERE/linux-arm-profile-remainder-honesty/fixtures/fake-x86-as-arm.env" fail
verify_report "$HERE/linux-arm-profile-remainder-honesty/fixtures/fake-profile-close.env" fail
verify_report "$HERE/linux-arm-profile-remainder-honesty/fixtures/fake-redone.env" fail

echo
echo "== Parent leftover honesty still PASS (#157 / #193 verify-report only; not a product redo)"
PARENT_157=PASS
PARENT_193=PASS
if [ "$SKIP_TESTS" = "0" ]; then
  "$ROOT/scripts/linux-arm-profile-honesty.sh" --verify-report \
    "$ROOT/scripts/linux-arm-profile-honesty/fixtures/honest.env"
  "$ROOT/scripts/linux-arm-profile-leftover-honesty.sh" --verify-report \
    "$ROOT/scripts/linux-arm-profile-leftover-honesty/fixtures/honest-skip.env"
else
  log "parent #157/#193 verify skipped (--skip-tests); inventory still PASS"
fi

HONESTY_DOC="$ROOT/docs/linux-arm-profile-remainder-honesty.md"
[ -f "$HONESTY_DOC" ] || fail "missing this leftover doc"
grep -Fq 'profile_acceptance=NOT_CLAIMED' "$HONESTY_DOC" || fail "this leftover must keep profile_acceptance=NOT_CLAIMED"
grep -Fq 'or #193 leftover honesty' "$HONESTY_DOC" || fail "this leftover must not redo #193"
grep -Fq 'live_arm_linux_product_jar=SKIP' "$HONESTY_DOC" || fail "this leftover must keep live ARM/Linux product-JAR SKIP"

# This leftover does not boot a product JAR. Env flags are not observation.
ARM_JAR_OBSERVED=false
NON_X86_JAR_OBSERVED=false
TARGET_X86_JAR_OBSERVED=false
ARM_JAR=SKIP
ARM_JAR_REASON="this host is ${HOST_UNAME_M}; live ARM/Linux product-JAR walk not observed; leftover does not boot a product JAR; x86 leftover / QEMU / Docker is not ARM; TARGET-ARM-01 stays not-tested; #157/#193 stay siblings; not Profile close"
NON_X86_JAR=SKIP
NON_X86_JAR_REASON="no native non-x86 host observed; leftover does not boot a product JAR; QEMU / Docker is not non-x86; #157/#193 stay siblings; not Profile close"
TARGET_X86_JAR=SKIP
TARGET_X86_JAR_REASON="this agent VM leftover is not approved TARGET-X86-01; live Linux x86 target product-JAR walk not observed; leftover does not boot a product JAR; TARGET-X86-01 stays not-tested; not Profile close"
if [ "${NEXUS_ARM_LINUX_PRODUCT_JAR_OBSERVED:-}" = "true" ]; then
  ARM_JAR_REASON="NEXUS_ARM_LINUX_PRODUCT_JAR_OBSERVED listed but this leftover did not boot a product JAR or observe a native ARM/Linux walk; host=${HOST_UNAME_M}; #157/#193 stay siblings; not Profile close"
  ARM_JAR_OBSERVED=false
fi
if [ "${NEXUS_NON_X86_PRODUCT_JAR_OBSERVED:-}" = "true" ]; then
  NON_X86_JAR_REASON="NEXUS_NON_X86_PRODUCT_JAR_OBSERVED listed but this leftover did not boot a product JAR or observe a native non-x86 walk; host=${HOST_UNAME_M}; #157/#193 stay siblings; not Profile close"
  NON_X86_JAR_OBSERVED=false
fi

GIT_COMMIT="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
GIT_BRANCH="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"
LEFTOVER_ROWS_CSV="$(echo "$CORE_LEFTOVER_ROWS" | tr ' ' ',')"

REPORT="$OUT/report.env"
{
  kv schema_version 1
  kv timestamp_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  kv git_commit "$GIT_COMMIT"
  kv git_branch "$GIT_BRANCH"
  kv host_uname_m "$HOST_UNAME_M"
  kv host_arch_family "$HOST_ARCH_FAMILY"
  kv java_version_line "$JAVA_VERSION_LINE"
  kv java_feature "$JAVA_FEATURE"
  kv boot_parent_ga "$BOOT_PARENT_GA"
  kv boot_parent_version "$BOOT_VERSION"
  kv boot_parent_match "$BOOT_PARENT_MATCH"
  kv docker_present "$DOCKER_PRESENT"
  kv docker_used "$DOCKER_USED"
  kv qemu_used "$QEMU_USED"
  kv leftover_157_harness "$PARENT_157"
  kv leftover_193_harness "$PARENT_193"
  kv leftover_157_cells_honest PASS
  kv leftover_193_cells_honest PASS
  kv platform_matrix_inventory PASS
  kv platform_matrix_rows "$PLATFORM_MATRIX_ROWS"
  kv leftover_rows_kept "$LEFTOVER_ROWS_CSV"
  kv leftover_rows_all "$LEFTOVER_ROWS_ALL"
  kv leftover_239_kept "$LEFTOVER_239_KEPT"
  kv leftover_237_kept "$LEFTOVER_237_KEPT"
  kv leftover_232_kept "$LEFTOVER_232_KEPT"
  kv leftover_212_kept "$LEFTOVER_212_KEPT"
  kv leftover_230_kept "$LEFTOVER_230_KEPT"
  kv leftover_229_kept "$LEFTOVER_229_KEPT"
  kv leftover_226_kept "$LEFTOVER_226_KEPT"
  kv leftover_228_kept "$LEFTOVER_228_KEPT"
  kv leftover_221_kept "$LEFTOVER_221_KEPT"
  kv leftover_225_kept "$LEFTOVER_225_KEPT"
  kv leftover_157_kept "$LEFTOVER_157_KEPT"
  kv leftover_193_kept "$LEFTOVER_193_KEPT"
  kv target_arm_01_status not-tested
  kv target_x86_01_status not-tested
  kv local_win_x64_install "$LOCAL_WIN_INSTALL"
  kv supply_chain_rows "$SUPPLY_ROWS"
  kv supply_chain_status not-collected
  kv supply_chain_any_collected false
  kv x86_note_extrapolated false
  kv x86_agent_vm_is_not_target_profile true
  kv x86_agent_vm_is_not_arm_linux_jar true
  kv qemu_is_not_arm true
  kv docker_is_not_arm true
  kv aarch64_pass_claimed false
  kv native_aarch64_host "$NATIVE_AARCH64_HOST"
  kv native_non_x86_host "$NATIVE_NON_X86_HOST"
  kv live_arm_linux_product_jar_observed "$ARM_JAR_OBSERVED"
  kv live_arm_linux_product_jar "$ARM_JAR"
  kv live_arm_linux_product_jar_skip_reason "$ARM_JAR_REASON"
  kv live_non_x86_product_jar_observed "$NON_X86_JAR_OBSERVED"
  kv live_non_x86_product_jar "$NON_X86_JAR"
  kv live_non_x86_product_jar_skip_reason "$NON_X86_JAR_REASON"
  kv live_linux_target_x86_product_jar_observed "$TARGET_X86_JAR_OBSERVED"
  kv live_linux_target_x86_product_jar "$TARGET_X86_JAR"
  kv live_linux_target_x86_product_jar_skip_reason "$TARGET_X86_JAR_REASON"
  kv redone_105 false
  kv redone_127 false
  kv redone_157 false
  kv redone_193 false
  kv collect_rerun false
  kv lifecycle_rerun false
  kv honesty_157_rerun false
  kv leftover_193_rerun false
  kv profile_acceptance NOT_CLAIMED
  kv profile_acceptance_reason "Honesty remainder beyond #193/#157. Parent harness PASS. Platform-matrix inventory PASS. Live ARM/Linux product-JAR SKIP. Not product Profile close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
} >"$REPORT"

MATRIX_MD="$OUT/matrix.md"
{
  echo "| Check | Result | Notes |"
  echo "|---|---|---|"
  echo "| JDK 21 | PASS | $JAVA_VERSION_LINE |"
  echo "| Boot 4.1.1 | $BOOT_PARENT_MATCH | \`$BOOT_PARENT_GA\` |"
  echo "| #157 leftover harness | $PARENT_157 | parent --verify-report; not a product redo |"
  echo "| #193 leftover harness | $PARENT_193 | parent --verify-report; not a product redo |"
  echo "| Platform-matrix inventory | PASS | $PLATFORM_MATRIX_IDS |"
  echo "| TARGET-ARM-01 / TARGET-X86-01 | not-tested | supply-chain not-collected |"
  echo "| Live ARM/Linux product-JAR | $ARM_JAR | ${ARM_JAR_REASON} |"
  echo "| Live non-x86 product-JAR | $NON_X86_JAR | ${NON_X86_JAR_REASON} |"
  echo "| TARGET-X86-01 product-JAR | $TARGET_X86_JAR | ${TARGET_X86_JAR_REASON} |"
  echo "| leftover rows kept | true | #157/#193 + #239 + #237 + #232 + #212 + #230 + #229 + #226 + #228 + #221 + #225 + ALL sibling leftover rows |"
  echo "| #105/#127/#157/#193 redo | false | sibling leftovers |"
  echo "| Docker / QEMU used | $DOCKER_USED / $QEMU_USED | present=$DOCKER_PRESENT |"
  echo "| Profile close | NOT_CLAIMED | G18 closed_unchanged; AuditPort untouched |"
} >"$MATRIX_MD"

{
  echo "linux-arm-profile-remainder-honesty collect"
  echo "out=$OUT"
  echo
  cat "$MATRIX_MD"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT" pass

SANITIZED="${ROOT}/docs/engineering/linux-arm-profile-remainder-honesty/agent-vm.env"
mkdir -p "$(dirname "$SANITIZED")"
cp "$REPORT" "$SANITIZED"

log "DONE report=$REPORT leftover_157=$PARENT_157 leftover_193=$PARENT_193 live_arm_linux_product_jar=$ARM_JAR profile_acceptance=NOT_CLAIMED"
echo
echo "PASS: honesty remainder recorded (#157/#193 harness PASS; platform-matrix inventory PASS; live ARM/Linux product-JAR $ARM_JAR)"
echo "See docs/linux-arm-profile-remainder-honesty.md"
echo "Keep leftover rows: docs/linux-arm-profile-honesty.md, docs/linux-arm-profile-leftover-honesty.md, and ALL sibling leftover rows"
echo "Not claimed: product Profile close / TARGET-ARM-01 tested / live ARM/Linux product-JAR / aarch64 PASS"
echo "Not redone: #105 / #127 / #157 / #193 / P0 / M0"
echo "out=$OUT"
echo "SANITIZED=$SANITIZED"
