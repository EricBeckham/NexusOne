#!/usr/bin/env bash
# Linux/ARM Profile leftover beyond #157: TARGET-ARM-01 / supply-chain
# csv still not-tested / not-collected PASS/SKIP honesty.
# Does not redo #105 collect, #127 lifecycle, or #157 honesty product code.
# Does not claim product Linux/ARM Profile close. G18 stays closed.
# No P0/M0 redo. Docker forbidden. x86 leftover is not an aarch64 PASS.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""

MATRIX="$ROOT/poc/p0-10/deployment-matrix.csv"
SUPPLY="$ROOT/poc/p0-10/supply-chain-evidence.csv"
COLLECT_ARTIFACT="$ROOT/docs/engineering/linux-arm-profile/agent-vm-x86_64.txt"
LIFECYCLE_ARTIFACT="$ROOT/docs/engineering/linux-arm-profile/agent-vm-x86_64-lifecycle.txt"
HONESTY_ARTIFACT="$ROOT/docs/engineering/linux-arm-profile/agent-vm-x86_64-honesty.txt"
README="$ROOT/docs/README.md"

REQUIRED_LEFTOVER_ROWS="linux-arm-profile-honesty linux-arm-profile-leftover-honesty g4-hot-purge-90d g4-hot-purge-90d-honesty g17-separation-of-duties g17-separation-honesty g1-fieldpolicy-mask-second-auth g1-fieldpolicy-mask-second-auth-honesty g10-identity-drift-honesty g15-native-bridge-honesty g4-pkce-spa-verifier-honesty s0-secret-getenv-snapshot-honesty g5-browser-failed-publish s0s1-live-redis-minio-isolation-honesty g6-recovery-port-dry-run g6-recovery-port-honesty g6-recovery-port-dry-run-honesty"

