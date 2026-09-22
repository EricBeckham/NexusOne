#!/usr/bin/env bash
# S0+S1 leftover after #105: install / upgrade / rollback / offline-bundle / SBOM
# on THIS host only. Not a product Linux/ARM Profile close. No Docker.
#
# Honesty: an x86_64 host MUST mark every aarch64_* cell SKIP with a reason.
# profile_acceptance is always NOT_CLAIMED. G18 stays closed. AuditPort untouched.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
OUT=""
VERIFY_REPORT=""
SKIP_JAR=0
SKIP_SCHEMA=0
SKIP_OFFLINE=0
SKIP_SBOM=0
REQUIRE_JAR=0
REQUIRE_SCHEMA=0
KEEP_PG=0
PG15_BIN="${NEXUS_PROFILE_PG15_BIN:-/usr/lib/postgresql/15/bin}"
PGHOST="${NEXUS_PROFILE_PGHOST:-127.0.0.1}"
PGPORT="${NEXUS_PROFILE_PGPORT:-55442}"
PGUSER="${NEXUS_PROFILE_PGUSER:-$USER}"
PGDATA=""
LC_DB="${NEXUS_PROFILE_LC_DB:-nexusone_profile_lc}"
MINIMAL_MODULES="${NEXUS_PROFILE_MODULES:-isolation-kernel,nexus-common,nexus-crypto-core}"

usage() {
  cat <<'EOF'
Usage:
  scripts/linux-arm-profile/lifecycle.sh [--out DIR] [flags]
  scripts/linux-arm-profile/lifecycle.sh --verify-report FILE

Local leftover drill after the #105 collect/verify package:
  JAR install of the minimal module set, schema install→upgrade→rollback
  on a dedicated PostgreSQL 15 cluster (never :5432), Maven offline compile,
  and a Maven SBOM / dependency-tree. aarch64 is SKIP unless uname -m is
  aarch64/arm64. Never claims product Profile acceptance.

  --out DIR            Output directory (default: .local/linux-arm-profile-lifecycle/<utc>)
  --skip-jar           Do not package JARs
  --skip-schema        Do not start a dedicated PG cluster
  --skip-offline       Do not build an offline bundle / -o compile
  --skip-sbom          Do not generate a Maven SBOM or dependency tree
  --require-jar        Missing Maven or package failure is FAIL
  --require-schema     Missing PG 15 server binaries or drill failure is FAIL
  --keep-pg            Leave the dedicated cluster running
  --verify-report F    Honesty gate on an existing report.env

No Docker. Passwords are never written to the report.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) OUT="${2:?}"; shift 2 ;;
    --skip-jar) SKIP_JAR=1; shift ;;
    --skip-schema) SKIP_SCHEMA=1; shift ;;
    --skip-offline) SKIP_OFFLINE=1; shift ;;
    --skip-sbom) SKIP_SBOM=1; shift ;;
    --require-jar) REQUIRE_JAR=1; shift ;;
    --require-schema) REQUIRE_SCHEMA=1; shift ;;
    --keep-pg) KEEP_PG=1; shift ;;
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
  local required="schema_version timestamp_utc host_uname_m host_arch_family java_feature boot_parent_version boot_parent_match docker_used x86_64_install x86_64_upgrade x86_64_rollback x86_64_offline x86_64_sbom aarch64_install aarch64_upgrade aarch64_rollback aarch64_offline aarch64_sbom profile_acceptance g18_status audit_port_changed"
  local key
  for key in $required; do
    [ -n "$(report_get "$file" "$key")" ] || fail "missing key $key in $file"
  done

  local arch family java boot match profile docker_used g18 audit
  arch="$(report_get "$file" host_uname_m)"
  family="$(report_get "$file" host_arch_family)"
  java="$(report_get "$file" java_feature)"
  boot="$(report_get "$file" boot_parent_version)"
  match="$(report_get "$file" boot_parent_match)"
  profile="$(report_get "$file" profile_acceptance)"
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

  local arm_i arm_u arm_r arm_o arm_s x86_i
  arm_i="$(report_get "$file" aarch64_install)"
  arm_u="$(report_get "$file" aarch64_upgrade)"
  arm_r="$(report_get "$file" aarch64_rollback)"
  arm_o="$(report_get "$file" aarch64_offline)"
  arm_s="$(report_get "$file" aarch64_sbom)"
  x86_i="$(report_get "$file" x86_64_install)"

  case "$arch" in
    aarch64|arm64)
      [ "$family" = "aarch64" ] || fail "host_arch_family should be aarch64 on $arch"
      [ "$x86_i" = "SKIP" ] || fail "x86_64_install must be SKIP on aarch64 host"
      [ -n "$(report_get "$file" x86_64_skip_reason)" ] || fail "x86_64 SKIP needs a reason"
      if [ "$arm_i" = "SKIP" ] || [ "$arm_u" = "SKIP" ] || [ "$arm_r" = "SKIP" ] || [ "$arm_o" = "SKIP" ] || [ "$arm_s" = "SKIP" ]; then
        [ -n "$(report_get "$file" aarch64_skip_reason)" ] || fail "aarch64 SKIP needs a reason"
      fi
      ;;
    x86_64|amd64)
      [ "$family" = "x86_64" ] || fail "host_arch_family should be x86_64 on $arch"
      [ "$arm_i" = "SKIP" ] || fail "aarch64_install must be SKIP on x86_64 host (no fake ARM PASS)"
      [ "$arm_u" = "SKIP" ] || fail "aarch64_upgrade must be SKIP on x86_64 host"
      [ "$arm_r" = "SKIP" ] || fail "aarch64_rollback must be SKIP on x86_64 host"
      [ "$arm_o" = "SKIP" ] || fail "aarch64_offline must be SKIP on x86_64 host"
      [ "$arm_s" = "SKIP" ] || fail "aarch64_sbom must be SKIP on x86_64 host"
      [ -n "$(report_get "$file" aarch64_skip_reason)" ] || fail "aarch64 SKIP needs a reason"
      ;;
    *)
      [ "$arm_i" = "SKIP" ] || fail "unknown arch $arch cannot claim aarch64 PASS"
      [ "$x86_i" = "SKIP" ] || fail "unknown arch $arch cannot claim x86_64 PASS"
      ;;
  esac

  if grep -Eiq 'PASSWORD=|passwd=|secret=' "$file"; then
    fail "report must not contain password/secret material"
  fi
  echo "PASS: lifecycle report honesty gate ($file)"
}

