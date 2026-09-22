#!/usr/bin/env bash
# G4 follow-up: export hash-chain audit + 90-day archive Job (Zonky PG 15).
set -euo pipefail
cd "$(dirname "$0")/.."
exec mvn -pl nexus-common,nexus-audit-core,isolation-kernel,nexus-bootstrap -am test \
  -Dtest=AuditCaptureAndChainTest,AuditArchiveJobTest,AuditFlywayContractTest,JdbcAuditChainTransactionTest,JdbcAuditArchiveIT,IsolatedExportTest,G4ExportAuditTest,G4AuditArchiveJobTest,G1IsolationSurfacesTest \
  -Dsurefire.failIfNoSpecifiedTests=false
