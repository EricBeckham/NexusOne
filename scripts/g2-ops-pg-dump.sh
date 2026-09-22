#!/usr/bin/env bash
# G2 leftover: ops-level pg_dump honesty (out of product SurfaceGuard).
# Intentional fixtures fail. Product src/main + ops scripts pass.
# Does not redo #137 dump HTTP / #144 tenant_only / #150 todo HTTP.
# AuditPort is not changed. G18 stays closed. Not a full product G2 close.
# No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G2 ops pg_dump honesty"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

echo
echo "== Gate unit tests (fixture fails; product src/main + ops scripts pass)"
mvn -pl nexus-permission-core -am test \
  -Dtest=OpsPgDumpHonestyGateTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "== Product scan CLI must pass"
mvn -f nexus-permission-core/pom.xml -q exec:java@ops-pg-dump-honesty \
  -Dexec.args="--repo ${ROOT}"

echo
echo "== Product ProcessBuilder(pg_dump) fixture must fail the CLI"
set +e
mvn -f nexus-permission-core/pom.xml -q exec:java@ops-pg-dump-honesty \
  -Dexec.args="--source ${ROOT}/nexus-permission-core/src/test/resources/ops-pg-dump-fixtures/negative --no-allowlist"
status=$?
set -e
if [ "$status" -eq 0 ]; then
  echo "expected product pg_dump fixture to fail the static gate"
  exit 1
fi
echo "PASS static rejection: ops-pg-dump-in-product"

echo
echo "== ArchUnit product dump/report/surface + intentional fixture"
mvn -pl nexus-bootstrap -am test \
  -Dtest=G2OpsPgDumpHonestyTest,OpsPgDumpHonestyArchTest,DryRunRecoveryPortContractTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: ops pg_dump stays out of SurfaceGuard; product dump uses AuthorizedExport"
echo "See docs/g2-ops-pg-dump.md"