if [ -n "$VERIFY_REPORT" ]; then
  verify_report "$VERIFY_REPORT"
  exit 0
fi

if [ -z "$OUT" ]; then
  OUT="$ROOT/.local/linux-arm-profile-lifecycle/$STAMP"
fi
mkdir -p "$OUT"/{logs,install/v1,offline-bundle,sbom,schema}
chmod 700 "$OUT" 2>/dev/null || true
PGDATA="${NEXUS_PROFILE_PGDATA:-$OUT/pgdata}"

log() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" | tee -a "$OUT/lifecycle.log"; }

# --- host inventory (same honesty as collect-and-verify.sh) ---
HOST_UNAME_M="$(uname -m)"
case "$HOST_UNAME_M" in
  aarch64|arm64) HOST_ARCH_FAMILY=aarch64 ;;
  x86_64|amd64) HOST_ARCH_FAMILY=x86_64 ;;
  *) HOST_ARCH_FAMILY=other ;;
esac

OS_PRETTY=unknown
if [ -r /etc/os-release ]; then
  # shellcheck disable=SC1091
  . /etc/os-release
  OS_PRETTY="${PRETTY_NAME:-unknown}"
fi

JAVA_FEATURE=missing
JAVA_OS_ARCH=missing
JAVA_VERSION_LINE=not-installed
if command -v java >/dev/null 2>&1; then
  JAVA_VERSION_LINE="$(java -version 2>&1 | head -n 1 | tr -d '\r')"
  JAVA_FEATURE="$(java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*java\.specification\.version[[:space:]]*=/{print $3; exit}')"
  JAVA_OS_ARCH="$(java -XshowSettings:properties -version 2>&1 | awk '/^[[:space:]]*os\.arch[[:space:]]*=/{print $3; exit}')"
fi

MAVEN_VERSION=not-installed
if command -v mvn >/dev/null 2>&1; then
  MAVEN_VERSION="$(mvn -B -version 2>&1 | sed 's/\x1b\[[0-9;]*m//g' | awk '/Apache Maven/{print $3; exit}')"
fi

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

JAVA_STATUS=SKIP
if [ "$JAVA_FEATURE" = "21" ]; then
  JAVA_STATUS=PASS
else
  JAVA_STATUS=FAIL
fi

DOCKER_PRESENT=false
DOCKER_USED=false
if command -v docker >/dev/null 2>&1; then
  DOCKER_PRESENT=true
fi

ARM_SKIP_REASON=""
X86_SKIP_REASON=""
if [ "$HOST_ARCH_FAMILY" = "x86_64" ]; then
  ARM_SKIP_REASON="host uname -m=${HOST_UNAME_M} (java os.arch=${JAVA_OS_ARCH}); no aarch64/arm64 CPU; Docker/QEMU user-mode not used (S0+S1 leftover forbids Docker). Cannot execute native aarch64 install/upgrade/rollback/offline/SBOM on this VM."
