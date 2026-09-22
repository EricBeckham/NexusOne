#!/usr/bin/env bash
# G2 leftover: report query/export on SurfaceGuard.
# AuditPort is not changed. G18 stays closed. Not a full product G2 close. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G2 report SurfaceGuard"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1
node -v
npm -v

(
  cd web
  npm test -- src/permission/reportGuard.test.js
  npm run assert:g2-report-surface
  npm run assert:h5-permission
  npm run assert:h5-runtime
)

mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=G2ReportSameEngineTest,ReportSurfaceGuardTest,G2ReportSurfaceAgreementTest,ReportSurfaceApiTest,G2SameEngineTest,G2SurfaceAgreementTest,G2H5SameEngineTest,FormSurfaceGuardTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: report query/export decide through SurfaceGuard; P2.PERM.0003/0002/0001"
echo "See docs/g2-report-surface-guard.md"
