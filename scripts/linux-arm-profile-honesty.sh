#!/usr/bin/env bash
# S0+S1 leftover: TARGET-ARM-01 / supply-chain-evidence.csv honesty.
# After #105 collect and #127 lifecycle. Does not re-run those scripts.
# Does not claim product Linux/ARM Profile close. Docker forbidden.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""

MATRIX="$ROOT/poc/p0-10/deployment-matrix.csv"
SUPPLY="$ROOT/poc/p0-10/supply-chain-evidence.csv"
COLLECT_ARTIFACT="$ROOT/docs/engineering/linux-arm-profile/agent-vm-x86_64.txt"
LIFECYCLE_ARTIFACT="$ROOT/docs/engineering/linux-arm-profile/agent-vm-x86_64-lifecycle.txt"

usage() {
  cat <<'EOF'
Usage:
  scripts/linux-arm-profile-honesty.sh [--out DIR]
  scripts/linux-arm-profile-honesty.sh --verify-report FILE

Inventory TARGET-ARM-01 (must stay not-tested) and
poc/p0-10/supply-chain-evidence.csv (must stay not-collected).
Quotes the committed #127 x86 leftover artifact. Does not re-run
collect-and-verify.sh or lifecycle.sh. Never claims Profile close.

  --out DIR           Output directory (default: .local/linux-arm-profile-honesty/<utc>)
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

artifact_get() {
  local file="$1" key="$2"
  awk -F= -v k="$key" '$1==k {sub(/^[^=]+=/, ""); print; exit}' "$file"
}

verify_report() {
  local file="$1"
  [ -f "$file" ] || fail "report not found: $file"
  local required="schema_version timestamp_utc host_uname_m host_arch_family java_feature boot_parent_version boot_parent_match docker_used collect_rerun lifecycle_rerun target_arm_01_status target_arm_01_install target_arm_01_upgrade target_arm_01_rollback target_arm_01_offline target_arm_01_crypto_international target_arm_01_crypto_gm supply_chain_rows supply_chain_status supply_chain_any_collected committed_collect_aarch64_build committed_collect_profile_acceptance committed_lifecycle_aarch64_install committed_lifecycle_profile_acceptance x86_lifecycle_note aarch64_pass_claimed profile_acceptance g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" p0_m0_redone)" = "false" ] || fail "p0_m0_redone must be false"
  [ "$(report_get "$file" collect_rerun)" = "false" ] || fail "collect_rerun must be false (do not redo #105)"
  [ "$(report_get "$file" lifecycle_rerun)" = "false" ] || fail "lifecycle_rerun must be false (do not redo #127)"
  [ "$(report_get "$file" profile_acceptance)" = "NOT_CLAIMED" ] || fail "profile_acceptance must stay NOT_CLAIMED"
  [ "$(report_get "$file" aarch64_pass_claimed)" = "false" ] || fail "aarch64_pass_claimed must be false"

  local cell
  for cell in target_arm_01_status target_arm_01_install target_arm_01_upgrade target_arm_01_rollback target_arm_01_offline target_arm_01_crypto_international target_arm_01_crypto_gm; do
    [ "$(report_get "$file" "$cell")" = "not-tested" ] || fail "$cell must stay not-tested"
  done
  [ "$(report_get "$file" supply_chain_status)" = "not-collected" ] || fail "supply_chain_status must stay not-collected"
  [ "$(report_get "$file" supply_chain_any_collected)" = "false" ] || fail "supply_chain_any_collected must be false"
  local rows
  rows="$(report_get "$file" supply_chain_rows)"
  [ "$rows" -ge 1 ] 2>/dev/null || fail "supply_chain_rows must be a positive integer"

  [ "$(report_get "$file" committed_collect_aarch64_build)" = "SKIP" ] || fail "committed #105 aarch64_build must stay SKIP"
  [ "$(report_get "$file" committed_collect_profile_acceptance)" = "NOT_CLAIMED" ] || fail "committed #105 profile_acceptance must stay NOT_CLAIMED"
  [ "$(report_get "$file" committed_lifecycle_aarch64_install)" = "SKIP" ] || fail "committed #127 aarch64_install must stay SKIP"
  [ "$(report_get "$file" committed_lifecycle_profile_acceptance)" = "NOT_CLAIMED" ] || fail "committed #127 profile_acceptance must stay NOT_CLAIMED"

  local arch
  arch="$(report_get "$file" host_uname_m)"
  case "$arch" in
    x86_64|amd64)
      [ "$(report_get "$file" host_arch_family)" = "x86_64" ] || fail "host_arch_family should be x86_64 on $arch"
      ;;
    aarch64|arm64)
      [ "$(report_get "$file" host_arch_family)" = "aarch64" ] || fail "host_arch_family should be aarch64 on $arch"
      ;;
    *)
      fail "unknown host_uname_m $arch"
      ;;
  esac

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
  OUT="$ROOT/.local/linux-arm-profile-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== Linux/ARM Profile TARGET-ARM-01 / supply-chain-evidence honesty leftover"