elif [ "$HOST_ARCH_FAMILY" = "aarch64" ]; then
  X86_SKIP_REASON="host uname -m=${HOST_UNAME_M} (java os.arch=${JAVA_OS_ARCH}); no x86_64 CPU; Docker/QEMU user-mode not used. Cannot execute native x86_64 install/upgrade/rollback/offline/SBOM on this host."
else
  ARM_SKIP_REASON="host uname -m=${HOST_UNAME_M} is neither x86_64 nor aarch64"
  X86_SKIP_REASON="$ARM_SKIP_REASON"
fi

refuse_forbidden_name() {
  local name="$1"
  if echo "$name" | grep -Eiq '(^|[^a-z0-9])haopan(_platform)?([^a-z0-9]|$)'; then
    fail "refusing name that mentions haopan: $name"
  fi
  case "$name" in
    postgres|template0|template1) fail "refusing system database name: $name" ;;
  esac
}

if [ "$PGPORT" = "5432" ]; then
  fail "refusing default product port 5432; lifecycle leftover uses a dedicated cluster (default 55442)"
fi
refuse_forbidden_name "$LC_DB"

# --- JAR install (this host only) ---
JAR_INSTALL=SKIP
JAR_INSTALL_REASON=""
JAR_SHA256=""
package_module() {
  local module="$1"
  local logfile="$OUT/logs/package-${module}.log"
  log "package ${module} on ${HOST_ARCH_FAMILY}"
  if [ "$module" = "isolation-kernel" ]; then
    (cd "$ROOT" && mvn -B -f modules/isolation-kernel/pom.xml -DskipTests package) >"$logfile" 2>&1
  else
    (cd "$ROOT" && mvn -B -pl "$module" -am -DskipTests package) >"$logfile" 2>&1
  fi
}

copy_built_jar() {
  local module="$1"
  local src=""
  if [ "$module" = "isolation-kernel" ]; then
    src="$(ls -1 "$ROOT/modules/isolation-kernel/target"/isolation-kernel-*.jar 2>/dev/null | grep -v -- '-sources.jar' | grep -v -- '-javadoc.jar' | head -n 1 || true)"
  else
    src="$(ls -1 "$ROOT/${module}/target"/${module}-*.jar 2>/dev/null | grep -v -- '-sources.jar' | grep -v -- '-javadoc.jar' | head -n 1 || true)"
  fi
  [ -n "$src" ] && [ -f "$src" ] || return 1
  cp -a "$src" "$OUT/install/v1/"
}

if [ "$SKIP_JAR" = "1" ]; then
  JAR_INSTALL_REASON="--skip-jar requested"
elif [ "$JAVA_STATUS" != "PASS" ]; then
  JAR_INSTALL=FAIL
  JAR_INSTALL_REASON="JDK feature version is ${JAVA_FEATURE}, product baseline is 21"
elif ! command -v mvn >/dev/null 2>&1; then
  JAR_INSTALL_REASON="mvn not installed on PATH"
  if [ "$REQUIRE_JAR" = "1" ]; then
    JAR_INSTALL=FAIL
  fi
else
  JAR_INSTALL=PASS
  IFS=','; set -- $MINIMAL_MODULES; unset IFS
  for module in "$@"; do
    module="$(printf '%s' "$module" | tr -d '[:space:]')"
    [ -n "$module" ] || continue
    if package_module "$module"; then
      if ! grep -Eq 'BUILD SUCCESS' "$OUT/logs/package-${module}.log"; then
        JAR_INSTALL=FAIL
        JAR_INSTALL_REASON="mvn package for $module did not print BUILD SUCCESS"
        break
      fi
      if ! copy_built_jar "$module"; then
        JAR_INSTALL=FAIL
        JAR_INSTALL_REASON="built JAR for $module not found under target/"
        break
      fi
      log "PASS package $module"
    else
      JAR_INSTALL=FAIL
      JAR_INSTALL_REASON="mvn package failed for $module (see logs/package-${module}.log)"
      log "FAIL package $module"
      break
    fi
  done
  if [ "$JAR_INSTALL" = "PASS" ]; then
    (cd "$OUT/install/v1" && sha256sum -- *.jar >SHA256SUMS)
    JAR_SHA256="$(tr '\n' ' ' <"$OUT/install/v1/SHA256SUMS" | sed 's/[[:space:]]\+$//')"
  fi
fi

