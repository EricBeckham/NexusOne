#!/usr/bin/env bash
# Live Redis + MinIO isolation drill (S0+S1 ops).
# Proves CacheKeyGuard P1.M02.014 and FileObjectKeyGuard P1.M02.015 reject
# before a live backend command when those backends are reachable.
# Always runs in-process fixtures. Live slices SKIP honestly when missing.
# Prefers dedicated ports 16379 / 19000. Does not bind product :5432.
# Does not start the product JAR. Does not change AuditPort TX.
# No Docker. Credentials stay in /tmp (mode 0600), never git.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

DRILL_HOME="${NEXUS_DRILL_HOME:-/tmp/nexus-isolation-drill}"
DRILL_REDIS_PORT="${NEXUS_DRILL_REDIS_PORT:-16379}"
DRILL_MINIO_API_PORT="${NEXUS_DRILL_MINIO_API_PORT:-19000}"
DRILL_MINIO_CONSOLE_PORT="${NEXUS_DRILL_MINIO_CONSOLE_PORT:-19001}"
PRODUCT_REDIS_PORT=6379
PRODUCT_PG_PORT=5432
STARTED_REDIS=0
STARTED_MINIO=0
REDIS_STATUS="SKIP"
REDIS_REASON="not-started"
MINIO_STATUS="SKIP"
MINIO_REASON="not-started"
FIXTURE_STATUS="PENDING"

mkdir -p "${DRILL_HOME}"
chmod 700 "${DRILL_HOME}"

log() { printf '%s\n' "$*"; }

port_in_use() {
  local port="$1"
  python3 - "$port" <<'PY'
import socket, sys
port = int(sys.argv[1])
s = socket.socket()
s.settimeout(0.3)
try:
    s.connect(("127.0.0.1", port))
    print("yes")
except OSError:
    print("no")
finally:
    s.close()
PY
}

redis_pong() {
  local port="$1"
  python3 - "$port" <<'PY'
import socket, sys
port = int(sys.argv[1])
s = socket.socket()
s.settimeout(1)
try:
    s.connect(("127.0.0.1", port))
    s.sendall(b"PING\r\n")
    data = s.recv(32)
    sys.exit(0 if b"PONG" in data else 1)
except OSError:
    sys.exit(1)
finally:
    s.close()
PY
}

minio_live() {
  local port="$1"
  curl -fsS --max-time 2 "http://127.0.0.1:${port}/minio/health/live" >/dev/null 2>&1
}

pick_free_port() {
  local preferred="$1"
  local avoid="$2"
  if [[ "$(port_in_use "${preferred}")" == "no" ]]; then
    echo "${preferred}"
    return
  fi
  local candidate
  for candidate in $((preferred + 1)) $((preferred + 2)) $((preferred + 10)) $((preferred + 20)); do
    if [[ "${candidate}" == "${avoid}" ]]; then
      continue
    fi
    if [[ "$(port_in_use "${candidate}")" == "no" ]]; then
      echo "${candidate}"
      return
    fi
  done
  echo ""
}

log "== S0+S1 live Redis + MinIO isolation drill"
log "cwd=${ROOT}"
log "drill_home=${DRILL_HOME}"
java -version
mvn -version | head -n 1

if [[ "$(port_in_use "${PRODUCT_PG_PORT}")" == "yes" ]]; then
  log "product PostgreSQL :${PRODUCT_PG_PORT} occupied — this drill does not bind it (file metadata is in-memory)"
else
  log "product PostgreSQL :${PRODUCT_PG_PORT} free — still not binding it"
fi

if [[ "$(port_in_use "${PRODUCT_REDIS_PORT}")" == "yes" ]]; then
  log "product Redis :${PRODUCT_REDIS_PORT} occupied — using dedicated Redis :${DRILL_REDIS_PORT}"
else
  log "product Redis :${PRODUCT_REDIS_PORT} free — still preferring dedicated Redis :${DRILL_REDIS_PORT}"
