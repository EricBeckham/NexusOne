#!/usr/bin/env bash
# G4 leftover: HTTP export wizard begin/complete (or deny) on the hash chain.
set -euo pipefail
cd "$(dirname "$0")/.."
exec mvn -pl nexus-audit-core,nexus-permission-core,nexus-p3-studio,nexus-bootstrap -am test \
  -Dtest=G4ExportWizardAuditTest,ExportWizardSessionsTest,ExportWizardApiTest,G4ExportWizardIT,AuditCaptureAndChainTest,JdbcAuditChainTransactionTest,SurfaceGuardBypassGateTest \
  -Dsurefire.failIfNoSpecifiedTests=false