# --- schema install / upgrade / rollback on a dedicated PG 15 cluster ---
SCHEMA_INSTALL=SKIP
SCHEMA_UPGRADE=SKIP
SCHEMA_ROLLBACK=SKIP
SCHEMA_REASON=""
PG_SERVER_VERSION=""
V001="$ROOT/nexus-bootstrap/src/main/resources/db/migration/V001__create_logical_schemas.sql"
V002="$ROOT/nexus-bootstrap/src/main/resources/db/migration/V002__create_table_registry.sql"
V003="$ROOT/nexus-bootstrap/src/main/resources/db/migration/V003__platform_runtime_meta.sql"

pg_bin() {
  local name="$1"
  if [ -x "$PG15_BIN/$name" ]; then
    printf '%s\n' "$PG15_BIN/$name"
    return 0
  fi
  return 1
}

stop_pg() {
  if [ -f "$PGDATA/postmaster.pid" ] && [ -x "$PG15_BIN/pg_ctl" ]; then
    "$PG15_BIN/pg_ctl" -D "$PGDATA" -m fast stop >/dev/null 2>&1 || true
  fi
}

start_pg() {
  export PGHOST PGPORT PGUSER
  if "$PG15_BIN/pg_isready" -h "$PGHOST" -p "$PGPORT" >/dev/null 2>&1; then
    log "PostgreSQL already accepting on ${PGHOST}:${PGPORT}"
    return 0
  fi
  if [ ! -f "$PGDATA/PG_VERSION" ]; then
    log "initdb dedicated cluster at $PGDATA"
    "$PG15_BIN/initdb" -D "$PGDATA" --username="$PGUSER" --auth-local=trust --auth-host=trust --no-instructions
    cat >>"$PGDATA/postgresql.conf" <<EOF
listen_addresses = '127.0.0.1'
port = ${PGPORT}
unix_socket_directories = '${PGDATA}'
logging_collector = off
EOF
  fi
  log "starting dedicated PostgreSQL 15 on ${PGHOST}:${PGPORT}"
  "$PG15_BIN/pg_ctl" -D "$PGDATA" -l "$OUT/logs/postgres.log" -w start -o "-p ${PGPORT} -k ${PGDATA} -h 127.0.0.1"
}

psql_q() {
  "$PG15_BIN/psql" -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -v ON_ERROR_STOP=1 "$@"
}

if [ "$SKIP_SCHEMA" = "1" ]; then
  SCHEMA_REASON="--skip-schema requested"
elif [ ! -x "$PG15_BIN/initdb" ] || [ ! -x "$PG15_BIN/pg_ctl" ] || [ ! -x "$PG15_BIN/psql" ] || [ ! -x "$PG15_BIN/pg_dump" ] || [ ! -x "$PG15_BIN/pg_restore" ]; then
  SCHEMA_REASON="PostgreSQL 15 server binaries missing under ${PG15_BIN} (need initdb/pg_ctl/psql/pg_dump/pg_restore). This leftover does not use Docker or the product :5432 cluster."
  if [ "$REQUIRE_SCHEMA" = "1" ]; then
    SCHEMA_INSTALL=FAIL
    SCHEMA_UPGRADE=FAIL
    SCHEMA_ROLLBACK=FAIL
  fi
elif [ ! -f "$V001" ] || [ ! -f "$V002" ] || [ ! -f "$V003" ]; then
  SCHEMA_REASON="bootstrap Flyway SQL V001–V003 not found; leftover applies those files only (not a full-app Flyway 13.7.0 migrate)"
  if [ "$REQUIRE_SCHEMA" = "1" ]; then
    SCHEMA_INSTALL=FAIL
    SCHEMA_UPGRADE=FAIL
    SCHEMA_ROLLBACK=FAIL
  fi