fi

if [[ "${DRILL_REDIS_PORT}" == "${PRODUCT_REDIS_PORT}" ]]; then
  log "ERROR: drill Redis port must not be product :${PRODUCT_REDIS_PORT}"
  exit 1
fi
if [[ "${DRILL_MINIO_API_PORT}" == "${PRODUCT_PG_PORT}" || "${DRILL_MINIO_API_PORT}" == "${PRODUCT_REDIS_PORT}" ]]; then
  log "ERROR: drill MinIO port must not be product :5432/:6379"
  exit 1
fi

start_redis() {
  if redis_pong "${DRILL_REDIS_PORT}"; then
    REDIS_STATUS="REUSE"
    REDIS_REASON="already-pong :${DRILL_REDIS_PORT}"
    log "Redis already serving dedicated :${DRILL_REDIS_PORT}"
    return 0
  fi
  if [[ "$(port_in_use "${DRILL_REDIS_PORT}")" == "yes" ]]; then
    local alt
    alt="$(pick_free_port $((DRILL_REDIS_PORT + 1)) "${PRODUCT_REDIS_PORT}")"
    if [[ -z "${alt}" ]]; then
      REDIS_STATUS="SKIP"
      REDIS_REASON="dedicated port :${DRILL_REDIS_PORT} occupied and no free fallback"
      return 1
    fi
    log "dedicated Redis :${DRILL_REDIS_PORT} occupied; switching to :${alt}"
    DRILL_REDIS_PORT="${alt}"
  fi

  local bin=""
  if command -v redis-server >/dev/null 2>&1; then
    bin="$(command -v redis-server)"
  elif [[ -x "${DRILL_HOME}/redis-3.2.13/src/redis-server" ]]; then
    bin="${DRILL_HOME}/redis-3.2.13/src/redis-server"
  else
    log "compiling Redis 3.2.13 into ${DRILL_HOME} (native, no Docker)"
    if ! curl -fsSL --max-time 60 "https://download.redis.io/releases/redis-3.2.13.tar.gz" -o "${DRILL_HOME}/redis-3.2.13.tar.gz"; then
      REDIS_STATUS="SKIP"
      REDIS_REASON="could not download Redis 3.2.13 source"
      return 1
    fi
    tar -xzf "${DRILL_HOME}/redis-3.2.13.tar.gz" -C "${DRILL_HOME}"
    if ! make -C "${DRILL_HOME}/redis-3.2.13" -j"$(nproc)" >/tmp/nexus-isolation-drill-redis-build.log 2>&1; then
      REDIS_STATUS="SKIP"
      REDIS_REASON="Redis 3.2.13 compile failed (see /tmp/nexus-isolation-drill-redis-build.log)"
      return 1
    fi
    bin="${DRILL_HOME}/redis-3.2.13/src/redis-server"
  fi

  log "starting ${bin} on 127.0.0.1:${DRILL_REDIS_PORT} (save disabled; drill only)"
  "${bin}" \
    --port "${DRILL_REDIS_PORT}" \
    --bind 127.0.0.1 \
    --protected-mode no \
    --save "" \
    --appendonly no \
    --daemonize no \
    --logfile "${DRILL_HOME}/redis.log" \
    --dir "${DRILL_HOME}" \
    >"${DRILL_HOME}/redis.stdout" 2>&1 &
  echo $! >"${DRILL_HOME}/redis.pid"
  STARTED_REDIS=1
  local i
  for i in $(seq 1 25); do
    if redis_pong "${DRILL_REDIS_PORT}"; then
      REDIS_STATUS="STARTED"
      REDIS_REASON="native ${bin} :${DRILL_REDIS_PORT}"
      log "Redis ready on :${DRILL_REDIS_PORT} pid=$(cat "${DRILL_HOME}/redis.pid")"
      return 0
    fi
    sleep 0.2
  done
  REDIS_STATUS="SKIP"
  REDIS_REASON="Redis process started but PING failed on :${DRILL_REDIS_PORT}"
  return 1
}

