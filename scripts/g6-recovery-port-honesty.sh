#!/usr/bin/env bash
# G6 leftover: RecoveryPort still dry-run honesty.
# Does not redo #135. Does not execute dump/restore. Does not touch haopan_platform.
# Does not claim a product RecoveryPort close, minute-level RPO, product HA,
# or delivery-grade HA. Does not reopen G18. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G6 RecoveryPort still dry-run honesty"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

echo
echo "Honesty leftover (not a #135 redo):"
echo "  1. #135 dry-run is not product HA"
echo "  2. #135 dry-run is not minute-level RPO"
echo "  3. #135 dry-run is not delivery-grade HA"
echo "  4. docs/README.md keeps both leftover rows"
echo "  5. product RecoveryPort close is not claimed"
echo

mvn -pl nexus-audit-core -am test \
  -Dtest=G6RecoveryPortHonestyTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: RecoveryPort still-dry-run honesty leftover holds"
echo "See docs/g6-recovery-port-honesty.md"
echo "Not claimed: product RecoveryPort close / minute-level RPO / product HA / delivery-grade HA"
echo "Not redone: #135 dry-run contract"
echo "Not touched: haopan_platform"
