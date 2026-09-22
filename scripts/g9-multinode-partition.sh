#!/usr/bin/env bash
# G9 leftover: in-process dual-reader / partition-oriented fail-closed harness.
# Shows version-poll convergence when invalidate is lost across two nodes.
# Does not start the product JAR. Does not require live Redis.
# Does not claim Sentinel / Cluster / replica promotion.
# Does not claim a live multi-JVM network partition. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G9 multi-node / partition leftover (in-process dual-reader)"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

echo
echo "Scenario (in-process, not live Sentinel/Cluster):"
echo "  1. writer + readerA + readerB share the durable store"
echo "  2. each node has its own invalidate bus (lost PUBLISH across the partition)"
echo "  3. GRANT is cached on both readers"
echo "  4. writer records a durable DENY; reader LRU stays on GRANT until that node polls"
echo "  5. reader A's poll / get does not mutate reader B"
echo "  6. each node's version poll (or request-path get) fail-closes to explicit_deny"
echo "     == durable PermissionEngine"
echo

mvn -pl nexus-permission-core -am test \
  -Dtest=G9DualReaderPartitionTest,JdbcG9DualReaderPartitionIT,G9FailoverRemainderTest,G9PolicySnapshotPropagationTest,PermissionEngineTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: two in-process readers converge via version poll after lost invalidate; no silent allow"
echo "See docs/g9-multinode-partition.md"
echo "Not claimed: Sentinel / Cluster / replica promotion / live multi-JVM partition"
echo "Honesty leftover (same snapshot store): ./scripts/g9-sentinel-cluster-honesty.sh"
echo "Live multi-JVM / replica honesty leftover: ./scripts/g9-live-multi-jvm-replica.sh"
