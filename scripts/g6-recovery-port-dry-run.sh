#!/usr/bin/env bash
# G6 leftover: RecoveryPort honesty / dry-run contract.
# Does not execute dump/restore. Does not touch haopan_platform.
# Does not claim a product RecoveryPort close, minute-level RPO, or product HA.
# Does not reopen G18. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G6 RecoveryPort honesty / dry-run contract"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

echo
echo "Contract:"
echo "  1. product RecoveryPort stays DryRunRecoveryPort"
echo "  2. dry-run may probe pg_dump --version only"
echo "  3. dry-run does not mutate a PostgreSQL 15 stand-in"
echo "  4. never haopan_platform, never port 5432"
echo "  5. profileAccepted=false, executed=false"
echo "  6. not minute-level RPO, not product HA, not a product RecoveryPort close"
echo

mvn -pl nexus-audit-core,nexus-bootstrap -am test \
  -Dtest=DryRunRecoveryPortTest,DryRunRecoveryPortContractTest,DryRunRecoveryPortDoesNotMutateDbIT,G6RecoveryPortDryRunIT \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: RecoveryPort dry-run contract holds; stand-in DB unchanged"
echo "See docs/g6-recovery-port-dry-run.md"
echo "Not claimed: product RecoveryPort close / minute-level RPO / product HA"
echo "Not touched: haopan_platform"