else
  SCHEMA_INSTALL=PASS
  SCHEMA_UPGRADE=PASS
  SCHEMA_ROLLBACK=PASS
  if ! start_pg; then
    SCHEMA_INSTALL=FAIL
    SCHEMA_UPGRADE=FAIL
    SCHEMA_ROLLBACK=FAIL
    SCHEMA_REASON="could not start dedicated PostgreSQL 15 on ${PGHOST}:${PGPORT}"
  else
    PG_SERVER_VERSION="$(psql_q -d postgres -Atc 'SHOW server_version;' | tr -d '\r')"
    if ! printf '%s' "$PG_SERVER_VERSION" | grep -Eq '^15(\.|$)'; then
      SCHEMA_INSTALL=SKIP
      SCHEMA_UPGRADE=SKIP
      SCHEMA_ROLLBACK=SKIP
      SCHEMA_REASON="dedicated server is not PostgreSQL 15 ($PG_SERVER_VERSION)"
    else
      haopan="$(psql_q -d postgres -Atc "SELECT datname FROM pg_database WHERE datname ILIKE '%haopan%';")"
      [ -z "$haopan" ] || fail "dedicated cluster unexpectedly contains haopan database(s): $haopan"

      log "schema INSTALL apply V001 on ${LC_DB}"
      psql_q -d postgres -c "DROP DATABASE IF EXISTS ${LC_DB};"
      psql_q -d postgres -c "CREATE DATABASE ${LC_DB} TEMPLATE template0 ENCODING 'UTF8';"
      psql_q -d "$LC_DB" -f "$V001" >"$OUT/logs/schema-install.log" 2>&1
      schemas="$(psql_q -d "$LC_DB" -Atc "SELECT nspname FROM pg_namespace WHERE nspname IN ('platform','iam','business_meta','business_data') ORDER BY 1;")"
      if [ "$(printf '%s\n' "$schemas" | grep -c .)" -ne 4 ]; then
        SCHEMA_INSTALL=FAIL
        SCHEMA_REASON="V001 did not create the four logical schemas (got: $schemas)"
      fi
      printf '%s\n' "$schemas" >"$OUT/schema/install-schemas.txt"

      if [ "$SCHEMA_INSTALL" = "PASS" ]; then
        log "schema snapshot after INSTALL"
        "$PG15_BIN/pg_dump" -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -Fc --no-owner --no-privileges \
          --file="$OUT/schema/install.dump" --dbname="$LC_DB"

        log "schema UPGRADE apply V002+V003"
        psql_q -d "$LC_DB" -f "$V002" >"$OUT/logs/schema-upgrade.log" 2>&1
        psql_q -d "$LC_DB" -f "$V003" >>"$OUT/logs/schema-upgrade.log" 2>&1
        manager="$(psql_q -d "$LC_DB" -Atc "SELECT meta_value FROM platform.runtime_meta WHERE meta_key='schema_manager';")"
        registry="$(psql_q -d "$LC_DB" -Atc "SELECT count(*) FROM platform.table_registry;")"
        if [ "$manager" != "flyway_static_only" ] || [ "$registry" -lt 2 ]; then
          SCHEMA_UPGRADE=FAIL
          SCHEMA_REASON="upgrade did not persist runtime_meta/table_registry (schema_manager=$manager registry=$registry)"
        fi
        printf 'schema_manager=%s\nregistry_rows=%s\n' "$manager" "$registry" >"$OUT/schema/upgrade-check.txt"
      fi

      if [ "$SCHEMA_INSTALL" = "PASS" ] && [ "$SCHEMA_UPGRADE" = "PASS" ]; then
        log "schema ROLLBACK restore INSTALL dump"
        psql_q -d postgres -c "DROP DATABASE IF EXISTS ${LC_DB};"
        psql_q -d postgres -c "CREATE DATABASE ${LC_DB} TEMPLATE template0 ENCODING 'UTF8';"
        "$PG15_BIN/pg_restore" -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" --no-owner --no-privileges \
          --dbname="$LC_DB" "$OUT/schema/install.dump" >"$OUT/logs/schema-rollback.log" 2>&1
        rolled_meta="$(psql_q -d "$LC_DB" -Atc "SELECT to_regclass('platform.runtime_meta');")"
        rolled_schemas="$(psql_q -d "$LC_DB" -Atc "SELECT count(*) FROM pg_namespace WHERE nspname IN ('platform','iam','business_meta','business_data');")"
        if [ -n "$rolled_meta" ] || [ "$rolled_schemas" -ne 4 ]; then
          SCHEMA_ROLLBACK=FAIL
          SCHEMA_REASON="rollback did not restore V001-only state (runtime_meta='$rolled_meta' schemas=$rolled_schemas)"
        fi
        printf 'runtime_meta=%s\nschemas=%s\n' "${rolled_meta:-null}" "$rolled_schemas" >"$OUT/schema/rollback-check.txt"
      fi
    fi
  fi
  if [ "$KEEP_PG" != "1" ]; then
    stop_pg
  fi
fi

if [ "$SCHEMA_INSTALL" = "FAIL" ] || [ "$SCHEMA_UPGRADE" = "FAIL" ] || [ "$SCHEMA_ROLLBACK" = "FAIL" ]; then
  if [ "$REQUIRE_SCHEMA" != "1" ] && [ "$SCHEMA_INSTALL" != "FAIL" ]; then
    :
  fi
fi