usage() {
  cat <<'EOF'
Usage:
  scripts/linux-arm-profile-leftover-honesty.sh [--out DIR]
  scripts/linux-arm-profile-leftover-honesty.sh --verify-report FILE

Beyond #157: TARGET-ARM-01 stays not-tested. supply-chain-evidence.csv
stays not-collected. Native aarch64 install/upgrade/rollback/offline/SBOM
is PASS only when this host is aarch64 AND the walk was observed. An x86
leftover note is not an aarch64 PASS. Does not re-run #105/#127/#157.
Does not claim Profile close.

  --out DIR           Output directory (default: .local/linux-arm-profile-leftover-honesty/<utc>)
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
  local required="schema_version timestamp_utc host_uname_m host_arch_family java_feature boot_parent_version boot_parent_match docker_used leftover_157_cells_honest leftover_rows_kept leftover_rows leftover_157_kept leftover_183_kept leftover_185_kept leftover_186_kept leftover_187_kept target_arm_01_status target_arm_01_install target_arm_01_upgrade target_arm_01_rollback target_arm_01_offline target_arm_01_crypto_international target_arm_01_crypto_gm supply_chain_rows supply_chain_status supply_chain_any_collected committed_collect_aarch64_build committed_collect_profile_acceptance committed_lifecycle_aarch64_install committed_lifecycle_profile_acceptance committed_honesty_target_arm_01 committed_honesty_supply_chain committed_honesty_profile_acceptance x86_lifecycle_note x86_note_extrapolated aarch64_pass_claimed native_aarch64_host native_aarch64_install native_aarch64_upgrade native_aarch64_rollback native_aarch64_offline native_aarch64_sbom native_aarch64_observed native_aarch64_skip_reason redone_105 redone_127 redone_157 collect_rerun lifecycle_rerun honesty_157_rerun profile_acceptance g18_status audit_port_changed p0_m0_redone"
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
  [ "$(report_get "$file" redone_105)" = "false" ] || fail "must not redo #105"
  [ "$(report_get "$file" redone_127)" = "false" ] || fail "must not redo #127"
  [ "$(report_get "$file" redone_157)" = "false" ] || fail "must not redo #157"
  [ "$(report_get "$file" collect_rerun)" = "false" ] || fail "collect_rerun must be false (do not redo #105)"
  [ "$(report_get "$file" lifecycle_rerun)" = "false" ] || fail "lifecycle_rerun must be false (do not redo #127)"
  [ "$(report_get "$file" honesty_157_rerun)" = "false" ] || fail "honesty_157_rerun must be false (do not redo #157)"
  [ "$(report_get "$file" profile_acceptance)" = "NOT_CLAIMED" ] || fail "profile_acceptance must stay NOT_CLAIMED"
  [ "$(report_get "$file" aarch64_pass_claimed)" = "false" ] || fail "aarch64_pass_claimed must be false"
  [ "$(report_get "$file" x86_note_extrapolated)" = "false" ] || fail "x86 leftover must not be extrapolated to aarch64 PASS"
  [ "$(report_get "$file" leftover_157_cells_honest)" = "PASS" ] || fail "leftover_157_cells_honest must be PASS"
  [ "$(report_get "$file" leftover_rows_kept)" = "true" ] || fail "leftover_rows_kept must stay true"
  [ "$(report_get "$file" leftover_157_kept)" = "true" ] || fail "leftover_157_kept must stay true"
  [ "$(report_get "$file" leftover_183_kept)" = "true" ] || fail "leftover_183_kept must stay true (keep g4-hot-purge-90d-honesty)"
  [ "$(report_get "$file" leftover_185_kept)" = "true" ] || fail "leftover_185_kept must stay true (keep s0s1-live-redis-minio-isolation-honesty)"
  [ "$(report_get "$file" leftover_186_kept)" = "true" ] || fail "leftover_186_kept must stay true (keep g6-recovery-port-dry-run-honesty)"
  [ "$(report_get "$file" leftover_187_kept)" = "true" ] || fail "leftover_187_kept must stay true (keep g17-separation-honesty)"

  local rows
  rows="$(report_get "$file" leftover_rows)"
  local required_row
  for required_row in $REQUIRED_LEFTOVER_ROWS; do
    echo "$rows" | grep -Fq "$required_row" || fail "leftover_rows must keep $required_row"
  done

  local cell
  for cell in target_arm_01_status target_arm_01_install target_arm_01_upgrade target_arm_01_rollback target_arm_01_offline target_arm_01_crypto_international target_arm_01_crypto_gm; do
    [ "$(report_get "$file" "$cell")" = "not-tested" ] || fail "$cell must stay not-tested"
  done
  [ "$(report_get "$file" supply_chain_status)" = "not-collected" ] || fail "supply_chain_status must stay not-collected"
  [ "$(report_get "$file" supply_chain_any_collected)" = "false" ] || fail "supply_chain_any_collected must be false"
  local supply_rows
  supply_rows="$(report_get "$file" supply_chain_rows)"
  [ "$supply_rows" -ge 1 ] 2>/dev/null || fail "supply_chain_rows must be a positive integer"

  [ "$(report_get "$file" committed_collect_aarch64_build)" = "SKIP" ] || fail "committed #105 aarch64_build must stay SKIP"
  [ "$(report_get "$file" committed_collect_profile_acceptance)" = "NOT_CLAIMED" ] || fail "committed #105 profile_acceptance must stay NOT_CLAIMED"
  [ "$(report_get "$file" committed_lifecycle_aarch64_install)" = "SKIP" ] || fail "committed #127 aarch64_install must stay SKIP"
  [ "$(report_get "$file" committed_lifecycle_profile_acceptance)" = "NOT_CLAIMED" ] || fail "committed #127 profile_acceptance must stay NOT_CLAIMED"
  [ "$(report_get "$file" committed_honesty_target_arm_01)" = "not-tested" ] || fail "committed #157 TARGET-ARM-01 must stay not-tested"
  [ "$(report_get "$file" committed_honesty_supply_chain)" = "not-collected" ] || fail "committed #157 supply-chain must stay not-collected"
  [ "$(report_get "$file" committed_honesty_profile_acceptance)" = "NOT_CLAIMED" ] || fail "committed #157 profile_acceptance must stay NOT_CLAIMED"

  local verdict
  for verdict in native_aarch64_install native_aarch64_upgrade native_aarch64_rollback native_aarch64_offline native_aarch64_sbom; do
    local value
    value="$(report_get "$file" "$verdict")"
    case "$value" in
      PASS|SKIP|FAIL) ;;
      *) fail "$verdict must be PASS/SKIP/FAIL" ;;
    esac
  done

  local arch
  arch="$(report_get "$file" host_uname_m)"
  case "$arch" in
    x86_64|amd64)
      [ "$(report_get "$file" host_arch_family)" = "x86_64" ] || fail "host_arch_family should be x86_64 on $arch"
      [ "$(report_get "$file" native_aarch64_host)" = "false" ] || fail "native_aarch64_host must be false on x86_64"
      ;;
    aarch64|arm64)
      [ "$(report_get "$file" host_arch_family)" = "aarch64" ] || fail "host_arch_family should be aarch64 on $arch"
      [ "$(report_get "$file" native_aarch64_host)" = "true" ] || fail "native_aarch64_host must be true on aarch64"
      ;;
    *)
      fail "unknown host_uname_m $arch"
      ;;
  esac

  if [ "$(report_get "$file" native_aarch64_install)" = "PASS" ] \
    || [ "$(report_get "$file" native_aarch64_upgrade)" = "PASS" ] \
    || [ "$(report_get "$file" native_aarch64_rollback)" = "PASS" ] \
    || [ "$(report_get "$file" native_aarch64_offline)" = "PASS" ] \
    || [ "$(report_get "$file" native_aarch64_sbom)" = "PASS" ]; then
    [ "$(report_get "$file" native_aarch64_host)" = "true" ] || fail "native aarch64 PASS requires native_aarch64_host=true"
    [ "$(report_get "$file" native_aarch64_observed)" = "true" ] || fail "native aarch64 PASS requires native_aarch64_observed=true"
    fail "native aarch64 PASS is not claimed by this leftover; TARGET-ARM-01 stays not-tested"
  fi
  if [ "$(report_get "$file" native_aarch64_install)" = "SKIP" ]; then
    [ -n "$(report_get "$file" native_aarch64_skip_reason)" ] || fail "native aarch64 SKIP needs native_aarch64_skip_reason"
    [ "$(report_get "$file" native_aarch64_observed)" != "true" ] || fail "SKIP cannot claim native_aarch64_observed=true"
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
  OUT="$ROOT/.local/linux-arm-profile-leftover-honesty/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