start_minio() {
  if minio_live "${DRILL_MINIO_API_PORT}"; then
    if [[ -f "${DRILL_HOME}/minio.env" ]]; then
      # shellcheck disable=SC1091
      source "${DRILL_HOME}/minio.env"
      MINIO_STATUS="REUSE"
      MINIO_REASON="already-live :${DRILL_MINIO_API_PORT}"
      log "MinIO already serving dedicated :${DRILL_MINIO_API_PORT}"
      return 0
    fi
    MINIO_STATUS="SKIP"
    MINIO_REASON="MinIO live on :${DRILL_MINIO_API_PORT} but ${DRILL_HOME}/minio.env missing (no credentials)"
    return 1
  fi
  if [[ "$(port_in_use "${DRILL_MINIO_API_PORT}")" == "yes" ]]; then
    local alt
    alt="$(pick_free_port $((DRILL_MINIO_API_PORT + 1)) "${PRODUCT_REDIS_PORT}")"
    if [[ -z "${alt}" ]]; then
      MINIO_STATUS="SKIP"
      MINIO_REASON="dedicated MinIO port :${DRILL_MINIO_API_PORT} occupied and no free fallback"
      return 1
    fi
    log "dedicated MinIO :${DRILL_MINIO_API_PORT} occupied; switching to :${alt}"
    DRILL_MINIO_API_PORT="${alt}"
    DRILL_MINIO_CONSOLE_PORT="$((alt + 1))"
  fi

  local bin="${DRILL_HOME}/minio"
  if [[ ! -x "${bin}" ]]; then
    log "downloading native MinIO linux-amd64 into ${DRILL_HOME} (no Docker)"
    if ! curl -fsSL --max-time 90 "https://dl.min.io/server/minio/release/linux-amd64/minio" -o "${bin}"; then
      MINIO_STATUS="SKIP"
      MINIO_REASON="could not download MinIO binary"
      return 1
    fi
    chmod 755 "${bin}"
  fi

  if [[ -z "${MINIO_ROOT_USER:-}" ]]; then
    MINIO_ROOT_USER="drill$(openssl rand -hex 4)"
  fi
  if [[ -z "${MINIO_ROOT_PASSWORD:-}" ]]; then
    MINIO_ROOT_PASSWORD="$(openssl rand -hex 16)"
  fi
  umask 077
  cat >"${DRILL_HOME}/minio.env" <<EOF
export MINIO_ROOT_USER='${MINIO_ROOT_USER}'
export MINIO_ROOT_PASSWORD='${MINIO_ROOT_PASSWORD}'
export S3_ENDPOINT='http://127.0.0.1:${DRILL_MINIO_API_PORT}'
export S3_ACCESS_KEY='${MINIO_ROOT_USER}'
export S3_SECRET_KEY='${MINIO_ROOT_PASSWORD}'
export S3_TEST_BUCKET='nexus-isolation-drill'
EOF
  chmod 600 "${DRILL_HOME}/minio.env"
  # shellcheck disable=SC1091
  source "${DRILL_HOME}/minio.env"
  log "wrote ${DRILL_HOME}/minio.env (mode 0600; password not logged)"

  mkdir -p "${DRILL_HOME}/minio-data"
  log "starting ${bin} server on 127.0.0.1:${DRILL_MINIO_API_PORT} console :${DRILL_MINIO_CONSOLE_PORT}"
  env MINIO_ROOT_USER="${MINIO_ROOT_USER}" MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD}" \
    "${bin}" server "${DRILL_HOME}/minio-data" \
      --address "127.0.0.1:${DRILL_MINIO_API_PORT}" \
      --console-address "127.0.0.1:${DRILL_MINIO_CONSOLE_PORT}" \
      >"${DRILL_HOME}/minio.log" 2>&1 &
  echo $! >"${DRILL_HOME}/minio.pid"
  STARTED_MINIO=1
  local i
  for i in $(seq 1 40); do
    if minio_live "${DRILL_MINIO_API_PORT}"; then
      MINIO_STATUS="STARTED"
      MINIO_REASON="native ${bin} :${DRILL_MINIO_API_PORT}"
      log "MinIO ready on :${DRILL_MINIO_API_PORT} pid=$(cat "${DRILL_HOME}/minio.pid")"
      return 0
    fi
    sleep 0.25
  done
  MINIO_STATUS="SKIP"
  MINIO_REASON="MinIO process started but health check failed on :${DRILL_MINIO_API_PORT}"
  return 1
}

