#!/usr/bin/env bash
# G2 remainder: export Task type + Job/Task submit on SurfaceGuard.
# Isolation reject-before-sink stays. AuditPort is not changed.
# G18 stays closed. Not a full product G2 close. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G2 export / Job / Task SurfaceGuard"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=G2SameEngineTest,G2ExportJobTaskEngineTest,G2AuthorizedExportTest,G2AuthorizedSubmitTest,G2RoleMatrixApiTest,G2SurfaceAgreementTest,PermissionDataScopeHookTest,FieldPolicyProjectorTest,PermissionApiTest,TenantMemberGrantSeedApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: export/Job/Task decide before IsolatedExport/enqueue; P2.PERM.0003 != P1.M02.019"
echo "See docs/g2-permission.md"