echo "== Linux/ARM Profile leftover honesty beyond #157"
echo "cwd=$ROOT"
echo "out=$OUT"
echo "TARGET-ARM-01 stays not-tested. supply-chain-evidence.csv stays not-collected."
echo "Does not redo #105/#127/#157. Not Profile close. G18 stays closed."
echo

[ -f "$MATRIX" ] || fail "missing $MATRIX"
[ -f "$SUPPLY" ] || fail "missing $SUPPLY"
[ -f "$COLLECT_ARTIFACT" ] || fail "missing committed #105 artifact $COLLECT_ARTIFACT"
[ -f "$LIFECYCLE_ARTIFACT" ] || fail "missing committed #127 artifact $LIFECYCLE_ARTIFACT"
[ -f "$HONESTY_ARTIFACT" ] || fail "missing committed #157 artifact $HONESTY_ARTIFACT"
[ -f "$README" ] || fail "missing $README"

HOST_UNAME_M="$(uname -m)"
case "$HOST_UNAME_M" in
  x86_64|amd64) HOST_ARCH_FAMILY=x86_64; NATIVE_AARCH64_HOST=false ;;
  aarch64|arm64) HOST_ARCH_FAMILY=aarch64; NATIVE_AARCH64_HOST=true ;;
  *) fail "unsupported uname -m=$HOST_UNAME_M" ;;
esac

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
HONESTY_TARGET="$(artifact_get "$HONESTY_ARTIFACT" target_arm_01_status)"
HONESTY_SUPPLY="$(artifact_get "$HONESTY_ARTIFACT" supply_chain_status)"
HONESTY_PROFILE="$(artifact_get "$HONESTY_ARTIFACT" profile_acceptance)"
HONESTY_AARCH64_CLAIMED="$(artifact_get "$HONESTY_ARTIFACT" aarch64_pass_claimed)"

