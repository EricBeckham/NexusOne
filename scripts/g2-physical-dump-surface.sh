#!/usr/bin/env bash
# G2 leftover: physical ledger dump / raw export on SurfaceGuard.
# AuditPort is not changed. G18 stays closed. Not a full product G2 close. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G2 physical dump SurfaceGuard"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1
node -v
npm -v

(
  cd web
  npm test -- src/permission/dumpGuard.test.js
  npm run assert:g2-physical-dump
  npm run assert:g2-report-surface
)

mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=G2DumpSameEngineTest,DumpSurfaceGuardTest,G2DumpSurfaceAgreementTest,DumpSurfaceApiTest,SurfaceGuardBypassGateTest,G2ReportSameEngineTest,G2SameEngineTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: physical dump / raw export decide through SurfaceGuard; P2.PERM.0003/0002/0001"
echo "See docs/g2-physical-dump-surface.md"
