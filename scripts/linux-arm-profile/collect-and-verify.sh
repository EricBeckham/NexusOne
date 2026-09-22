#!/usr/bin/env bash
# S0+S1 remainder: Linux/ARM Profile evidence collector (not a Profile close).
#
# Records OS/arch, JDK 21, Boot 4.1.1 coordinates, PostgreSQL 15 client/server
# probes, and whether this host can build/test a minimal module set.
# aarch64 results are PASS only on a real aarch64/arm64 CPU. x86_64 hosts MUST
# mark ARM as SKIP with a reason. Never claims product Profile acceptance.
#
# No Docker. Does not rewrite P0/M0 foundations. Does not touch AuditPort.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
INVENTORY="$ROOT/poc/p0-10/collect-profile.sh"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
REQUIRE_BUILD=0
SKIP_BUILD=0
VERIFY_REPORT=""
MINIMAL_MODULES="${NEXUS_PROFILE_MODULES:-isolation-kernel,nexus-common,nexus-crypto-core}"

usage() {
  cat <<'EOF'
Usage:
  scripts/linux-arm-profile/collect-and-verify.sh [--out DIR] [--require-build|--skip-build]
  scripts/linux-arm-profile/collect-and-verify.sh --verify-report FILE

Collect host inventory + optional minimal-module build/test. Writes report.env
and matrix.md. aarch64 is SKIP unless uname -m is aarch64/arm64.

  --out DIR           Output directory (default: .local/linux-arm-profile/<utc>)
  --require-build     Missing Maven or a module build/test failure is FAIL
  --skip-build        Inventory only
  --verify-report F   Honesty gate on an existing report.env (CI cross-check)

Environment (optional, never printed):
  NEXUS_DB_URL / NEXUS_DB_USER / NEXUS_DB_PASSWORD
  PGHOST / PGPORT / PGDATABASE / PGUSER / PGPASSWORD
  NEXUS_PROFILE_MODULES   comma list (default isolation-kernel,nexus-common,nexus-crypto-core)
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --require-build) REQUIRE_BUILD=1; shift ;;
    --skip-build) SKIP_BUILD=1; shift ;;
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
  local required="schema_version timestamp_utc host_uname_m host_arch_family java_feature boot_parent_ga boot_parent_version boot_parent_match pg_client_status pg_server_live pg_server_test_ga docker_used x86_64_build x86_64_test aarch64_build aarch64_test profile_acceptance g18_status audit_port_changed"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || fail "missing key $key in $file"
  done

  local arch family java boot match profile arm_build arm_test x86_build docker_used g18 audit
  arch="$(report_get "$file" host_uname_m)"
  family="$(report_get "$file" host_arch_family)"
  java="$(report_get "$file" java_feature)"
  boot="$(report_get "$file" boot_parent_version)"
  match="$(report_get "$file" boot_parent_match)"
  profile="$(report_get "$file" profile_acceptance)"
  arm_build="$(report_get "$file" aarch64_build)"
  arm_test="$(report_get "$file" aarch64_test)"
  x86_build="$(report_get "$file" x86_64_build)"
  docker_used="$(report_get "$file" docker_used)"
  g18="$(report_get "$file" g18_status)"
  audit="$(report_get "$file" audit_port_changed)"

  [ "$java" = "21" ] || fail "java_feature must be 21, got $java"
  [ "$boot" = "4.1.1" ] || fail "boot_parent_version must be 4.1.1, got $boot"
  [ "$match" = "PASS" ] || fail "boot_parent_match must be PASS"
  [ "$profile" = "NOT_CLAIMED" ] || fail "profile_acceptance must stay NOT_CLAIMED (got $profile)"
  [ "$docker_used" = "false" ] || fail "docker_used must be false"
  [ "$g18" = "closed_unchanged" ] || fail "g18_status must stay closed_unchanged"
  [ "$audit" = "false" ] || fail "audit_port_changed must be false"

  case "$arch" in
    aarch64|arm64)
      [ "$family" = "aarch64" ] || fail "host_arch_family should be aarch64 on $arch"
      [ "$arm_build" != "SKIP" ] || [ -n "$(report_get "$file" aarch64_skip_reason)" ] || fail "aarch64 SKIP needs a reason"
      [ "$x86_build" = "SKIP" ] || fail "x86_64_build must be SKIP on aarch64 host"
      [ -n "$(report_get "$file" x86_64_skip_reason)" ] || fail "x86_64 SKIP needs a reason"
      ;;
    x86_64|amd64)
      [ "$family" = "x86_64" ] || fail "host_arch_family should be x86_64 on $arch"
      [ "$arm_build" = "SKIP" ] || fail "aarch64_build must be SKIP on x86_64 host (no fake ARM PASS)"
      [ "$arm_test" = "SKIP" ] || fail "aarch64_test must be SKIP on x86_64 host"
      [ -n "$(report_get "$file" aarch64_skip_reason)" ] || fail "aarch64 SKIP needs a reason"
      ;;
    *)
      [ "$arm_build" = "SKIP" ] || fail "unknown arch $arch cannot claim aarch64 PASS"
      [ "$x86_build" = "SKIP" ] || fail "unknown arch $arch cannot claim x86_64 PASS"
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
  OUT="$ROOT/.local/linux-arm-profile/$STAMP"