[ "$COLLECT_ARM_BUILD" = "SKIP" ] || fail "committed #105 aarch64_build is $COLLECT_ARM_BUILD; must stay SKIP"
[ "$COLLECT_PROFILE" = "NOT_CLAIMED" ] || fail "committed #105 profile_acceptance is $COLLECT_PROFILE"
[ "$LIFE_ARM_INSTALL" = "SKIP" ] || fail "committed #127 aarch64_install is $LIFE_ARM_INSTALL; must stay SKIP"
[ "$LIFE_ARM_UPGRADE" = "SKIP" ] || fail "committed #127 aarch64_upgrade must stay SKIP"
[ "$LIFE_ARM_ROLLBACK" = "SKIP" ] || fail "committed #127 aarch64_rollback must stay SKIP"
[ "$LIFE_ARM_OFFLINE" = "SKIP" ] || fail "committed #127 aarch64_offline must stay SKIP"
[ "$LIFE_ARM_SBOM" = "SKIP" ] || fail "committed #127 aarch64_sbom must stay SKIP"
[ "$LIFE_PROFILE" = "NOT_CLAIMED" ] || fail "committed #127 profile_acceptance is $LIFE_PROFILE"
[ "$HONESTY_TARGET" = "not-tested" ] || fail "committed #157 target_arm_01_status is $HONESTY_TARGET"
[ "$HONESTY_SUPPLY" = "not-collected" ] || fail "committed #157 supply_chain_status is $HONESTY_SUPPLY"
[ "$HONESTY_PROFILE" = "NOT_CLAIMED" ] || fail "committed #157 profile_acceptance is $HONESTY_PROFILE"
[ "$HONESTY_AARCH64_CLAIMED" = "false" ] || fail "committed #157 aarch64_pass_claimed is $HONESTY_AARCH64_CLAIMED"

MISSING_ROWS=""
LEFTOVER_ROWS_KEPT=true
LEFTOVER_157_KEPT=false
LEFTOVER_183_KEPT=false
LEFTOVER_185_KEPT=false
LEFTOVER_186_KEPT=false
LEFTOVER_187_KEPT=false
for required_row in $REQUIRED_LEFTOVER_ROWS; do
  if grep -Fq "[${required_row}](./${required_row}.md)" "$README"; then
    if [ "$required_row" = "linux-arm-profile-honesty" ]; then
      LEFTOVER_157_KEPT=true
    fi
    if [ "$required_row" = "g4-hot-purge-90d-honesty" ]; then
      LEFTOVER_183_KEPT=true
    fi
    if [ "$required_row" = "s0s1-live-redis-minio-isolation-honesty" ]; then
      LEFTOVER_185_KEPT=true
    fi
    if [ "$required_row" = "g6-recovery-port-dry-run-honesty" ]; then
      LEFTOVER_186_KEPT=true
    fi
    if [ "$required_row" = "g17-separation-honesty" ]; then
      LEFTOVER_187_KEPT=true
    fi
  else
    LEFTOVER_ROWS_KEPT=false
    MISSING_ROWS="${MISSING_ROWS} ${required_row}"
  fi