echo "cwd=$ROOT"
echo "Reads poc/p0-10 CSVs + committed #105/#127 artifacts. Does not re-run those scripts."
echo "Not claimed: product Profile close / TARGET-ARM-01 tested / supply-chain collected / aarch64 PASS"
echo

[ -f "$MATRIX" ] || fail "missing $MATRIX"
[ -f "$SUPPLY" ] || fail "missing $SUPPLY"
[ -f "$COLLECT_ARTIFACT" ] || fail "missing committed #105 artifact $COLLECT_ARTIFACT"
[ -f "$LIFECYCLE_ARTIFACT" ] || fail "missing committed #127 artifact $LIFECYCLE_ARTIFACT"

HOST_UNAME_M="$(uname -m)"
case "$HOST_UNAME_M" in
  x86_64|amd64) HOST_ARCH_FAMILY=x86_64 ;;
  aarch64|arm64) HOST_ARCH_FAMILY=aarch64 ;;
  *) fail "unsupported uname -m=$HOST_UNAME_M" ;;
esac

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

eval "$(python3 - "$MATRIX" "$SUPPLY" <<'PY'
import csv, sys
matrix_path, supply_path = sys.argv[1], sys.argv[2]

def q(s):
    return "'" + s.replace("'", "'\\''") + "'"

with open(matrix_path, newline="", encoding="utf-8") as f:
    rows = list(csv.DictReader(f))
arm = next((r for r in rows if (r.get("profile_id") or "").strip() == "TARGET-ARM-01"), None)
if arm is None:
    sys.stderr.write("FAIL: TARGET-ARM-01 row missing\n")
    raise SystemExit(1)
cells = {
    "install": (arm.get("install_result") or "").strip(),
    "upgrade": (arm.get("upgrade_result") or "").strip(),
    "rollback": (arm.get("rollback_result") or "").strip(),
    "offline": (arm.get("offline_result") or "").strip(),
    "crypto_i": (arm.get("crypto_international") or "").strip(),
    "crypto_g": (arm.get("crypto_gm") or "").strip(),
    "arch": (arm.get("cpu_arch") or "").strip(),
}
print("TARGET_ARM_INSTALL=%s" % q(cells["install"]))
print("TARGET_ARM_UPGRADE=%s" % q(cells["upgrade"]))
print("TARGET_ARM_ROLLBACK=%s" % q(cells["rollback"]))
print("TARGET_ARM_OFFLINE=%s" % q(cells["offline"]))
print("TARGET_ARM_CRYPTO_I=%s" % q(cells["crypto_i"]))
print("TARGET_ARM_CRYPTO_G=%s" % q(cells["crypto_g"]))
print("TARGET_ARM_CPU_ARCH=%s" % q(cells["arch"]))

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

[ "${TARGET_ARM_CPU_ARCH}" = "aarch64" ] || fail "TARGET-ARM-01 cpu_arch must stay aarch64 (row identity)"
for cell in TARGET_ARM_INSTALL TARGET_ARM_UPGRADE TARGET_ARM_ROLLBACK TARGET_ARM_OFFLINE TARGET_ARM_CRYPTO_I TARGET_ARM_CRYPTO_G; do
  eval "val=\${$cell}"
  [ "$val" = "not-tested" ] || fail "$cell is '$val'; TARGET-ARM-01 must stay not-tested"
done
[ "${SUPPLY_ANY_COLLECTED}" = "false" ] || fail "supply-chain-evidence.csv has a collected row; leftover requires not-collected"
[ "${SUPPLY_ROWS}" -ge 1 ] || fail "supply-chain-evidence.csv has no component rows"

COLLECT_ARM_BUILD="$(artifact_get "$COLLECT_ARTIFACT" aarch64_build)"
COLLECT_PROFILE="$(artifact_get "$COLLECT_ARTIFACT" profile_acceptance)"
LIFE_ARM_INSTALL="$(artifact_get "$LIFECYCLE_ARTIFACT" aarch64_install)"
LIFE_ARM_UPGRADE="$(artifact_get "$LIFECYCLE_ARTIFACT" aarch64_upgrade)"
LIFE_ARM_ROLLBACK="$(artifact_get "$LIFECYCLE_ARTIFACT" aarch64_rollback)"
LIFE_ARM_OFFLINE="$(artifact_get "$LIFECYCLE_ARTIFACT" aarch64_offline)"
LIFE_ARM_SBOM="$(artifact_get "$LIFECYCLE_ARTIFACT" aarch64_sbom)"
LIFE_PROFILE="$(artifact_get "$LIFECYCLE_ARTIFACT" profile_acceptance)"
LIFE_X86_INSTALL="$(artifact_get "$LIFECYCLE_ARTIFACT" x86_64_install)"
LIFE_HOST="$(artifact_get "$LIFECYCLE_ARTIFACT" host_uname_m)"