fi
mkdir -p "$OUT"
chmod 700 "$OUT" 2>/dev/null || true

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/collect.log"; }

# --- host inventory ---
HOST_UNAME_M="$(uname -m)"
case "$HOST_UNAME_M" in
  aarch64|arm64) HOST_ARCH_FAMILY=aarch64 ;;
  x86_64|amd64) HOST_ARCH_FAMILY=x86_64 ;;
  *) HOST_ARCH_FAMILY=other ;;
esac

OS_PRETTY=unknown
OS_ID=unknown
OS_VERSION_ID=unknown
if [ -r /etc/os-release ]; then
  # shellcheck disable=SC1091
  . /etc/os-release
  OS_PRETTY="${PRETTY_NAME:-unknown}"
  OS_ID="${ID:-unknown}"
  OS_VERSION_ID="${VERSION_ID:-unknown}"
fi

CPU_VENDOR=""
CPU_MODEL=""
if [ -r /proc/cpuinfo ]; then
  CPU_VENDOR="$(awk -F: '/vendor_id/{gsub(/^ /,"",$2); print $2; exit}' /proc/cpuinfo || true)"
  CPU_MODEL="$(awk -F: '/model name/{gsub(/^ /,"",$2); print $2; exit}' /proc/cpuinfo || true)"
fi
if [ -z "$CPU_MODEL" ] && command -v lscpu >/dev/null 2>&1; then
  CPU_MODEL="$(lscpu | awk -F: '/Model name/{gsub(/^ /,"",$2); print $2; exit}')"
  CPU_VENDOR="$(lscpu | awk -F: '/Vendor ID/{gsub(/^ /,"",$2); print $2; exit}')"
fi

JAVA_HOME_RESOLVED="${JAVA_HOME:-}"
JAVA_VERSION_LINE=not-installed
JAVA_FEATURE=missing
JAVA_OS_ARCH=missing
JAVA_VM_NAME=missing
if command -v java >/dev/null 2>&1; then
  JAVA_VERSION_LINE="$(java -version 2>&1 | head -n 1 | tr -d '\r')"
  JAVA_FEATURE="$(java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*java\.specification\.version[[:space:]]*=/{print $3; exit}')"
  JAVA_OS_ARCH="$(java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*os\.arch[[:space:]]*=/{print $3; exit}')"
  JAVA_VM_NAME="$(java -XshowSettings:properties -version 2>&1 | awk -F= '/^[[:space:]]*java\.vm\.name[[:space:]]*=/{gsub(/^ /,"",$2); print $2; exit}')"
  if [ -z "$JAVA_HOME_RESOLVED" ]; then
    JAVA_BIN="$(command -v java)"
    JAVA_HOME_RESOLVED="$(dirname "$(dirname "$(readlink -f "$JAVA_BIN" 2>/dev/null || echo "$JAVA_BIN")")")"
  fi
fi

MAVEN_VERSION=not-installed
if command -v mvn >/dev/null 2>&1; then
  MAVEN_VERSION="$(mvn -version 2>&1 | awk '/Apache Maven/{print $3; exit}')"
fi

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

FLYWAY_PINNED="$(python3 - "$ROOT/pom.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(sys.argv[1]).getroot()
props = root.find("m:properties", ns)
if props is None:
    print("missing")
    raise SystemExit(0)
n = props.find("m:flyway.version", ns)
print((n.text or "missing").strip() if n is not None else "missing")
PY
)"