# --- offline bundle (Maven local-cache snapshot, not official offline media) ---
OFFLINE=SKIP
OFFLINE_REASON=""
OFFLINE_COUNT=0
if [ "$SKIP_OFFLINE" = "1" ]; then
  OFFLINE_REASON="--skip-offline requested"
elif [ "$JAR_INSTALL" != "PASS" ]; then
  OFFLINE_REASON="offline bundle needs a successful JAR install on this host (jar_install=${JAR_INSTALL}; ${JAR_INSTALL_REASON})"
elif ! command -v mvn >/dev/null 2>&1; then
  OFFLINE_REASON="mvn not installed on PATH"
else
  mkdir -p "$OUT/offline-bundle/jars" "$OUT/offline-bundle/deps"
  cp -a "$OUT/install/v1/"*.jar "$OUT/offline-bundle/jars/" 2>/dev/null || true
  log "copy runtime dependencies into offline-bundle/deps"
  if (cd "$ROOT" && mvn -B -pl nexus-crypto-core -am \
        org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies \
        -DoutputDirectory="$OUT/offline-bundle/deps" \
        -DincludeScope=runtime \
        -Dsilent=true) >"$OUT/logs/offline-copy-deps.log" 2>&1 \
     && (cd "$ROOT" && mvn -B -f modules/isolation-kernel/pom.xml \
        org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies \
        -DoutputDirectory="$OUT/offline-bundle/deps" \
        -DincludeScope=runtime \
        -Dsilent=true) >>"$OUT/logs/offline-copy-deps.log" 2>&1; then
    (cd "$OUT/offline-bundle" && find jars deps -type f | sort | xargs -r sha256sum >SHA256SUMS)
    OFFLINE_COUNT="$(find "$OUT/offline-bundle/jars" "$OUT/offline-bundle/deps" -type f | wc -l | tr -d ' ')"
    log "offline compile mvn -o (uses this host's local ~/.m2, not official offline media)"
    if (cd "$ROOT" && mvn -o -B -pl nexus-common,nexus-crypto-core -am -DskipTests compile) >"$OUT/logs/offline-compile.log" 2>&1 \
       && (cd "$ROOT" && mvn -o -B -f modules/isolation-kernel/pom.xml -DskipTests compile) >>"$OUT/logs/offline-compile.log" 2>&1; then
      OFFLINE=PASS
      OFFLINE_REASON="Maven local-cache offline compile of the minimal module set; NOT official offline media (no OS/JDK/PG/Keycloak/Flowable/MinIO packages, no signed digest board)"
    else
      OFFLINE=FAIL
      OFFLINE_REASON="mvn -o compile failed (see logs/offline-compile.log)"
    fi
  else
    OFFLINE=FAIL
    OFFLINE_REASON="maven-dependency-plugin copy-dependencies failed (see logs/offline-copy-deps.log)"
  fi
fi

# --- SBOM from Maven if the plugin is available; else dependency-tree ---
SBOM=SKIP
SBOM_TOOL=none
SBOM_REASON=""
SBOM_COMPONENTS=0
if [ "$SKIP_SBOM" = "1" ]; then
  SBOM_REASON="--skip-sbom requested"
elif ! command -v mvn >/dev/null 2>&1; then
  SBOM_REASON="mvn not installed on PATH; cannot generate a Maven SBOM or dependency tree"
elif [ "$JAR_INSTALL" != "PASS" ] && [ "$SKIP_JAR" = "1" ]; then
  SBOM_REASON="SBOM leftover expects a resolved Maven reactor on this host; --skip-jar left no package"
else
  log "try CycloneDX Maven plugin (one-off, does not change product poms)"
  if (cd "$ROOT" && mvn -B -N org.cyclonedx:cyclonedx-maven-plugin:2.9.1:makeAggregateBom \
        -DoutputDirectory="$OUT/sbom" \
        -DoutputName=nexusone-maven \
        -DoutputFormat=json \
        -DschemaVersion=1.5) >"$OUT/logs/sbom-cyclonedx.log" 2>&1 \
     && [ -f "$OUT/sbom/nexusone-maven.json" ]; then
    SBOM=PASS
    SBOM_TOOL=cyclonedx-maven-plugin:2.9.1
    SBOM_COMPONENTS="$(python3 - "$OUT/sbom/nexusone-maven.json" <<'PY'
