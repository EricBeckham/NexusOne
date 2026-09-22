#!/usr/bin/env bash
# G9 PolicySnapshot invalidate / Redis-down evidence.
# Does not start the product JAR. Does not require live Redis 3.2.
# PermissionEngine is run only as the unchanged truth table (G2 coordination).
# No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G9 PolicySnapshot invalidate / Redis-down"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

mvn -pl nexus-permission-core -am test \
  -Dtest=G9PolicySnapshotFailoverTest,RedisInvalidationTest,SnapshotManagerTest,PermissionEngineTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: Redis-down decide matches durable PermissionEngine; lost channel reloads; fail-closed on durable miss"
echo "See docs/g9-policysnapshot-invalidate.md"
