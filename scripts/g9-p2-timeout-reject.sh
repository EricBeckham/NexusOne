#!/usr/bin/env bash
# G9 leftover: bounded P2-down timeout then reject.
# Does not start the product JAR. Does not require live Redis.
# Does not claim Sentinel / Cluster. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G9 P2 timeout-then-reject"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

mvn -pl nexus-permission-core -am test \
  -Dtest=G9P2TimeoutRejectTest,JdbcP2TimeoutRejectIT,SnapshotManagerTest,G9PolicySnapshotFailoverTest,PermissionEngineTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: decide rejects after configured timeout when version/load hangs; healthy path unchanged"
echo "See docs/g9-p2-timeout-reject.md"