import json, sys
doc = json.load(open(sys.argv[1], encoding="utf-8"))
print(len(doc.get("components") or []))
PY
)"
    SBOM_REASON="Maven CycloneDX aggregate of this reactor. NOT a product SBOM: no license review, no CVE disposition, no official download digest, poc/p0-10/supply-chain-evidence.csv stays not-collected"
  else
    log "CycloneDX unavailable or failed; fall back to mvn dependency:tree"
    if (cd "$ROOT" && mvn -B -pl nexus-crypto-core -am dependency:tree \
          -DoutputFile="$OUT/sbom/dependency-tree-crypto.txt") >"$OUT/logs/sbom-tree.log" 2>&1 \
       && (cd "$ROOT" && mvn -B -f modules/isolation-kernel/pom.xml dependency:tree \
          -DoutputFile="$OUT/sbom/dependency-tree-isolation-kernel.txt") >>"$OUT/logs/sbom-tree.log" 2>&1 \
       && [ -s "$OUT/sbom/dependency-tree-crypto.txt" ]; then
      SBOM=PASS
      SBOM_TOOL=maven-dependency-tree
      SBOM_COMPONENTS="$(grep -cE '^[|+\\]' "$OUT/sbom/dependency-tree-crypto.txt" "$OUT/sbom/dependency-tree-isolation-kernel.txt" 2>/dev/null | awk -F: '{s+=$NF} END {print s+0}')"
      SBOM_REASON="mvn dependency:tree of the minimal module set. CycloneDX plugin did not produce a BOM (see logs/sbom-cyclonedx.log). NOT a product SBOM; supply-chain-evidence.csv stays not-collected"
    else
      SBOM=SKIP
      SBOM_TOOL=none
      SBOM_REASON="neither cyclonedx-maven-plugin nor dependency:tree produced an artifact (see logs/sbom-*.log)"
    fi
  fi
fi

# --- assign THIS-host results to the matching arch column ---
X86_INSTALL=SKIP
X86_UPGRADE=SKIP
X86_ROLLBACK=SKIP
X86_OFFLINE=SKIP
X86_SBOM=SKIP
ARM_INSTALL=SKIP
ARM_UPGRADE=SKIP
ARM_ROLLBACK=SKIP
ARM_OFFLINE=SKIP
ARM_SBOM=SKIP

if [ "$HOST_ARCH_FAMILY" = "x86_64" ]; then
  X86_INSTALL="$JAR_INSTALL"
  X86_UPGRADE="$SCHEMA_UPGRADE"
  X86_ROLLBACK="$SCHEMA_ROLLBACK"
  X86_OFFLINE="$OFFLINE"
  X86_SBOM="$SBOM"
  if [ "$JAR_INSTALL" = "SKIP" ] && [ -n "$JAR_INSTALL_REASON" ]; then
    X86_SKIP_REASON="${X86_SKIP_REASON:+$X86_SKIP_REASON; }jar: $JAR_INSTALL_REASON"
  fi
elif [ "$HOST_ARCH_FAMILY" = "aarch64" ]; then
  ARM_INSTALL="$JAR_INSTALL"
  ARM_UPGRADE="$SCHEMA_UPGRADE"
  ARM_ROLLBACK="$SCHEMA_ROLLBACK"
  ARM_OFFLINE="$OFFLINE"
  ARM_SBOM="$SBOM"
  if [ "$JAR_INSTALL" = "SKIP" ] && [ -n "$JAR_INSTALL_REASON" ]; then
    ARM_SKIP_REASON="${ARM_SKIP_REASON:+$ARM_SKIP_REASON; }jar: $JAR_INSTALL_REASON"
  fi
fi

GIT_COMMIT="$(git -C "$ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
GIT_BRANCH="$(git -C "$ROOT" rev-parse --abbrev-ref HEAD 2>/dev/null || echo unknown)"