done
[ "$LEFTOVER_ROWS_KEPT" = "true" ] || fail "docs/README.md dropped leftover row(s):$MISSING_ROWS"
[ "$LEFTOVER_157_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover linux-arm-profile-honesty (#157)"
[ "$LEFTOVER_183_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g4-hot-purge-90d-honesty (#183)"
[ "$LEFTOVER_185_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover s0s1-live-redis-minio-isolation-honesty (#185)"
[ "$LEFTOVER_186_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g6-recovery-port-dry-run-honesty (#186)"
[ "$LEFTOVER_187_KEPT" = "true" ] || fail "docs/README.md must keep sibling leftover g17-separation-honesty (#187)"

NATIVE_OBSERVED=false
if [ "${NEXUS_ARM_NATIVE_WALK_OBSERVED:-false}" = "true" ]; then
  NATIVE_OBSERVED=true
fi

if [ "$NATIVE_AARCH64_HOST" != "true" ]; then
  NATIVE_SKIP_REASON="this host is ${HOST_UNAME_M}; x86 leftover (#127/#157) is this-host only and is not an aarch64 PASS; TARGET-ARM-01 stays not-tested; no approved Linux+aarch64+JDK 21+PostgreSQL 15.x delivery row walk"
elif [ "$NATIVE_OBSERVED" != "true" ]; then
  NATIVE_SKIP_REASON="host is aarch64 but no approved delivery-row install/upgrade/rollback/offline/SBOM walk was observed; TARGET-ARM-01 stays not-tested"
else
  NATIVE_SKIP_REASON="native walk flag set, but this leftover does not flip TARGET-ARM-01 or claim Profile close"
fi

# Honesty leftover never fabricates native aarch64 PASS.
NATIVE_INSTALL=SKIP
NATIVE_UPGRADE=SKIP
NATIVE_ROLLBACK=SKIP
NATIVE_OFFLINE=SKIP
NATIVE_SBOM=SKIP
NATIVE_OBSERVED=false

X86_NOTE="committed #127 artifact host_uname_m=${LIFE_HOST:-unknown} x86_64_install=${LIFE_X86_INSTALL:-unset}; aarch64_install/upgrade/rollback/offline/sbom=SKIP; committed #157 TARGET-ARM-01=not-tested supply-chain=not-collected; this leftover does not re-run collect/lifecycle/#157; x86 leftover cells are this-host only and are not an aarch64 PASS"

LEFTOVER_ROWS_CSV="$(echo "$REQUIRED_LEFTOVER_ROWS" | tr ' ' ',')"

GIT_COMMIT="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
GIT_BRANCH="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"

{
  kv schema_version 1
  kv timestamp_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  kv git_commit "$GIT_COMMIT"
  kv git_branch "$GIT_BRANCH"
  kv host_uname_m "$HOST_UNAME_M"
  kv host_arch_family "$HOST_ARCH_FAMILY"
  kv java_version_line "$JAVA_VERSION_LINE"
  kv java_feature "$JAVA_FEATURE"
  kv boot_parent_version "$BOOT_VERSION"
  kv boot_parent_match "$BOOT_PARENT_MATCH"
  kv docker_used "$DOCKER_USED"
  kv leftover_157_cells_honest PASS
  kv leftover_rows_kept "$LEFTOVER_ROWS_KEPT"
  kv leftover_rows "$LEFTOVER_ROWS_CSV"
  kv leftover_157_kept "$LEFTOVER_157_KEPT"
  kv leftover_183_kept "$LEFTOVER_183_KEPT"
  kv leftover_185_kept "$LEFTOVER_185_KEPT"
  kv leftover_186_kept "$LEFTOVER_186_KEPT"
  kv leftover_187_kept "$LEFTOVER_187_KEPT"
  kv collect_rerun false
  kv lifecycle_rerun false
  kv honesty_157_rerun false
  kv redone_105 false
  kv redone_127 false
  kv redone_157 false
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
  kv committed_honesty_target_arm_01 "$HONESTY_TARGET"
  kv committed_honesty_supply_chain "$HONESTY_SUPPLY"
  kv committed_honesty_profile_acceptance "$HONESTY_PROFILE"
  kv x86_lifecycle_note "$X86_NOTE"
  kv x86_note_extrapolated false
  kv aarch64_pass_claimed false
  kv native_aarch64_host "$NATIVE_AARCH64_HOST"
  kv native_aarch64_install "$NATIVE_INSTALL"
  kv native_aarch64_upgrade "$NATIVE_UPGRADE"
  kv native_aarch64_rollback "$NATIVE_ROLLBACK"
  kv native_aarch64_offline "$NATIVE_OFFLINE"
  kv native_aarch64_sbom "$NATIVE_SBOM"
  kv native_aarch64_observed "$NATIVE_OBSERVED"
  kv native_aarch64_skip_reason "$NATIVE_SKIP_REASON"
  kv profile_acceptance NOT_CLAIMED
  kv profile_acceptance_reason "Honesty leftover beyond #157. TARGET-ARM-01 stays not-tested. supply-chain stays not-collected. x86 leftover is not aarch64 PASS. Not product Profile close."
  kv g18_status closed_unchanged
  kv audit_port_changed false
  kv p0_m0_redone false
} > "$OUT/report.env"

{
  echo "# Linux/ARM Profile leftover honesty beyond #157"
  echo
  echo "| Check | Result |"
  echo "|---|---|"
  echo "| leftover #157 cells still honest | PASS |"
  echo "| TARGET-ARM-01 | not-tested |"
  echo "| supply-chain-evidence.csv | not-collected ($SUPPLY_ROWS rows) |"
  echo "| native aarch64 install/upgrade/rollback/offline/SBOM | SKIP |"
  echo "| x86 leftover extrapolated to aarch64 | false |"
  echo "| leftover rows kept | $LEFTOVER_ROWS_CSV |"
  echo "| #105/#127/#157 redo | false |"
  echo "| product Profile acceptance | NOT_CLAIMED |"
  echo
  echo "Native SKIP reason: $NATIVE_SKIP_REASON"
  echo "Note: $X86_NOTE"
} > "$OUT/matrix.md"

verify_report "$OUT/report.env"
log "wrote $OUT/report.env"
echo
echo "PASS: leftover honesty recorded (TARGET-ARM-01 not-tested; supply-chain not-collected; native aarch64 SKIP)"
echo "See docs/linux-arm-profile-leftover-honesty.md"
echo "Keep sibling leftover: docs/linux-arm-profile-honesty.md"
echo "Not claimed: product Profile close / TARGET-ARM-01 tested / supply-chain collected / aarch64 PASS"
echo "out=$OUT"
