#!/usr/bin/env bash
# G2 leftover: H5 /m/* list/detail/form/todo on SurfaceGuard.
# AuditPort is not changed. G18 stays closed. Not a full product G2 close. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G2 H5 SurfaceGuard"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1
node -v
npm -v

(
  cd web
  npm test -- src/mobile/permission/surfaceGuard.test.js
  npm run assert:h5-permission
  npm run assert:h5-runtime
)

mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=G2H5SameEngineTest,H5SurfaceGuardTest,G2H5SurfaceAgreementTest,G2SameEngineTest,G2SurfaceAgreementTest,FormSurfaceGuardTest,FormStudioPermissionApiTest,TenantMemberGrantSeederTest,TenantMemberGrantSeedApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: H5 list/detail/form/todo decide through SurfaceGuard; P2.PERM.0003/0002/0001"
echo "See docs/g2-h5-permission.md"