ZONKY_GA=""
ZONKY_VERSION=""
eval "$(python3 - "$ROOT/modules/isolation-kernel/pom.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
ns = {"m": "http://maven.apache.org/POM/4.0.0"}
root = ET.parse(sys.argv[1]).getroot()
for dep in root.findall(".//m:dependency", ns):
    g = (dep.find("m:groupId", ns).text or "").strip() if dep.find("m:groupId", ns) is not None else ""
    a = (dep.find("m:artifactId", ns).text or "").strip() if dep.find("m:artifactId", ns) is not None else ""
    v = (dep.find("m:version", ns).text or "").strip() if dep.find("m:version", ns) is not None else ""
    if (g, a) == ("io.zonky.test.postgres", "embedded-postgres-binaries-bom"):
        print("ZONKY_GA=%r" % f"{g}:{a}:{v}")
        print("ZONKY_VERSION=%r" % v)
        break
else:
    print("ZONKY_GA='missing'")
    print("ZONKY_VERSION='missing'")
PY
)"

# --- PostgreSQL client / live server (never print secrets) ---
psql_cmd() {
  if command -v psql >/dev/null 2>&1; then
    command -v psql
    return 0
  fi
  local candidate
  for candidate in /usr/lib/postgresql/15/bin/psql /usr/lib/postgresql/16/bin/psql; do
    if [ -x "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

PSQL_BIN=""
PG_CLIENT_PSQL=not-installed
PG_CLIENT_PG_CONFIG=not-installed
PG_CLIENT_15_BIN=not-installed
PG_CLIENT_MAJOR=unknown
PG_CLIENT_STATUS=SKIP
PG_CLIENT_SKIP_REASON="psql not on PATH and no /usr/lib/postgresql/15/bin/psql"
if PSQL_BIN="$(psql_cmd)"; then
  PG_CLIENT_PSQL="$("$PSQL_BIN" --version 2>&1 | tr -d '\r')"
  if printf '%s' "$PG_CLIENT_PSQL" | grep -Eq '15(\.|$)'; then
    PG_CLIENT_MAJOR=15
    PG_CLIENT_STATUS=PASS
    PG_CLIENT_SKIP_REASON=""
  else
    PG_CLIENT_MAJOR="$(printf '%s' "$PG_CLIENT_PSQL" | awk '{for(i=1;i<=NF;i++) if($i ~ /^[0-9]+\./){split($i,a,"."); print a[1]; exit}}')"
    PG_CLIENT_STATUS=SKIP
    PG_CLIENT_SKIP_REASON="psql on PATH is not PostgreSQL 15 ($PG_CLIENT_PSQL)"
  fi
fi
if [ -x /usr/lib/postgresql/15/bin/psql ]; then
  PG_CLIENT_15_BIN="$(/usr/lib/postgresql/15/bin/psql --version 2>&1 | tr -d '\r')"
  PG_CLIENT_MAJOR=15
  PG_CLIENT_STATUS=PASS
  PG_CLIENT_SKIP_REASON=""
  PSQL_BIN=/usr/lib/postgresql/15/bin/psql
fi
if command -v pg_config >/dev/null 2>&1; then
  PG_CLIENT_PG_CONFIG="$(pg_config --version 2>&1 | tr -d '\r')"
elif [ -x /usr/lib/postgresql/15/bin/pg_config ]; then
  PG_CLIENT_PG_CONFIG="$(/usr/lib/postgresql/15/bin/pg_config --version 2>&1 | tr -d '\r')"
fi

PG_SERVER_LIVE=SKIP
PG_SERVER_LIVE_VERSION=""
PG_SERVER_LIVE_REASON="no live PostgreSQL probe (NEXUS_DB_URL / PGHOST unset and 127.0.0.1:5432 not queried without a client)"
if [ -n "$PSQL_BIN" ]; then
  export PGCONNECT_TIMEOUT="${PGCONNECT_TIMEOUT:-3}"
  if [ -z "${NEXUS_DB_URL:-}" ] && [ -z "${PGHOST:-}" ]; then
    export PGHOST=127.0.0.1
    export PGPORT="${PGPORT:-5432}"
    export PGDATABASE="${PGDATABASE:-postgres}"
  fi
  probe_server() {
    # Swallow connection errors; never echo env. Time out quickly.
    "$PSQL_BIN" -At -c 'SHOW server_version;' 2>/dev/null || return 1
  }
  if [ -n "${NEXUS_DB_URL:-}" ]; then
    # jdbc:postgresql://host:port/db  →  host/port/db for libpq (password stays in env)
    eval "$(python3 - <<'PY'
import os, re
url = os.environ.get("NEXUS_DB_URL", "")
m = re.match(r"jdbc:postgresql://([^:/]+)(?::(\d+))?/([^?;]+)", url)
if not m:
    print("PROBE_OK=0")
    raise SystemExit(0)
host, port, db = m.group(1), m.group(2) or "5432", m.group(3)
print("export PGHOST=%r" % host)
print("export PGPORT=%r" % port)
print("export PGDATABASE=%r" % db)
print("PROBE_OK=1")
PY
)"
    if [ "${PROBE_OK:-0}" = "1" ]; then
      export PGUSER="${NEXUS_DB_USER:-${PGUSER:-}}"
      if [ -n "${NEXUS_DB_PASSWORD+x}" ]; then
        export PGPASSWORD="${NEXUS_DB_PASSWORD}"
      fi
    fi
  fi
  if PG_SERVER_LIVE_VERSION="$(probe_server)"; then
    PG_SERVER_LIVE_VERSION="$(printf '%s' "$PG_SERVER_LIVE_VERSION" | tr -d '\r')"
    if printf '%s' "$PG_SERVER_LIVE_VERSION" | grep -Eq '^15(\.|$)'; then
      PG_SERVER_LIVE=PASS
      PG_SERVER_LIVE_REASON=""
    else
      PG_SERVER_LIVE=SKIP
      PG_SERVER_LIVE_REASON="reachable server is not PostgreSQL 15 ($PG_SERVER_LIVE_VERSION)"
    fi
  else
    PG_SERVER_LIVE=SKIP
    PG_SERVER_LIVE_REASON="psql could not connect (no listener, auth, or unset NEXUS_DB_*/PG*); live server version unknown"
  fi
fi
# Do not leak libpq secrets into later commands / the report.
unset PGPASSWORD || true

DOCKER_PRESENT=false
DOCKER_USED=false
if command -v docker >/dev/null 2>&1; then
  DOCKER_PRESENT=true
fi
QEMU_USERMODE=false
if [ -d /proc/sys/fs/binfmt_misc ]; then
  if ls /proc/sys/fs/binfmt_misc 2>/dev/null | grep -Eq 'qemu|aarch64|arm'; then
    QEMU_USERMODE=true
  fi
fi

# --- arch matrix (honesty first) ---
X86_BUILD=SKIP
X86_TEST=SKIP
X86_SKIP_REASON=""
ARM_BUILD=SKIP
ARM_TEST=SKIP
ARM_SKIP_REASON=""

if [ "$HOST_ARCH_FAMILY" = "x86_64" ]; then
  ARM_BUILD=SKIP
  ARM_TEST=SKIP
  ARM_SKIP_REASON="host uname -m=${HOST_UNAME_M} (java os.arch=${JAVA_OS_ARCH}); no aarch64/arm64 CPU; Docker/QEMU user-mode not used (S0+S1 remainder forbids Docker). Cannot execute native aarch64 build/test on this VM."
  X86_SKIP_REASON=""
elif [ "$HOST_ARCH_FAMILY" = "aarch64" ]; then
  X86_BUILD=SKIP
  X86_TEST=SKIP
  X86_SKIP_REASON="host uname -m=${HOST_UNAME_M} (java os.arch=${JAVA_OS_ARCH}); no x86_64 CPU; Docker/QEMU user-mode not used. Cannot execute native x86_64 build/test on this host."
  ARM_SKIP_REASON=""
else
  X86_SKIP_REASON="host uname -m=${HOST_UNAME_M} is neither x86_64 nor aarch64"
  ARM_SKIP_REASON="host uname -m=${HOST_UNAME_M} is neither x86_64 nor aarch64"
fi

JAVA_STATUS=SKIP
JAVA_SKIP_REASON=""
if [ "$JAVA_FEATURE" = "21" ]; then
  JAVA_STATUS=PASS
else
  JAVA_STATUS=FAIL
  JAVA_SKIP_REASON="JDK feature version is ${JAVA_FEATURE}, product baseline is 21"
fi

# --- optional minimal module build/test on THIS host only ---
run_module() {
  local module="$1"
  local logfile="$OUT/build-${module}.log"
  log "build+test ${module} on ${HOST_ARCH_FAMILY}"
  if [ "$module" = "isolation-kernel" ]; then
    (cd "$ROOT" && mvn -B -f modules/isolation-kernel/pom.xml test) >"$logfile" 2>&1
  else
    (cd "$ROOT" && mvn -B -pl "$module" -am test) >"$logfile" 2>&1
  fi
}

BUILD_STATUS=SKIP
TEST_STATUS=SKIP
BUILD_SKIP_REASON=""
if [ "$SKIP_BUILD" = "1" ]; then
  BUILD_SKIP_REASON="--skip-build requested"
elif [ "$JAVA_STATUS" != "PASS" ]; then
  BUILD_SKIP_REASON="$JAVA_SKIP_REASON"
  BUILD_STATUS=FAIL
  TEST_STATUS=FAIL
elif ! command -v mvn >/dev/null 2>&1; then
  BUILD_SKIP_REASON="mvn not installed on PATH"
  if [ "$REQUIRE_BUILD" = "1" ]; then
    BUILD_STATUS=FAIL
    TEST_STATUS=FAIL
  fi
else
  BUILD_STATUS=PASS
  TEST_STATUS=PASS
  IFS=','; set -- $MINIMAL_MODULES; unset IFS
  for module in "$@"; do
    module="$(printf '%s' "$module" | tr -d '[:space:]')"
    [ -n "$module" ] || continue
    if run_module "$module"; then
      log "PASS module $module"
      if ! grep -Eq 'BUILD SUCCESS' "$OUT/build-${module}.log"; then
        BUILD_STATUS=FAIL
        TEST_STATUS=FAIL
        BUILD_SKIP_REASON="mvn for $module did not print BUILD SUCCESS"
        break
      fi
      if grep -Eq 'Failures: [1-9]|Errors: [1-9]' "$OUT/build-${module}.log"; then
        BUILD_STATUS=FAIL
        TEST_STATUS=FAIL
        BUILD_SKIP_REASON="Surefire reported failures/errors in $module (see build-${module}.log)"
        break
      fi
    else
      BUILD_STATUS=FAIL
      TEST_STATUS=FAIL
      BUILD_SKIP_REASON="mvn test failed for $module (see build-${module}.log)"
      log "FAIL module $module"
      break
    fi
  done
fi

if [ "$HOST_ARCH_FAMILY" = "x86_64" ]; then
  X86_BUILD="$BUILD_STATUS"
  X86_TEST="$TEST_STATUS"
  if [ "$BUILD_STATUS" = "SKIP" ]; then
    X86_SKIP_REASON="$BUILD_SKIP_REASON"
  fi
elif [ "$HOST_ARCH_FAMILY" = "aarch64" ]; then
  ARM_BUILD="$BUILD_STATUS"
  ARM_TEST="$TEST_STATUS"
  if [ "$BUILD_STATUS" = "SKIP" ]; then
    ARM_SKIP_REASON="$BUILD_SKIP_REASON"
  fi
fi

GIT_COMMIT="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
GIT_BRANCH="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"

if [ -x "$INVENTORY" ]; then
  "$INVENTORY" >"$OUT/collect-profile.txt" 2>&1 || true
fi

REPORT="$OUT/report.env"
{
  kv schema_version 1
  kv timestamp_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  kv git_commit "$GIT_COMMIT"
  kv git_branch "$GIT_BRANCH"
  kv os_pretty "$OS_PRETTY"
  kv os_id "$OS_ID"
  kv os_version_id "$OS_VERSION_ID"
  kv kernel "$(uname -sr)"
  kv host_uname_m "$HOST_UNAME_M"
  kv host_arch_family "$HOST_ARCH_FAMILY"
  kv cpu_vendor "${CPU_VENDOR:-unknown}"
  kv cpu_model "${CPU_MODEL:-unknown}"
  kv java_home "$JAVA_HOME_RESOLVED"
  kv java_version_line "$JAVA_VERSION_LINE"
  kv java_feature "$JAVA_FEATURE"
  kv java_os_arch "$JAVA_OS_ARCH"
  kv java_vm_name "$JAVA_VM_NAME"
  kv java_status "$JAVA_STATUS"
  kv maven_version "$MAVEN_VERSION"
  kv boot_parent_ga "$BOOT_PARENT_GA"
  kv boot_parent_version "$BOOT_VERSION"
  kv boot_parent_match "$BOOT_PARENT_MATCH"
  kv flyway_pinned "$FLYWAY_PINNED"
  kv pg_client_psql "$PG_CLIENT_PSQL"
  kv pg_client_pg_config "$PG_CLIENT_PG_CONFIG"
  kv pg_client_15_bin "$PG_CLIENT_15_BIN"
  kv pg_client_major "$PG_CLIENT_MAJOR"
  kv pg_client_status "$PG_CLIENT_STATUS"
  kv pg_client_skip_reason "$PG_CLIENT_SKIP_REASON"
  kv pg_server_live "$PG_SERVER_LIVE"
  kv pg_server_live_version "${PG_SERVER_LIVE_VERSION:-}"
  kv pg_server_live_reason "$PG_SERVER_LIVE_REASON"
  kv pg_server_test_ga "$ZONKY_GA"
  kv pg_server_test_version "$ZONKY_VERSION"
  kv docker_present "$DOCKER_PRESENT"
  kv docker_used "$DOCKER_USED"
  kv qemu_usermode "$QEMU_USERMODE"
  kv minimal_modules "$MINIMAL_MODULES"
  kv x86_64_build "$X86_BUILD"
  kv x86_64_test "$X86_TEST"
  kv x86_64_skip_reason "$X86_SKIP_REASON"
  kv aarch64_build "$ARM_BUILD"
  kv aarch64_test "$ARM_TEST"
  kv aarch64_skip_reason "$ARM_SKIP_REASON"
  kv profile_acceptance NOT_CLAIMED
  kv profile_acceptance_reason "Product Linux/ARM Profile needs native aarch64 hardware plus install/upgrade/rollback/offline/SBOM on an approved OS/CPU/JDK/PG combo. This remainder only inventories and builds a minimal module set on the current host. ARM SKIP or x86-only PASS is not Profile acceptance."
  kv g18_status closed_unchanged
  kv audit_port_changed false
} >"$REPORT"

MATRIX="$OUT/matrix.md"
{
  echo "| Check | Result | Notes |"
  echo "|---|---|---|"
  echo "| OS / arch | $HOST_ARCH_FAMILY ($HOST_UNAME_M) | $OS_PRETTY |"
  echo "| JDK 21 | $JAVA_STATUS | $JAVA_VERSION_LINE; os.arch=$JAVA_OS_ARCH |"
  echo "| Boot 4.1.1 coordinates | $BOOT_PARENT_MATCH | \`$BOOT_PARENT_GA\` |"
  echo "| PostgreSQL 15 client | $PG_CLIENT_STATUS | ${PG_CLIENT_PSQL}; ${PG_CLIENT_SKIP_REASON} |"
  echo "| PostgreSQL 15 live server | $PG_SERVER_LIVE | ${PG_SERVER_LIVE_VERSION:-} ${PG_SERVER_LIVE_REASON} |"
  echo "| PostgreSQL 15 test-time (Zonky) | recorded | \`$ZONKY_GA\` |"
  echo "| x86_64 build/test ($MINIMAL_MODULES) | $X86_BUILD / $X86_TEST | ${X86_SKIP_REASON} |"
  echo "| aarch64 build/test | $ARM_BUILD / $ARM_TEST | ${ARM_SKIP_REASON} |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| Product Profile acceptance | NOT_CLAIMED | See profile_acceptance_reason |"
} >"$MATRIX"

# Human summary (no secrets)
{
  echo "linux-arm-profile collect-and-verify"
  echo "out=$OUT"
  echo
  cat "$MATRIX"
  echo
  echo "report=$REPORT"
} | tee "$OUT/summary.txt"

verify_report "$REPORT"

if [ "$BOOT_PARENT_MATCH" != "PASS" ]; then
  fail "root pom parent is not org.springframework.boot:spring-boot-starter-parent:4.1.1"
fi
if [ "$JAVA_STATUS" = "FAIL" ]; then
  fail "$JAVA_SKIP_REASON"
fi
if [ "$BUILD_STATUS" = "FAIL" ]; then
  fail "${BUILD_SKIP_REASON:-minimal module build/test failed}"
fi

log "DONE report=$REPORT profile_acceptance=NOT_CLAIMED"
echo "$OUT"
