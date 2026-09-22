#!/usr/bin/env bash
# G4 leftover: 90-day hot-row purge (Zonky PG 15). Does not DROP the hot table.
set -euo pipefail
cd "$(dirname "$0")/.."
exec mvn -pl nexus-common,nexus-audit-core,nexus-file-core,modules/isolation-kernel,nexus-bootstrap -am test \
  -Dtest=AuditHotPurgeJobTest,JdbcAuditHotPurgeIT,AuditFlywayContractTest,G4AuditHotPurgeJobTest,JdbcAuditChainTransactionTest \
  -Dsurefire.failIfNoSpecifiedTests=false