[ "$COLLECT_ARM_BUILD" = "SKIP" ] || fail "committed #105 aarch64_build is $COLLECT_ARM_BUILD; must stay SKIP"
[ "$COLLECT_PROFILE" = "NOT_CLAIMED" ] || fail "committed #105 profile_acceptance is $COLLECT_PROFILE"
[ "$LIFE_ARM_INSTALL" = "SKIP" ] || fail "committed #127 aarch64_install is $LIFE_ARM_INSTALL; must stay SKIP"
[ "$LIFE_ARM_UPGRADE" = "SKIP" ] || fail "committed #127 aarch64_upgrade must stay SKIP"
[ "$LIFE_ARM_ROLLBACK" = "SKIP" ] || fail "committed #127 aarch64_rollback must stay SKIP"
[ "$LIFE_ARM_OFFLINE" = "SKIP" ] || fail "committed #127 aarch64_offline must stay SKIP"
[ "$LIFE_ARM_SBOM" = "SKIP" ] || fail "committed #127 aarch64_sbom must stay SKIP"
[ "$LIFE_PROFILE" = "NOT_CLAIMED" ] || fail "committed #127 profile_acceptance is $LIFE_PROFILE"

X86_NOTE="committed #127 artifact host_uname_m=${LIFE_HOST:-unknown} x86_64_install=${LIFE_X86_INSTALL:-unset}; aarch64_install/upgrade/rollback/offline/sbom=SKIP; this leftover does not re-run lifecycle.sh; x86 leftover cells are this-host only and are not an aarch64 PASS"

{
  kv schema_version 1
  kv timestamp_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  kv host_uname_m "$HOST_UNAME_M"
  kv host_arch_family "$HOST_ARCH_FAMILY"
  kv java_feature "$JAVA_FEATURE"
  kv boot_parent_version "$BOOT_VERSION"
  kv boot_parent_match "$BOOT_PARENT_MATCH"
  kv docker_used "$DOCKER_USED"
  kv collect_rerun false
  kv lifecycle_rerun false
  kv target_arm_01_status not-tested
  kv target_arm_01_install "$TARGET_ARM_INSTALL"
  kv target_arm_01_upgrade "$TARGET_ARM_UPGRADE"
  kv target_arm_01_rollback "$TARGET_ARM_ROLLBACK"
  kv target_arm_01_offline "$TARGET_ARM_OFFLINE"
  kv target_arm_01_crypto_international "$TARGET_ARM_CRYPTO_I"
  kv target_arm_01_crypto_gm "$TARGET_ARM_CRYPTO_G"
  kv supply_chain_rows "$SUPPLY_ROWS"
  kv supply_chain_status not-collected
  kv supply_chain_any_collected false
  kv committed_collect_aarch64_build "$COLLECT_ARM_BUILD"
  kv committed_collect_profile_acceptance "$COLLECT_PROFILE"
  kv committed_lifecycle_aarch64_install "$LIFE_ARM_INSTALL"
  kv committed_lifecycle_profile_acceptance "$LIFE_PROFILE"
  kv committed_lifecycle_x86_64_install "${LIFE_X86_INSTALL:-unset}"
  kv x86_lifecycle_note "$X86_NOTE"
  kv aarch64_pass_claimed false
  kv profile_acceptance NOT_CLAIMED
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
} > "$OUT/report.env"

{
  echo "# Linux/ARM Profile TARGET-ARM-01 / supply-chain honesty"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| TARGET-ARM-01 | not-tested |"
  echo "| supply-chain-evidence.csv | not-collected ($SUPPLY_ROWS rows) |"
  echo "| committed #105 aarch64 | SKIP |"
  echo "| committed #127 aarch64 | SKIP |"
  echo "| local x86 lifecycle note | this-host only; not aarch64 PASS |"
  echo "| product Profile acceptance | NOT_CLAIMED |"
  echo
  echo "Note: $X86_NOTE"
} > "$OUT/matrix.md"

verify_report "$OUT/report.env"
log "wrote $OUT/report.env"
echo
echo "PASS: honesty leftover recorded (TARGET-ARM-01 not-tested; supply-chain not-collected; no aarch64 PASS)"
echo "See docs/linux-arm-profile-honesty.md"
echo "Not claimed: product Profile close / TARGET-ARM-01 tested / supply-chain collected / aarch64 PASS"
echo "out=$OUT"
