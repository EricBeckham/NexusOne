#!/usr/bin/env bash
# G4 leftover: SurfaceGuard / permission-deny on the existing hash chain (Zonky PG 15).
set -euo pipefail
cd "$(dirname "$0")/.."
exec mvn -pl nexus-permission-core,nexus-p3-studio,nexus-bootstrap -am test \
  -Dtest=G4SurfaceGuardDenyAuditTest,FormSurfaceGuardTest,H5SurfaceGuardTest,ReportSurfaceGuardTest,G4GuardDenyAuditIT,G4AuditCoverageIT,JdbcAuditChainTransactionTest \
  -Dsurefire.failIfNoSpecifiedTests=false
