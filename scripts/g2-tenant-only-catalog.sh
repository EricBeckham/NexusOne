#!/usr/bin/env bash
# G2 leftover: isolation-only tenant_only / catalog whitelist honesty.
# AuditPort is not changed. G18 stays closed. Not a full product G2 close. No Docker.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "== G2 isolation-only tenant_only / catalog whitelist"
echo "cwd=$ROOT"
java -version
mvn -version | head -n 1

mvn -pl nexus-permission-core,nexus-p3-studio -am test \
  -Dtest=IsolationOnlyDecisionTest,G2CatalogSameEngineTest,CatalogSurfaceGuardTest,G2CatalogSurfaceAgreementTest,CatalogSurfaceApiTest,TenantOnlyDataScopeHookTest,FormSurfaceGuardTest,H5SurfaceGuardTest,ReportSurfaceGuardTest,PermissionDataScopeHookTest,SurfaceGuardBypassGateTest,G2SameEngineTest,G2SurfaceAgreementTest \
  -Dsurefire.failIfNoSpecifiedTests=false

echo
echo "PASS: isolation-only tenant_only fail-closes when P2 TenantContext exists;"
echo "catalog definition HTTP decides through SurfaceGuard (ledger/read + ledger/write)."
echo "See docs/g2-tenant-only-catalog.md"