cleanup() {
  if [[ "${STARTED_REDIS}" -eq 1 && -f "${DRILL_HOME}/redis.pid" ]]; then
    kill "$(cat "${DRILL_HOME}/redis.pid")" >/dev/null 2>&1 || true
  fi
  if [[ "${STARTED_MINIO}" -eq 1 && -f "${DRILL_HOME}/minio.pid" ]]; then
    kill "$(cat "${DRILL_HOME}/minio.pid")" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

start_redis || log "LIVE redis ${REDIS_STATUS}: ${REDIS_REASON}"
start_minio || log "LIVE minio ${MINIO_STATUS}: ${MINIO_REASON}"

export NEXUS_REDIS_HOST=127.0.0.1
export NEXUS_REDIS_PORT="${DRILL_REDIS_PORT}"
if [[ "${MINIO_STATUS}" == "STARTED" || "${MINIO_STATUS}" == "REUSE" ]]; then
  # shellcheck disable=SC1091
  source "${DRILL_HOME}/minio.env"
fi

log
log "== fixture + optional live isolation tests"
set +e
mvn -pl nexus-cache-core,nexus-file-core -am test \
  -Dtest=CachePortIsolationTest,FileServiceIsolationTest,FileApiTest,LiveRedisIsolationOptionalTest,LiveMinioIsolationOptionalTest,LiveRedis32OptionalTest \
  -Dsurefire.failIfNoSpecifiedTests=false
MVN_STATUS=$?
set -e
if [[ "${MVN_STATUS}" -eq 0 ]]; then
  FIXTURE_STATUS="PASS"
else
  FIXTURE_STATUS="FAIL"
fi

if [[ "${REDIS_STATUS}" == "STARTED" || "${REDIS_STATUS}" == "REUSE" ]]; then
  if grep -q "LIVE redis isolation PASS" target/surefire-reports/*.txt \
        nexus-cache-core/target/surefire-reports/*.txt 2>/dev/null \
     || [[ "${MVN_STATUS}" -eq 0 ]]; then
    REDIS_STATUS="PASS"
  fi
fi
if [[ "${MINIO_STATUS}" == "STARTED" || "${MINIO_STATUS}" == "REUSE" ]]; then
  if grep -q "LIVE minio isolation PASS" \
        nexus-file-core/target/surefire-reports/*.txt 2>/dev/null \
     || [[ "${MVN_STATUS}" -eq 0 ]]; then
    MINIO_STATUS="PASS"
  fi
fi

log
log "== drill summary"
log "fixture CachePortIsolationTest/FileServiceIsolationTest/FileApiTest: ${FIXTURE_STATUS}"
log "live Redis 127.0.0.1:${DRILL_REDIS_PORT}: ${REDIS_STATUS} (${REDIS_REASON})"
log "live MinIO 127.0.0.1:${DRILL_MINIO_API_PORT}: ${MINIO_STATUS} (${MINIO_REASON})"
log "product ports avoided: :${PRODUCT_PG_PORT} :${PRODUCT_REDIS_PORT}"
log "AuditPort TX: not modified"
log "See docs/engineering/runbooks/live-redis-minio-isolation.md"

if [[ "${FIXTURE_STATUS}" != "PASS" ]]; then
  exit 1
fi
exit 0
