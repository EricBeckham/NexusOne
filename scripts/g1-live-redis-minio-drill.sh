#!/usr/bin/env bash
# Optional live Redis / MinIO G1 isolation drills (cache + file).
# Skips cleanly when those services are absent. No Docker.
# Does not start the product JAR and does not change AuditPort same-TX append.
#
# Redis: NEXUS_REDIS_HOST (default 127.0.0.1) / NEXUS_REDIS_PORT (default 6379)
# MinIO: S3_ENDPOINT, S3_ACCESS_KEY, S3_SECRET_KEY, optional S3_TEST_BUCKET
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

REDIS_HOST="${NEXUS_REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${NEXUS_REDIS_PORT:-6379}"
S3_ENDPOINT="${S3_ENDPOINT:-}"
S3_BUCKET="${S3_TEST_BUCKET:-nexus-g1-live}"

echo "== G1 live Redis / MinIO isolation drills"
echo "cwd=$ROOT"
java -version
if command -v mvn >/dev/null 2>&1; then
  mvn -version | head -n 1
else
  echo "ERROR: mvn is not on PATH"
  exit 1
fi

redis_probe="SKIP"
if command -v redis-cli >/dev/null 2>&1; then
  if redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" ping 2>/dev/null | grep -q PONG; then
    redis_probe="UP $(redis-cli -h "$REDIS_HOST" -p "$REDIS_PORT" INFO server 2>/dev/null | awk -F: '/^redis_version/{print $2}' | tr -d '\r')"
  fi
elif (echo >/dev/tcp/"$REDIS_HOST"/"$REDIS_PORT") >/dev/null 2>&1; then
  redis_probe="UP (tcp $REDIS_HOST:$REDIS_PORT)"
fi

minio_probe="SKIP"
if [[ -n "$S3_ENDPOINT" && -n "${S3_ACCESS_KEY:-}" && -n "${S3_SECRET_KEY:-}" ]]; then
  if command -v curl >/dev/null 2>&1 && curl -fsS --max-time 2 "$S3_ENDPOINT/minio/health/live" >/dev/null 2>&1; then
    minio_probe="UP $S3_ENDPOINT bucket=$S3_BUCKET"
  else
    minio_probe="ENV_SET (health check failed; tests will skip if client cannot connect)"
  fi
fi

echo "redis_probe=$redis_probe"
echo "minio_probe=$minio_probe"

mvn -pl nexus-cache-core,nexus-file-core,nexus-bootstrap -am test \
  -Dtest=LiveRedisIsolationDrillTest,LiveRedis32OptionalTest,LiveMinioIsolationDrillTest,LiveG1CacheFileDrillTest,G1IsolationSurfacesTest,CachePortIsolationTest,FileServiceIsolationTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "DONE: live drills skip when Redis/MinIO are absent; G1 in-process surfaces still run."
echo "See docs/g1-live-redis-minio.md"
