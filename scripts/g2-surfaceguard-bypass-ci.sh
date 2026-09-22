#!/usr/bin/env bash
# G2 leftover: SurfaceGuard / PermissionEngine bypass CI gate.
# Intentional fixtures fail. Product src/main passes.
# Does not scan JDBC / QueryCompiler (orthogonal G1 gate).
# AuditPort is not changed. G18 stays closed. Not a full product G2 close.
# No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G2 SurfaceGuard / PermissionEngine bypass CI"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

echo
echo "== Gate unit tests (fixtures fail; product src/main passes)"
mvn -pl nexus-permission-core -am test \
  -Dtest=SurfaceGuardBypassGateTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "== Product scan CLI must pass"
mvn -f nexus-permission-core/pom.xml -q exec:java@surfaceguard-bypass \
  -Dexec.args="--repo ${ROOT}"

echo
echo "== IsolatedExport fixture must fail the CLI"
set +e
mvn -f nexus-permission-core/pom.xml -q exec:java@surfaceguard-bypass \
  -Dexec.args="--source ${ROOT}/nexus-permission-core/src/test/resources/surfaceguard-bypass-fixtures/negative/export --no-allowlist"
status=$?
set -e
if [ "$status" -eq 0 ]; then
  echo "expected IsolatedExport fixture to fail the static gate"
  exit 1
fi
echo "PASS static rejection: isolated-export-bypass"

echo
echo "== ArchUnit product layers + intentional fixture"
mvn -pl nexus-bootstrap -am test \
  -Dtest=G2SurfaceGuardBypassTest,SurfaceGuardBypassArchTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: SurfaceGuard bypass gate; fixtures fail; product src/main empty"
echo "See docs/g2-surfaceguard-bypass-ci.md"