REPORT="$OUT/report.env"
{
  kv schema_version 1
  kv timestamp_utc "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  kv git_commit "$GIT_COMMIT"
  kv git_branch "$GIT_BRANCH"
  kv os_pretty "$OS_PRETTY"
  kv host_uname_m "$HOST_UNAME_M"
  kv host_arch_family "$HOST_ARCH_FAMILY"
  kv java_version_line "$JAVA_VERSION_LINE"
  kv java_feature "$JAVA_FEATURE"
  kv java_os_arch "$JAVA_OS_ARCH"
  kv java_status "$JAVA_STATUS"
  kv maven_version "$MAVEN_VERSION"
  kv boot_parent_ga "$BOOT_PARENT_GA"
  kv boot_parent_version "$BOOT_VERSION"
  kv boot_parent_match "$BOOT_PARENT_MATCH"
  kv docker_present "$DOCKER_PRESENT"
  kv docker_used "$DOCKER_USED"
  kv minimal_modules "$MINIMAL_MODULES"
  kv jar_install "$JAR_INSTALL"
  kv jar_install_reason "$JAR_INSTALL_REASON"
  kv schema_install "$SCHEMA_INSTALL"
  kv schema_upgrade "$SCHEMA_UPGRADE"
  kv schema_rollback "$SCHEMA_ROLLBACK"
  kv schema_reason "$SCHEMA_REASON"
  kv pg15_bin "$PG15_BIN"
  kv pg_listen "${PGHOST}:${PGPORT}"
  kv pg_server_version "$PG_SERVER_VERSION"
  kv lc_db "$LC_DB"
  kv offline "$OFFLINE"
  kv offline_reason "$OFFLINE_REASON"
  kv offline_file_count "$OFFLINE_COUNT"
  kv sbom "$SBOM"
  kv sbom_tool "$SBOM_TOOL"
  kv sbom_components "$SBOM_COMPONENTS"
  kv sbom_reason "$SBOM_REASON"
  kv x86_64_install "$X86_INSTALL"
  kv x86_64_upgrade "$X86_UPGRADE"
  kv x86_64_rollback "$X86_ROLLBACK"
  kv x86_64_offline "$X86_OFFLINE"
  kv x86_64_sbom "$X86_SBOM"
  kv x86_64_skip_reason "$X86_SKIP_REASON"
  kv aarch64_install "$ARM_INSTALL"
  kv aarch64_upgrade "$ARM_UPGRADE"
  kv aarch64_rollback "$ARM_ROLLBACK"
  kv aarch64_offline "$ARM_OFFLINE"
  kv aarch64_sbom "$ARM_SBOM"
  kv aarch64_skip_reason "$ARM_SKIP_REASON"
  kv profile_acceptance NOT_CLAIMED
  kv profile_acceptance_reason "Product Linux/ARM Profile needs a frozen approved Linux + native aarch64 CPU + JDK 21 + PostgreSQL 15.x row, plus install/upgrade/rollback/offline media/SBOM/CVE on THAT row. This leftover only drills JAR layout + V001–V003 schema apply/restore + Maven offline compile + Maven SBOM/tree on the current host. x86 PASS + ARM SKIP is not Profile acceptance. TARGET-ARM-01 stays not-tested. supply-chain-evidence.csv stays not-collected."
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
  echo "| JAR install ($MINIMAL_MODULES) | $JAR_INSTALL | ${JAR_INSTALL_REASON} |"
  echo "| Schema install (V001, dedicated PG 15) | $SCHEMA_INSTALL | ${SCHEMA_REASON} server=$PG_SERVER_VERSION |"
  echo "| Schema upgrade (V002+V003) | $SCHEMA_UPGRADE | not Flyway 13.7.0 full-app migrate |"
  echo "| Schema rollback (restore V001 dump) | $SCHEMA_ROLLBACK | not product PG major-version rollback |"
  echo "| Offline bundle + mvn -o | $OFFLINE | files=$OFFLINE_COUNT; ${OFFLINE_REASON} |"
  echo "| SBOM | $SBOM | tool=$SBOM_TOOL components=$SBOM_COMPONENTS; ${SBOM_REASON} |"
  echo "| x86_64 install/upgrade/rollback/offline/SBOM | $X86_INSTALL / $X86_UPGRADE / $X86_ROLLBACK / $X86_OFFLINE / $X86_SBOM | ${X86_SKIP_REASON} |"
  echo "| aarch64 install/upgrade/rollback/offline/SBOM | $ARM_INSTALL / $ARM_UPGRADE / $ARM_ROLLBACK / $ARM_OFFLINE / $ARM_SBOM | ${ARM_SKIP_REASON} |"
  echo "| Docker used | $DOCKER_USED | present=$DOCKER_PRESENT |"
  echo "| Product Profile acceptance | NOT_CLAIMED | See profile_acceptance_reason |"
} >"$MATRIX"

{
  echo "linux-arm-profile lifecycle leftover"
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
  fail "JDK feature version is ${JAVA_FEATURE}, product baseline is 21"
fi
if [ "$JAR_INSTALL" = "FAIL" ]; then
  fail "${JAR_INSTALL_REASON:-JAR install failed}"
fi
if [ "$REQUIRE_SCHEMA" = "1" ] && { [ "$SCHEMA_INSTALL" = "FAIL" ] || [ "$SCHEMA_UPGRADE" = "FAIL" ] || [ "$SCHEMA_ROLLBACK" = "FAIL" ]; }; then
  fail "${SCHEMA_REASON:-schema leftover failed}"
fi
if [ "$OFFLINE" = "FAIL" ]; then
  fail "${OFFLINE_REASON:-offline leftover failed}"
fi

log "DONE report=$REPORT profile_acceptance=NOT_CLAIMED"
echo "$OUT"
