#!/usr/bin/env bash
# G9 leftover: Redis-down / lost-invalidate fail-closed (no silent allow).
# Does not start the product JAR. Does not require live Redis.
# Does not claim Sentinel / Cluster / replica promotion. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G9 failover remainder (fail-closed / durable PermissionEngine)"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

mvn -pl nexus-permission-core -am test \
  -Dtest=G9FailoverRemainderTest,JdbcG9FailoverRemainderIT,RedisInvalidationTest,G9PolicySnapshotFailoverTest,PermissionEngineTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: Redis-down DENY fail-closes; publish failure is not success; readers see deny via version poll"
echo "See docs/g9-failover-remainder.md"
